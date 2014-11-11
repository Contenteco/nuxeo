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
 *     vpasquier
 *     slacoin
 */
package org.nuxeo.ecm.automation.server;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

import org.codehaus.jackson.JsonFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.io.services.IOComponent;
import org.nuxeo.ecm.automation.io.services.JsonFactoryManager;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class AutomationServerComponent extends DefaultComponent implements
        AutomationServer {

    /**
     * Was used to get the JsonFactory, but since 5.7.3 use either: *
     * <code>{@link JsonFactoryManager#getJsonFactory()}</code> * Context
     * annotation in JAX-RS object (providers or resources)
     */
    @Deprecated
    public static AutomationServerComponent me;

    protected static final String XP_BINDINGS = "bindings";

    protected static final String IOCOMPONENT_NAME = "org.nuxeo.ecm.automation.io.services.IOComponent";

    protected IOComponent ioComponent;

    private static final String XP_MARSHALLER = "marshallers";

    protected Map<String, RestBinding> bindings;

    protected static final String XP_CODECS = "codecs";

    protected volatile Map<String, RestBinding> lookup;

    protected List<Class<? extends MessageBodyWriter<?>>> writers;

    protected List<Class<? extends MessageBodyReader<?>>> readers;

    @Override
    public void activate(ComponentContext context) throws Exception {
        bindings = new HashMap<String, RestBinding>();
        writers = new ArrayList<>();
        readers = new ArrayList<>();
        me = this;
        ioComponent = ((IOComponent) Framework.getRuntime().getComponentInstance(
                IOCOMPONENT_NAME).getInstance());
    }

    @Override
    public void deactivate(ComponentContext context) throws Exception {
        bindings = null;
        me = null;
    }

    @Override
    public void registerContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {
        if (XP_BINDINGS.equals(extensionPoint)) {
            RestBinding binding = (RestBinding) contribution;
            addBinding(binding);
        } else if (XP_MARSHALLER.equals(extensionPoint)) {
            MarshallerDescriptor marshaller = (MarshallerDescriptor) contribution;
            writers.addAll(marshaller.getWriters());
            readers.addAll(marshaller.getReaders());
        } else if (XP_CODECS.equals(extensionPoint)) {
            ioComponent.registerContribution(contribution, extensionPoint,
                    contributor);
        }
    }

    @Override
    public void unregisterContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {
        if (XP_BINDINGS.equals(extensionPoint)) {
            RestBinding binding = (RestBinding) contribution;
            removeBinding(binding);
        } else if (XP_CODECS.equals(extensionPoint)) {
            ioComponent.unregisterContribution(contribution, extensionPoint,
                    contributor);
        }
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (AutomationServer.class.isAssignableFrom(adapter)) {
            return adapter.cast(this);
        }
        return null;
    }

    @Override
    public void applicationStarted(ComponentContext context) throws Exception {
        super.applicationStarted(context);
    }

    /**
     * Since 5.7.3, use {@link JsonFactoryManager#getJsonFactory()}
     */
    @Deprecated
    public JsonFactory getFactory() {
        return Framework.getLocalService(JsonFactoryManager.class).getJsonFactory();
    }

    @Override
    public RestBinding getOperationBinding(String name) {
        return lookup().get(name);
    }

    @Override
    public RestBinding getChainBinding(String name) {
        return lookup().get(Constants.CHAIN_ID_PREFIX + name);
    }

    @Override
    public RestBinding[] getBindings() {
        Map<String, RestBinding> map = lookup();
        return map.values().toArray(new RestBinding[map.size()]);
    }

    protected String getBindingKey(RestBinding binding) {
        return binding.isChain() ? Constants.CHAIN_ID_PREFIX
                + binding.getName() : binding.getName();
    }

    @Override
    public synchronized void addBinding(RestBinding binding) {
        String key = getBindingKey(binding);
        bindings.put(key, binding);
        lookup = null;
    }

    @Override
    public synchronized RestBinding removeBinding(RestBinding binding) {
        RestBinding result = bindings.remove(getBindingKey(binding));
        lookup = null;
        return result;
    }

    @Override
    public boolean accept(String name, boolean isChain, HttpServletRequest req) {
        if (isChain) {
            name = "Chain." + name;
        }
        RestBinding binding = lookup().get(name);
        if (binding != null) {
            if (binding.isDisabled()) {
                return false;
            }
            if (binding.isSecure()) {
                if (!req.isSecure()) {
                    return false;
                }
            }
            Principal principal = req.getUserPrincipal();

            if (binding.isAdministrator() || binding.hasGroups()) {
                if (principal instanceof NuxeoPrincipal) {
                    NuxeoPrincipal np = (NuxeoPrincipal) principal;
                    if (binding.isAdministrator() && np.isAdministrator()) {
                        return true;
                    }
                    if (binding.hasGroups()) {
                        for (String group : binding.getGroups()) {
                            if (np.isMemberOf(group)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        }
        return true;
    }

    private Map<String, RestBinding> lookup() {
        Map<String, RestBinding> _lookup = lookup;
        if (_lookup == null) {
            synchronized (this) {
                lookup = new HashMap<String, RestBinding>(bindings);
                _lookup = lookup;
            }
        }
        return _lookup;
    }

    @Override
    public List<Class<? extends MessageBodyWriter<?>>> getWriters() {
        return writers;
    }

    @Override
    public List<Class<? extends MessageBodyReader<?>>> getReaders() {
        return readers;
    }

}
