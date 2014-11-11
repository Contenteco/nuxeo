/*
 * Copyright (c) 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     bstefanescu
 */
package org.nuxeo.ecm.automation.jaxrs.io.operations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationDocumentation;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.OperationParameters;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class AutomationInfo {

    protected static final Log log = LogFactory.getLog(AutomationInfo.class);

    protected final List<OperationDocumentation> ops;

    protected final List<OperationDocumentation> chains;

    public AutomationInfo(AutomationService service) throws OperationException {
        ops = service.getDocumentation();
        // build a map for easy lookup
        Map<String, OperationDocumentation> map = new HashMap<String, OperationDocumentation>();
        for (OperationDocumentation doc : ops) {
            map.put(doc.id, doc);
        }
        chains = new ArrayList<OperationDocumentation>();
        for (OperationChain chain : service.getOperationChains()) {
            try {
                OperationDocumentation doc = new OperationDocumentation(
                        chain.getId());
                doc.description = chain.getDescription();
                doc.category = "Chain";
                doc.label = doc.id;
                doc.params = new OperationDocumentation.Param[0];
                // compute chain signature
                List<OperationParameters> ops = chain.getOperations();
                if (ops.isEmpty()) {
                    doc.signature = new String[] { "void", "void" };
                } else if (ops.size() == 1) {
                    OperationDocumentation opdoc = map.get(ops.get(0).id());
                    if (opdoc == null) {
                        log.error("Unable to find Operation " + ops.get(0).id()
                                + " that is used by chain " + chain.getId()
                                + ", chain will be discarded");
                        continue;
                    }
                    doc.signature = opdoc.signature;
                } else {
                    ArrayList<String[]> sigs = new ArrayList<String[]>();
                    for (OperationParameters o : ops) {
                        OperationDocumentation opdoc = map.get(o.id());
                        if (opdoc == null) {
                            log.error("Unable to find Operation " + o.id()
                                    + " that is used by chain " + chain.getId()
                                    + ", chain will be discarded");
                            continue;
                        }
                        sigs.add(opdoc.signature);
                    }
                    String[] head = sigs.get(0);
                    ArrayList<String> rs = new ArrayList<String>();
                    for (int i = 0; i < head.length; i += 2) {
                        String in = head[i];
                        String out = head[i + 1];
                        List<String> result = new ArrayList<String>();
                        checkPath(out, sigs, 1, result);
                        for (String r : result) {
                            rs.add(in);
                            rs.add(r);
                        }
                    }
                    doc.signature = rs.toArray(new String[rs.size()]);
                }
                chains.add(doc);

            } catch (Throwable e) {
                log.error(
                        "Unable to build definition of Chain " + chain.getId(),
                        e);
            }
        }
    }

    protected void checkPath(String in, List<String[]> sigs, int offset,
            List<String> result) {
        boolean last = sigs.size() - 1 == offset;
        String[] sig = sigs.get(offset);
        for (int i = 0; i < sig.length; i += 2) {
            if ("void".equals(in) || "void".equals(sig[i]) || in.equals(sig[i])) {
                if (last) {
                    result.add(sig[i + 1]);
                } else {
                    checkPath(sig[i + 1], sigs, offset + 1, result);
                }
            }
        }
    }

    public List<OperationDocumentation> getOperations() {
        return ops;
    }

    public List<OperationDocumentation> getChains() {
        return chains;
    }
}
