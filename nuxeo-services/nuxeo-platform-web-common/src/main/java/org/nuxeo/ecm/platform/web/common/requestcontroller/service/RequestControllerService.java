/*
 * (C) Copyright 2006-2009 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.ecm.platform.web.common.requestcontroller.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Runtime component that implements the {@link RequestControllerManager}
 * interface. Contains both the Extension point logic and the service
 * implementation.
 *
 * @author tiry
 */
public class RequestControllerService extends DefaultComponent implements
        RequestControllerManager {

    public static final String FILTER_CONFIG_EP = "filterConfig";

    public static final String CORS_CONFIG_EP = "corsConfig";

    private static final Log log = LogFactory.getLog(RequestControllerService.class);

    protected static final Map<String, FilterConfigDescriptor> grantPatterns = new LinkedHashMap<String, FilterConfigDescriptor>();

    protected static final Map<String, FilterConfigDescriptor> denyPatterns = new LinkedHashMap<String, FilterConfigDescriptor>();

    // @GuardedBy("itself")
    protected static final Map<String, RequestFilterConfig> configCache = new LRUCachingMap<String, RequestFilterConfig>(
            250);

    protected static final Map<String, FilterConfig> filterConfigCache = new LRUCachingMap<>(250);

    protected static final NuxeoCorsFilterDescriptorRegistry corsFilterRegistry = new NuxeoCorsFilterDescriptorRegistry();

    @Override
    public void registerContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {
        if (FILTER_CONFIG_EP.equals(extensionPoint)) {
            FilterConfigDescriptor desc = (FilterConfigDescriptor) contribution;
            registerFilterConfig(desc);
        } else if (CORS_CONFIG_EP.equals(extensionPoint)) {
            corsFilterRegistry.addContribution((NuxeoCorsFilterDescriptor)contribution);
        } else {
            log.error("Unknown ExtensionPoint " + extensionPoint);
        }
    }

    public void registerFilterConfig(String name, String pattern,
            boolean grant, boolean tx, boolean sync, boolean cached,
            boolean isPrivate, String cacheTime) {
        FilterConfigDescriptor desc = new FilterConfigDescriptor(name, pattern,
                grant, tx, sync, cached, isPrivate, cacheTime);
        registerFilterConfig(desc);
    }

    public void registerFilterConfig(FilterConfigDescriptor desc) {
        if (desc.isGrantRule()) {
            grantPatterns.put(desc.getName(), desc);
            log.debug("Registered grant filter config");
        } else {
            denyPatterns.put(desc.getName(), desc);
            log.debug("Registered deny filter config");
        }
    }

    @Override
    public void unregisterContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {
        if (CORS_CONFIG_EP.equals(extensionPoint)) {
            corsFilterRegistry.removeContribution((NuxeoCorsFilterDescriptor) contribution);
        }
    }

    /* Service interface */

    @Override
    public FilterConfig getCorsConfigForRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        FilterConfig filterConfig = null;
        synchronized (filterConfigCache) {
            filterConfig = filterConfigCache.get(uri);
        }

        if (filterConfig == null) {
            filterConfig = computeCorsFilterConfigForUri(uri);
            synchronized (filterConfigCache) {
                filterConfigCache.put(uri, filterConfig);
            }
        }

        return filterConfig;
    }

    public FilterConfig computeCorsFilterConfigForUri(String uri) {
        NuxeoCorsFilterDescriptor descriptor = corsFilterRegistry.getFirstMatchingDescriptor(uri);
        return descriptor != null ? descriptor.buildFilterConfig() : null;
    }

    public RequestFilterConfig getConfigForRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        RequestFilterConfig config = null;

        synchronized (configCache) {
            config = configCache.get(uri);
        }
        if (config == null) {
            config = computeConfigForRequest(uri);
            synchronized (configCache) {
                configCache.put(uri, config);
            }
        }
        return config;
    }

    public RequestFilterConfig computeConfigForRequest(String uri) {
        // handle deny patterns
        for (FilterConfigDescriptor desc : denyPatterns.values()) {
            Pattern pat = desc.getCompiledPattern();
            Matcher m = pat.matcher(uri);
            if (m.matches()) {
                return new RequestFilterConfigImpl(false, false, false, false,
                        false, "");
            }
        }

        // handle grant patterns
        for (FilterConfigDescriptor desc : grantPatterns.values()) {
            Pattern pat = desc.getCompiledPattern();
            Matcher m = pat.matcher(uri);
            if (m.matches()) {
                return new RequestFilterConfigImpl(desc.useSync(),
                        desc.useTx(), desc.useTxBuffered(), desc.isCached(),
                        desc.isPrivate(), desc.getCacheTime());
            }
        }

        // return deny by default
        return new RequestFilterConfigImpl(false, false, false, false, false,
                "");
    }

}
