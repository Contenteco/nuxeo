/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 * $Id: $
 */

package org.nuxeo.ecm.platform.ui.web.rest;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.platform.ui.web.rest.api.URLPolicyService;
import org.nuxeo.ecm.platform.url.api.DocumentView;
import org.nuxeo.runtime.api.Framework;

/**
 * TODO: document me.
 *
 * @author tiry
 */
public class FancyURLFilter implements Filter {

    private static final Log log = LogFactory.getLog(FancyURLFilter.class);

    protected URLPolicyService urlService;

    protected StaticNavigationHandler dummyNavigationHandler;

    public void init(FilterConfig conf) throws ServletException {
        log.debug("Nuxeo5 URLFilter started");
        try {
            urlService = Framework.getService(URLPolicyService.class);
            dummyNavigationHandler = new StaticNavigationHandler(
                    conf.getServletContext());
        } catch (Exception e) {
            log.error("Could not retrieve the URLPolicyService",e);
        }
    }

    public void destroy() {
        urlService = null;
    }

    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // check if this is an URL that needs to be parsed
        if (urlService.isCandidateForDecoding(httpRequest)) {
            DocumentView docView = urlService.getDocumentViewFromRequest(httpRequest);
            // if parse succeeded => process
            if (docView != null) {
                // put it in request
                urlService.setDocumentViewInRequest(httpRequest, docView);

                // get the view id for navigation from the stored outcome
                String jsfOutcome = docView.getViewId();

                // get target page according to navigation rules
                String target = dummyNavigationHandler.getViewIdFromOutcome(jsfOutcome);

                // dispatch
                RequestDispatcher dispatcher;
                if (target != null) {
                    dispatcher = httpRequest.getRequestDispatcher(target);
                } else {
                    // use a dummy view id: if it's displayed then something
                    // went wrong
                    dispatcher = httpRequest.getRequestDispatcher("/generic_error_page.faces");
                }
                try {
                    // set force encoding in case forward triggers a redirect
                    // (when a seam page is processed for instance).
                    request.setAttribute(
                            URLPolicyService.FORCE_URL_ENCODING_REQUEST_KEY,
                            true);
                    // forward request to the target viewId
                    dispatcher.forward(new FancyURLRequestWrapper(httpRequest,
                            docView), wrapResponse(httpRequest, httpResponse));
                    return;
                } catch (Exception e) {
                    // do nothing
                }
            }
        }

        // do not filter if it's candidate for encoding so soon : document view
        // has not been set in the request yet => always wrap
        chain.doFilter(request, wrapResponse(httpRequest, httpResponse));
    }

    private ServletResponse wrapResponse(HttpServletRequest request,
            HttpServletResponse response) {
        return new FancyURLResponseWrapper(response, request,
                dummyNavigationHandler);
    }
}
