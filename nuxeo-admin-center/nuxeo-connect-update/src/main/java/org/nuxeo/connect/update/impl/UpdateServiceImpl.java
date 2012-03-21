/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     bstefanescu, jcarsique
 */
package org.nuxeo.connect.update.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.nuxeo.common.Environment;
import org.nuxeo.common.xmap.Context;
import org.nuxeo.common.xmap.XMap;
import org.nuxeo.common.xmap.XValueFactory;
import org.nuxeo.connect.update.LocalPackage;
import org.nuxeo.connect.update.PackageDependency;
import org.nuxeo.connect.update.PackageException;
import org.nuxeo.connect.update.PackageState;
import org.nuxeo.connect.update.PackageType;
import org.nuxeo.connect.update.PackageUpdateService;
import org.nuxeo.connect.update.Version;
import org.nuxeo.connect.update.model.PackageDefinition;
import org.nuxeo.connect.update.standalone.PackagePersistence;
import org.nuxeo.connect.update.standalone.StandaloneUpdateService;
import org.nuxeo.connect.update.standalone.task.commands.Append;
import org.nuxeo.connect.update.standalone.task.commands.Command;
import org.nuxeo.connect.update.standalone.task.commands.Config;
import org.nuxeo.connect.update.standalone.task.commands.Copy;
import org.nuxeo.connect.update.standalone.task.commands.Delete;
import org.nuxeo.connect.update.standalone.task.commands.Deploy;
import org.nuxeo.connect.update.standalone.task.commands.DeployConfig;
import org.nuxeo.connect.update.standalone.task.commands.Flush;
import org.nuxeo.connect.update.standalone.task.commands.FlushCoreCache;
import org.nuxeo.connect.update.standalone.task.commands.FlushJaasCache;
import org.nuxeo.connect.update.standalone.task.commands.Install;
import org.nuxeo.connect.update.standalone.task.commands.LoadJar;
import org.nuxeo.connect.update.standalone.task.commands.ParameterizedCopy;
import org.nuxeo.connect.update.standalone.task.commands.ReloadProperties;
import org.nuxeo.connect.update.standalone.task.commands.UnAppend;
import org.nuxeo.connect.update.standalone.task.commands.Undeploy;
import org.nuxeo.connect.update.standalone.task.commands.UndeployConfig;
import org.nuxeo.connect.update.standalone.task.commands.Uninstall;
import org.nuxeo.connect.update.standalone.task.commands.UnloadJar;
import org.nuxeo.connect.update.standalone.task.update.Rollback;
import org.nuxeo.connect.update.standalone.task.update.Update;
import org.nuxeo.connect.update.xml.FormsDefinition;
import org.nuxeo.connect.update.xml.PackageDefinitionImpl;
import org.nuxeo.runtime.reload.NuxeoRestart;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
public class UpdateServiceImpl extends StandaloneUpdateService implements
        PackageUpdateService {

    protected static XMap xmap;

    protected PackagePersistence persistence;

    protected Map<String, Class<? extends Command>> commands;

    public static XMap getXmap() {
        return xmap;
    }

    public UpdateServiceImpl() throws IOException {
        super(Environment.getDefault());
        persistence = new PackagePersistence(this);
        commands = new HashMap<String, Class<? extends Command>>();
    }

    @Override
    public File getDataDir() {
        return persistence.getRoot();
    }

    public PackagePersistence getPersistence() {
        return persistence;
    }

    public LocalPackage addPackage(File file) throws PackageException {
        return persistence.addPackage(file);
    }

    public void removePackage(String id) throws PackageException {
        persistence.removePackage(id);
    }

    public LocalPackage getPackage(String id) throws PackageException {
        return persistence.getPackage(id);
    }

    public List<LocalPackage> getPackages() throws PackageException {
        return persistence.getPackages();
    }

    public static XMap createXmap() {
        @SuppressWarnings("hiding")
        XMap xmap = new XMap();
        xmap.setValueFactory(PackageType.class, new XValueFactory() {
            @Override
            public String serialize(Context arg0, Object arg1) {
                return ((PackageType) arg1).getValue();
            }

            @Override
            public Object deserialize(Context arg0, String arg1) {
                return PackageType.getByValue(arg1);
            }
        });
        xmap.setValueFactory(Version.class, new XValueFactory() {
            @Override
            public String serialize(Context arg0, Object arg1) {
                return arg1.toString();
            }

            @Override
            public Object deserialize(Context arg0, String arg1) {
                return new Version(arg1);
            }
        });
        xmap.setValueFactory(PackageDependency.class, new XValueFactory() {

            @Override
            public String serialize(Context arg0, Object arg1) {
                return arg1.toString();
            }

            @Override
            public Object deserialize(Context arg0, String arg1) {
                return new PackageDependency(arg1);
            }
        });
        xmap.register(PackageDefinitionImpl.class);
        xmap.register(FormsDefinition.class);
        return xmap;
    }

    public void initialize() throws PackageException {
        xmap = createXmap();
        addCommand(Copy.ID, Copy.class);
        addCommand(Append.ID, Append.class);
        addCommand(UnAppend.ID, UnAppend.class);
        addCommand(ParameterizedCopy.ID, ParameterizedCopy.class);
        addCommand(Delete.ID, Delete.class);
        addCommand(Install.ID, Install.class);
        addCommand(Uninstall.ID, Uninstall.class);
        addCommand(FlushCoreCache.ID, FlushCoreCache.class);
        addCommand(FlushJaasCache.ID, FlushJaasCache.class);
        addCommand(Flush.ID, Flush.class);
        addCommand(ReloadProperties.ID, ReloadProperties.class);
        addCommand(Deploy.ID, Deploy.class);
        addCommand(Undeploy.ID, Undeploy.class);
        addCommand(DeployConfig.ID, DeployConfig.class);
        addCommand(UndeployConfig.ID, UndeployConfig.class);
        addCommand(LoadJar.ID, LoadJar.class);
        addCommand(UnloadJar.ID, UnloadJar.class);
        addCommand(Config.ID, Config.class);
        addCommand(Update.ID, Update.class);
        addCommand(Rollback.ID, Rollback.class);
        startInstalledPackages();
    }

    public void setPackageState(LocalPackage pkg, int state)
            throws PackageException {
        persistence.updateState(pkg.getId(), state);
        pkg.setState(state);
    }

    public void shutdown() throws PackageException {
        xmap = null;
    }

    public Command getCommand(String id) throws PackageException {
        Class<? extends Command> type = commands.get(id);
        if (type != null) {
            try {
                return type.getConstructor().newInstance();
            } catch (Exception e) {
                throw new PackageException("Failed to load command " + id, e);
            }
        }
        return null;
    }

    public void addCommand(String id, Class<? extends Command> cmd) {
        commands.put(id, cmd);
    }

    public void removeCommand(String id) {
        commands.remove(id);
    }

    public LocalPackage getActivePackage(String name) throws PackageException {
        return persistence.getActivePackage(name);
    }

    public void restart() throws PackageException {
        try {
            NuxeoRestart.restart();
        } catch (Throwable t) {
            throw new PackageException("Failed to restart Nuxeo", t);
        }
    }

    public PackageDefinition loadPackageFromZip(File file)
            throws PackageException {
        ZipFile zip = null;
        try {
            zip = new ZipFile(file);
            ZipEntry mfEntry = zip.getEntry(LocalPackage.MANIFEST);
            InputStream mfStream = zip.getInputStream(mfEntry);
            return loadPackage(mfStream);
        } catch (PackageException e) {
            throw e;
        } catch (Exception e) {
            throw new PackageException(
                    "Failed to load package definition from zip file: " + file,
                    e);
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException e) {
                    throw new PackageException("Failed to close package zip: "
                            + file, e);
                }
            }
        }
    }

    public PackageDefinition loadPackage(File file) throws PackageException {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            return loadPackage(in);
        } catch (PackageException e) {
            throw e;
        } catch (Exception e) {
            throw new PackageException(
                    "Failed to load XML package definition from file: " + file,
                    e);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                throw new PackageException("Failed to close input stream for "
                        + file, e);
            }
        }
    }

    public PackageDefinition loadPackage(InputStream in)
            throws PackageException {
        try {
            return (PackageDefinition) xmap.load(in);
        } catch (Exception e) {
            throw new PackageException(
                    "Failed to parse XML package definition", e);
        }
    }

    protected void startInstalledPackages() throws PackageException {
        for (Map.Entry<String, Integer> entry : persistence.getStates().entrySet()) {
            if (entry.getValue().intValue() == PackageState.INSTALLED) {
                persistence.updateState(entry.getKey(), PackageState.STARTED);
            }
        }
    }

    @Override
    public void reset() throws PackageException {
        persistence.reset();
    }
}
