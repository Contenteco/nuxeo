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
package org.nuxeo.connect.update.task.standalone;

import java.io.File;
import java.util.Map;

import org.nuxeo.connect.update.LocalPackage;
import org.nuxeo.connect.update.PackageException;
import org.nuxeo.connect.update.PackageState;
import org.nuxeo.connect.update.PackageUpdateService;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
public class InstallTask extends CommandsTask {

    public InstallTask(PackageUpdateService pus) {
        super(pus);
    }

    @Override
    public boolean isInstallTask() {
        return true;
    }

    @Override
    protected File getCommandsFile() throws PackageException {
        return pkg.getInstallFile();
    }

    @Override
    protected void doRun(Map<String, String> params) throws PackageException {
        super.doRun(params);
        // generate the uninstall.xml file
        File file = pkg.getData().getEntry(LocalPackage.UNINSTALL);
        writeLog(file);
        // No reload of components in standalone mode
    }

    @Override
    protected void rollbackDone() throws PackageException {
        service.setPackageState(pkg, PackageState.DOWNLOADED);
    }

    @Override
    protected void taskDone() throws PackageException {
        service.setPackageState(pkg, PackageState.STARTED);
    }

    @Override
    protected void flush() throws PackageException {
        // standalone mode: nothing to do
    }

}
