/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Arnaud Kervern
 */

package org.nuxeo.ecm.platform.web.common.requestcontroller.service;

import java.io.Serializable;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.api.Framework;

import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * @author <a href="mailto:ak@nuxeo.com">Arnaud Kervern</a>
 * @since 5.7.2
 */
@XObject(value = "corsConfig")
public class NuxeoCorsFilterDescriptor implements Serializable, Cloneable {

    private static final String PROPERTIES_PREFIX = "cors.";

    @XNode("@name")
    protected String name;

    @XNode("@enabled")
    protected Boolean enabled = true;

    @XNode("@allowGenericHttpRequests")
    protected Boolean allowGenericHttpRequests = true;

    @XNode("@allowOrigin")
    protected String allowOrigin;

    @XNode("@allowSubdomains")
    protected boolean allowSubdomains = false;

    @XNode("@supportedMethods")
    protected String supportedMethods;

    @XNode("@supportedHeaders")
    protected String supportedHeaders;

    @XNode("@exposedHeaders")
    protected String exposedHeaders;

    @XNode("@supportsCredentials")
    protected Boolean supportsCredentials = true;

    @XNode("@maxAge")
    protected int maxAge = -1;

    protected String pattern = "";

    @XNode("pattern")
    public void setPattern(String pattern) {
        this.pattern = Framework.expandVars(pattern);
    }

    public FilterConfig buildFilterConfig() {
        final Dictionary<String, String> parameters = buildDictionary();

        return new FilterConfig() {
            @Override
            public String getFilterName() {
                return "NuxeoCorsFilterDescriptor";
            }

            @Override
            public ServletContext getServletContext() {
                // Not used with @see CORSFilter
                return null;
            }

            @Override
            public String getInitParameter(String name) {
                return parameters.get(name);
            }

            @Override
            public Enumeration getInitParameterNames() {
                return parameters.keys();
            }
        };
    }

    public boolean isMatching(HttpServletRequest request) {
        return !StringUtils.isEmpty(pattern)
                && request.getRequestURI().matches(pattern);
    }

    public NuxeoCorsFilterDescriptor clone() throws CloneNotSupportedException {
        NuxeoCorsFilterDescriptor n = new NuxeoCorsFilterDescriptor();
        n.name = name;
        n.allowGenericHttpRequests = allowGenericHttpRequests;
        n.allowOrigin = allowOrigin;
        n.allowSubdomains = allowSubdomains;
        n.supportedMethods = supportedMethods;
        n.supportedHeaders = supportedHeaders;
        n.exposedHeaders = exposedHeaders;
        n.supportsCredentials = supportsCredentials;
        n.maxAge = maxAge;
        n.pattern = pattern;
        return n;
    }

    public void merge(NuxeoCorsFilterDescriptor o) {
        allowGenericHttpRequests = o.allowGenericHttpRequests;
        supportsCredentials = o.supportsCredentials;
        allowSubdomains = o.allowSubdomains;

        if (!StringUtils.isEmpty(o.allowOrigin)) {
            allowOrigin = o.allowOrigin;
        }

        if (!StringUtils.isEmpty(o.supportedMethods)) {
            supportedMethods = o.supportedMethods;
        }

        if (!StringUtils.isEmpty(o.supportedHeaders)) {
            supportedHeaders = o.supportedHeaders;
        }

        if (!StringUtils.isEmpty(o.exposedHeaders)) {
            exposedHeaders = o.exposedHeaders;
        }

        if (maxAge == -1) {
            maxAge = o.maxAge;
        }

        if (!StringUtils.isEmpty(o.pattern)) {
            pattern = o.pattern;
        }
    }

    protected Dictionary<String, String> buildDictionary() {
        Dictionary<String, String> params = new Hashtable<>();
        params.put(PROPERTIES_PREFIX + "allowGenericHttpRequests", Boolean.toString(allowGenericHttpRequests));

        if (!isEmpty(allowOrigin)) {
            params.put(PROPERTIES_PREFIX + "allowOrigin", allowOrigin);
        }

        params.put(PROPERTIES_PREFIX + "allowSubdomains", Boolean.toString(allowSubdomains));

        if (!isEmpty(supportedMethods)) {
            params.put(PROPERTIES_PREFIX + "supportedMethods", supportedMethods);
        }

        if (!isEmpty(supportedHeaders)) {
            params.put(PROPERTIES_PREFIX + "supportedHeaders", supportedHeaders);
        }

        if (!isEmpty(exposedHeaders)) {
            params.put(PROPERTIES_PREFIX + "exposedHeaders", exposedHeaders);
        }

        params.put(PROPERTIES_PREFIX + "supportsCredentials", Boolean.toString(supportsCredentials));
        params.put(PROPERTIES_PREFIX + "maxAge", Integer.toString(maxAge));

        return params;
    }
}
