/*
 * (C) Copyright 2006-2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     bstefanescu
 */
package org.nuxeo.connect.update.task.standalone.commands;

import java.io.File;
import java.util.Map;

import org.nuxeo.connect.update.PackageException;
import org.nuxeo.connect.update.ValidationStatus;
import org.nuxeo.connect.update.task.Command;
import org.nuxeo.connect.update.task.Task;
import org.nuxeo.connect.update.xml.XmlWriter;
import org.w3c.dom.Element;

/**
 * Install bundle, flush any application cache and perform Nuxeo preprocessing
 * on the bundle.
 * <p>
 * The inverse of this command is Undeploy.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class DeployConfigPlaceholder extends AbstractCommand {

    public static final String ID = "deploy-config";

    protected File file;

    public DeployConfigPlaceholder() {
        super(ID);
    }

    public DeployConfigPlaceholder(File file) {
        super(ID);
        this.file = file;
    }

    @Override
    protected void doValidate(Task task, ValidationStatus status)
            throws PackageException {
        // do nothing
    }

    @Override
    protected Command doRun(Task task, Map<String, String> prefs)
            throws PackageException {
        // standalone mode: nothing to do
        // XXX why not UndeployConfigPlaceholder ?
        return new UndeployPlaceholder(file);
    }

    public void readFrom(Element element) throws PackageException {
        String v = element.getAttribute("file");
        if (v.length() > 0) {
            file = new File(v);
            guardVars.put("file", file);
        }
    }

    public void writeTo(XmlWriter writer) {
        writer.start(ID);
        if (file != null) {
            writer.attr("file", file.getAbsolutePath());
        }
        writer.end();
    }
}
