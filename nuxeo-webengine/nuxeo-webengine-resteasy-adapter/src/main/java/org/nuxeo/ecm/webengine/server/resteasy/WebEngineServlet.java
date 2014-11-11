/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *
 * $Id$
 */

package org.nuxeo.ecm.webengine.server.resteasy;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Path;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.resteasy.core.Dispatcher;
import org.jboss.resteasy.core.ThreadLocalResteasyProviderFactory;
import org.jboss.resteasy.plugins.providers.ByteArrayProvider;
import org.jboss.resteasy.plugins.providers.DefaultTextPlain;
import org.jboss.resteasy.plugins.providers.FormUrlEncodedProvider;
import org.jboss.resteasy.plugins.providers.InputStreamProvider;
import org.jboss.resteasy.plugins.providers.StreamingOutputProvider;
import org.jboss.resteasy.plugins.providers.StringTextStar;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.jboss.resteasy.plugins.server.servlet.HttpServletInputMessage;
import org.jboss.resteasy.plugins.server.servlet.HttpServletResponseWrapper;
import org.jboss.resteasy.plugins.server.servlet.ServletSecurityContext;
import org.jboss.resteasy.plugins.server.servlet.ServletUtil;
import org.jboss.resteasy.specimpl.UriInfoImpl;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.nuxeo.ecm.platform.web.common.exceptionhandling.DefaultNuxeoExceptionHandler;
import org.nuxeo.ecm.platform.web.common.exceptionhandling.ExceptionHelper;
import org.nuxeo.ecm.webengine.WebEngine;
import org.nuxeo.ecm.webengine.WebException;
import org.nuxeo.ecm.webengine.model.WebContext;
import org.nuxeo.ecm.webengine.session.UserSession;
import org.nuxeo.runtime.api.Framework;

