/*
 * Copyright (c) 2006-2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     bstefanescu
 *     Vladimir Pasquier <vpasquier@nuxeo.com>
 */
package org.nuxeo.ecm.automation.core.operations.execution;

import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * Run an embedded operation chain that returns a DocumentModel using the
 * current input.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@Operation(id = RunDocumentChain.ID, category = Constants.CAT_SUBCHAIN_EXECUTION, label = "Run Document Chain", description = "Run an operation chain which is returning a document in the current context. The input for the chain ro run is the current input of the operation. Return the output of the chain as a document. The 'parameters' injected are accessible in the subcontext ChainParameters. For instance, @{ChainParameters['parameterKey']}.")
public class RunDocumentChain {

    public static final String ID = "Context.RunDocumentOperation";

    @Context
    protected OperationContext ctx;

    @Context
    protected AutomationService service;

    @Context
    protected CoreSession session;

    @Param(name = "id")
    protected String chainId;

    @Param(name="isolate", required = false, values = "false")
    protected boolean isolate = false;

    @Param(name = "parameters", description = "Accessible in the subcontext ChainParameters. For instance, @{ChainParameters['parameterKey']}.", required = false)
    protected Properties chainParameters;

    /**
     * @since 6.0
     * Define if the chain in parameter should be executed in new transaction.
     */
    @Param(name = "newTx", required = false, values = "false", description = "Define if the chain in parameter should be executed in new transaction.")
    protected boolean newTx = false;

    /**
     * @since 6.0
     * Define transaction timeout (default to 60 sec).
     */
    @Param(name = "timeout", required = false, description = "Define transaction timeout (default to 60 sec).")
    protected Integer timeout = 60;

    /**
     * @since 6.0
     * Define if transaction should rollback or not (default to true).
     */
    @Param(name = "rollbackGlobalOnError", required = false, values = "true", description = "Define if transaction should rollback or not (default to true)")
    protected boolean rollbackGlobalOnError = true;


    @OperationMethod
    @SuppressWarnings("unchecked")
    public DocumentModel run(DocumentModel doc) throws Exception {
        // Handle isolation option
        Map<String, Object> vars = isolate ? new HashMap<>(
                ctx.getVars()) : ctx.getVars();
        OperationContext subctx = ctx.getSubContext(isolate, doc);

        // Running chain/operation
        DocumentModel result = null;
        if(newTx) {
            result = (DocumentModel) service.runInNewTx(subctx, chainId, chainParameters,
                    timeout, rollbackGlobalOnError);
        }else{
            result = (DocumentModel) service.run(subctx, chainId, (Map) chainParameters);
        }

        // reconnect documents in the context
        if (!isolate) {
            for (String varName : vars.keySet()) {
                if (!ctx.getVars().containsKey(varName)) {
                    ctx.put(varName, vars.get(varName));
                } else {
                    Object value = vars.get(varName);
                    if (value != null && value instanceof DocumentModel) {
                        ctx.getVars().put(
                                varName,
                                session.getDocument(((DocumentModel) value).getRef()));
                    } else {
                        ctx.getVars().put(varName, value);
                    }
                }
            }
        }
        return result;
    }

    @OperationMethod
    public DocumentModelList run(DocumentModelList docs) throws Exception {
        DocumentModelList result = new DocumentModelListImpl(docs.size());
        for (DocumentModel doc : docs) {
            result.add(run(doc));
        }
        return result;
    }

}
