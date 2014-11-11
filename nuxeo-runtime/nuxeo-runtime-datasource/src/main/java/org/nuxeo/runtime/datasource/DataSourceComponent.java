/*
 * (C) Copyright 2009 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Florent Guillaume
 */

package org.nuxeo.runtime.datasource;

import java.util.HashMap;
import java.util.Map;

import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.LinkRef;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;
import javax.naming.NotContextException;
import javax.naming.Reference;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Nuxeo component allowing the JNDI registration of datasources by extension
 * point contributions.
 * <p>
 * For now only the internal Nuxeo JNDI server is supported.
 */
public class DataSourceComponent extends DefaultComponent {

    private final Log log = LogFactory.getLog(DataSourceComponent.class);

    public static final String DATASOURCES_XP = "datasources";

    public static final String ENV_CTX_NAME = "java:comp/env/";

    protected final Map<String, DataSourceDescriptor> datasources = new HashMap<String, DataSourceDescriptor>();

    protected final Map<String, DataSourceLinkDescriptor> links = new HashMap<String, DataSourceLinkDescriptor>();
    protected boolean started = false;

    @Override
    public void registerContribution(Object contrib, String extensionPoint,
            ComponentInstance component) throws Exception {
        if (DATASOURCES_XP.equals(extensionPoint)) {
        	if (contrib instanceof DataSourceDescriptor) {
        		addDataSource((DataSourceDescriptor) contrib);
        	} else if (contrib instanceof DataSourceLinkDescriptor) {
        		addDataSourceLink((DataSourceLinkDescriptor) contrib);
        	} else {
        		log.error("Wrong datasource extension type " + contrib.getClass().getName());
        	}
        } else {
            log.error("Ignoring unknown extension point: " + extensionPoint);
        }
    }

    @Override
    public void unregisterContribution(Object contrib, String extensionPoint,
            ComponentInstance component) throws Exception {
        if (DATASOURCES_XP.equals(extensionPoint)) {
        	if (contrib instanceof DataSourceDescriptor) {
        		removeDataSource((DataSourceDescriptor) contrib);
        	} else if (contrib instanceof DataSourceLinkDescriptor) {
        		removeDataSourceLink((DataSourceLinkDescriptor) contrib);
        	}
        }
    }

    @Override
    public int getApplicationStartedOrder() {
        return -1000;
    }

    @Override
    public void applicationStarted(ComponentContext context) throws Exception {
        started = true;
        for (DataSourceDescriptor datasourceDesc : datasources.values()) {
            bindDataSource(datasourceDesc);
        }
        for (DataSourceLinkDescriptor linkDesc:links.values()) {
        	bindDataSourceLink(linkDesc);
        }
    }

    @Override
    public void deactivate(ComponentContext context) throws Exception {
        super.deactivate(context);
        for (DataSourceLinkDescriptor desc : links.values()) {
            log.warn(desc.name + " datasource link still referenced");
            unbindDataSourceLink(desc);
        }
        links.clear();
        for (DataSourceDescriptor desc : datasources.values()) {
            log.warn(desc.name + " datasource still referenced");
            unbindDataSource(desc);
        }
        datasources.clear();
        started = false;
    }

    protected void addDataSource(DataSourceDescriptor contrib) {
        datasources.put(contrib.name, contrib);
        bindDataSource(contrib);
    }

    protected void removeDataSource(DataSourceDescriptor contrib) {
        unbindDataSource(contrib);
        datasources.remove(contrib.name);
    }

    protected void bindDataSource(DataSourceDescriptor descr) {
        if (!started) {
            return;
        }
        log.info("Registering datasource: " + descr.name);
        try {
            Name name = new CompositeName(ENV_CTX_NAME + descr.name);
            Context ctx = new InitialContext();
            // bind intermediate names as subcontexts (jdbc/foo)
            for (int i = 0; i < name.size() - 1; i++) {
                try {
                    ctx = (Context) ctx.lookup(name.get(i));
                } catch (NamingException e) {
                    ctx = ctx.createSubcontext(name.get(i));
                }
            }
            Reference reference = descr.getReference();
            String localname = name.get(name.size() - 1);
            ctx.bind(localname, reference);
            // don't need a reference, local access only, ensure not leaking ds
            DataSource ds = (DataSource) ctx.lookup(localname);
            ctx.rebind(localname, ds);
        } catch (NamingException e) {
            log.error("Cannot bind datasource '" + descr.name + "' in JNDI", e);
        }
    }

    protected void unbindDataSource(DataSourceDescriptor descr) {
        if (!started) {
            return;
        }
        log.info("Unregistering datasource: " + descr.name);
        try {
            Context ctx = new InitialContext();
            ctx.unbind(ENV_CTX_NAME + descr.name);
        } catch (NotContextException e) {
            log.warn(e);
        } catch (NoInitialContextException e) {
            ;
        } catch (NamingException e) {
            log.error("Cannot unbind datasource '" + descr.name + "' in JNDI",
                    e);
        }
    }

    protected void addDataSourceLink(DataSourceLinkDescriptor contrib) {
        links.put(contrib.name, contrib);
        bindDataSourceLink(contrib);
    }

    protected void removeDataSourceLink(DataSourceLinkDescriptor contrib) {
        unbindDataSourceLink(contrib);
        links.remove(contrib.name);
    }

    protected void bindDataSourceLink(DataSourceLinkDescriptor descr) {
        if (!started) {
            return;
        }
        log.info("Registering DataSourceLink: " + descr.name);
        try {
            Name name = new CompositeName(ENV_CTX_NAME + descr.name);
            Context ctx = new InitialContext();
            // bind intermediate names as subcontexts (jdbc/foo)
            for (int i = 0; i < name.size() - 1; i++) {
                try {
                    ctx = (Context) ctx.lookup(name.get(i));
                } catch (NamingException e) {
                    ctx = ctx.createSubcontext(name.get(i));
                }
            }
            ctx.bind(name.get(name.size() - 1), new LinkRef(ENV_CTX_NAME + descr.global));
        } catch (NamingException e) {
            log.error("Cannot bind DataSourceLink '" + descr.name + "' in JNDI", e);
        }
    }

    protected void unbindDataSourceLink(DataSourceLinkDescriptor descr) {
        if (!started) {
            return;
        }
        log.info("Unregistering DataSourceLink: " + descr.name);
        try {
            Context ctx = new InitialContext();
            ctx.unbind(ENV_CTX_NAME + descr.name);
        } catch (NotContextException e) {
            log.warn(e);
        } catch (NoInitialContextException e) {
            ;
        } catch (NamingException e) {
            log.error("Cannot unbind DataSourceLink '" + descr.name + "' in JNDI",
                    e);
        }
    }

}