/**
 * Mostly copied from {@link HttpServletDispatcher}.
 * <p>
 * Modifications:
 * <ul>
 * <li>Changed Dispatcher implementation.
 * <li>Added methods to register root resources without {@link Path}
 * annotation.
 * <li>Added WebEngine initialization
 * </ul>
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class WebEngineServlet extends HttpServlet {

    private static final Log log = LogFactory.getLog(WebEngineServlet.class);

    private static final long serialVersionUID = 1L;

    private static boolean isTaglibLoaded = false;

    protected boolean enableJsp = false;;

    protected WebEngineDispatcher dispatcher;

    // protected WebEngine engine;

    protected void initializeBuiltinProviders(
            ResteasyProviderFactory providerFactory) {
        // RegisterBuiltin.register(providerFactory);
        try {
            providerFactory.addMessageBodyReader(new DefaultTextPlain());
            providerFactory.addMessageBodyWriter(new DefaultTextPlain());
            providerFactory.addMessageBodyReader(new StringTextStar());
            providerFactory.addMessageBodyWriter(new StringTextStar());
            providerFactory.addMessageBodyReader(new InputStreamProvider());
            providerFactory.addMessageBodyWriter(new InputStreamProvider());
            providerFactory.addMessageBodyReader(new ByteArrayProvider());
            providerFactory.addMessageBodyWriter(new ByteArrayProvider());
            providerFactory.addMessageBodyReader(new FormUrlEncodedProvider());
            providerFactory.addMessageBodyWriter(new FormUrlEncodedProvider());
            providerFactory.addMessageBodyWriter(new StreamingOutputProvider());
        } catch (Throwable t) {
            log.error(t);
        }
    }

    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        ResourceContainer rc = (ResourceContainer) Framework.getRuntime().getComponent(
                ResourceContainer.NAME);
        dispatcher = rc.getDispatcher();
        initializeBuiltinProviders(dispatcher.getProviderFactory());
        String v = Framework.getProperty("org.nuxeo.ecm.webengine.enableJsp");
        if ("true".equals(v)) {
            enableJsp = true;
        }
    }

    @Override
    protected void service(HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) throws ServletException,
            IOException {
        // need to set the encoding of characters manually
        if (null == httpServletRequest.getCharacterEncoding()) {
            httpServletRequest.setCharacterEncoding("UTF-8");
        }

        if (enableJsp) {
            WebEngine engine = Framework.getLocalService(WebEngine.class);
            if (!isTaglibLoaded) {
                synchronized (this) {
                    if (!isTaglibLoaded) {
                        engine.loadJspTaglib(this);
                        isTaglibLoaded = true;
                    }
                }
            }
            engine.initJspRequestSupport(this, httpServletRequest,
                    httpServletResponse);
        }

        service(httpServletRequest.getMethod(), httpServletRequest,
                httpServletResponse);

        // check if the target resource don't want automatic headers to be inserted
        if (null != httpServletRequest.getAttribute("org.nuxeo.webengine.DisableAutoHeaders")) {
            // insert automatic headers
            httpServletResponse.addHeader("Pragma", "no-cache");
            httpServletResponse.addHeader("Cache-Control", "no-cache");
            httpServletResponse.addHeader("Cache-Control", "no-store");
            httpServletResponse.addHeader("Cache-Control", "must-revalidate");
            httpServletResponse.addHeader("Expires", "0");
            httpServletResponse.setDateHeader("Expires", 0); // prevents caching
        }

    }

    public void service(String httpMethod, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        WebContext ctx = null;
        try {
            // classloader/deployment aware RestasyProviderFactory. Used to have
            // request specific
            // ResteasyProviderFactory.getInstance()
            ResteasyProviderFactory defaultInstance = ResteasyProviderFactory.getInstance();
            if (defaultInstance instanceof ThreadLocalResteasyProviderFactory) {
                ThreadLocalResteasyProviderFactory.push(dispatcher.getProviderFactory()); // BS
                // modif
            }
            HttpHeaders headers = ServletUtil.extractHttpHeaders(request);
            UriInfoImpl uriInfo = ServletUtil.extractUriInfo(request,
                    request.getServletPath()); // BS modif
            // org.jboss.resteasy.specimpl.patch.UriInfoImpl uriInfo =
            // org.jboss.resteasy.specimpl.patch.UriInfoImpl.create(request); //
            // BS: resteasy bug - it is not auto escaping @ in literal part of
            // @Path expression

            HttpResponse theResponse = createServletResponse(response);
            HttpRequest in = createHttpRequest(httpMethod, request, headers,
                    uriInfo, theResponse);

            // bs: initialize webengine context
            ctx = new WebEngineContext(in, request);
            request.setAttribute(WebContext.class.getName(), ctx);

            try {
                ResteasyProviderFactory.pushContext(HttpServletRequest.class,
                        request);
                ResteasyProviderFactory.pushContext(HttpServletResponse.class,
                        response);
                ResteasyProviderFactory.pushContext(SecurityContext.class,
                        new ServletSecurityContext(request));
                dispatcher.invoke(in, theResponse);
            } finally {
                ResteasyProviderFactory.clearContextData();
            }
        } catch (Throwable t) {
            Throwable unwrappedException = DefaultNuxeoExceptionHandler.unwrapException(t);
            if (ExceptionHelper.isClientAbortError(unwrappedException)) {
                log.warn("Client disconnected: " + unwrappedException.getMessage());
            } else {
                if (response.isCommitted()) {
                    log.error("Cannot display error page: "
                            + "response is already commited", t);
                } else {
                    log.error("Failed to render resource", t);
                    WebException exception = WebException.wrap(
                            "Failed to render resource", t);
                    response.sendError(500, exception.getStackTraceString());
                }
            }
        } finally {
            ResteasyProviderFactory defaultInstance = ResteasyProviderFactory.getInstance();
            if (defaultInstance instanceof ThreadLocalResteasyProviderFactory) {
                ThreadLocalResteasyProviderFactory.pop();
            }
            ResteasyProviderFactory.clearContextData();
        }
    }

    protected HttpRequest createHttpRequest(String httpMethod,
            HttpServletRequest request, HttpHeaders headers, UriInfo uriInfo,
            HttpResponse theResponse) {
        return new HttpServletInputMessage(request, theResponse, headers,
                uriInfo, httpMethod.toUpperCase(), dispatcher);
    }

    protected HttpResponse createServletResponse(HttpServletResponse response) {
        return new HttpServletResponseWrapper(response,
                dispatcher.getProviderFactory());
    }

}
