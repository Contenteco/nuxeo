/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.platform.ui.web.contentview;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.AbstractPageProvider;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.ClientRuntimeException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.PageSelections;
import org.nuxeo.ecm.core.api.SortInfo;

/**
 * Page provider performing a query on a core session and returning
 * <p>
 * It builds the query at each call so that it can refresh itself when the
 * query changes.
 * <p>
 * TODO: describe needed properties
 *
 * @author Anahide Tchertchian
 */
public class CoreQueryDocumentPageProvider extends
        AbstractPageProvider<DocumentModel> implements
        ContentViewPageProvider<DocumentModel> {

    private static final Log log = LogFactory.getLog(CoreQueryDocumentPageProvider.class);

    private static final long serialVersionUID = 1L;

    public static final String CORE_SESSION_PROPERTY = "coreSession";

    public static final String CHECK_QUERY_CACHE_PROPERTY = "checkQueryCache";

    protected PageProviderDescriptor descriptor;

    protected String query;

    protected List<DocumentModel> currentPageDocuments;

    public void setPageProviderDescriptor(
            PageProviderDescriptor providerDescriptor) {
        descriptor = providerDescriptor;
    }

    @Override
    public List<DocumentModel> getCurrentPage() {
        checkQueryCache();
        if (currentPageDocuments == null) {

            CoreSession coreSession = getCoreSession();
            if (query == null) {
                buildQuery(coreSession);
            }
            if (query == null) {
                throw new ClientRuntimeException(String.format(
                        "Cannot perform null query: check provider '%s'",
                        getName()));
            }

            currentPageDocuments = new ArrayList<DocumentModel>();

            try {

                if (log.isDebugEnabled()) {
                    log.debug(String.format(
                            "Perform query for provider '%s': '%s' with pageSize=%s, offset=%s",
                            getName(), query, Long.valueOf(getPageSize()),
                            Long.valueOf(offset)));
                }

                DocumentModelList docs = coreSession.query(query, null,
                        getPageSize(), offset, true);
                resultsCount = docs.totalSize();
                currentPageDocuments = docs;

                if (log.isDebugEnabled()) {
                    log.debug(String.format(
                            "Performed query for provider '%s': got %s hits",
                            getName(), Long.valueOf(resultsCount)));
                }

            } catch (Exception e) {
                throw new ClientRuntimeException(e);
            }
        }
        return currentPageDocuments;
    }

    protected void buildQuery(CoreSession coreSession) {
        try {
            SortInfo[] sortArray = null;
            if (sortInfos != null) {
                sortArray = sortInfos.toArray(new SortInfo[] {});
            }
            String newQuery;
            if (descriptor.getWhereClause() == null) {
                newQuery = NXQLQueryBuilder.getQuery(descriptor.getPattern(),
                        getParameters(),
                        descriptor.getQuotePatternParameters(), sortArray);
            } else {
                DocumentModel searchDocumentModel = getSearchDocumentModel();
                if (searchDocumentModel == null) {
                    throw new ClientException(String.format(
                            "Cannot build query of provider '%s': "
                                    + "no search document model is set",
                            getName()));
                }
                newQuery = NXQLQueryBuilder.getQuery(searchDocumentModel,
                        descriptor.getWhereClause(), getParameters(), sortArray);
            }

            if (newQuery != null && !newQuery.equals(query)) {
                // query has changed => refresh
                refresh();
                query = newQuery;
            }
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

    protected void checkQueryCache() {
        // maybe handle refresh of select page according to query
        Map<String, Serializable> props = getProperties();
        if (props.containsKey(CHECK_QUERY_CACHE_PROPERTY)
                && Boolean.TRUE.equals(Boolean.valueOf((String) props.get(CHECK_QUERY_CACHE_PROPERTY)))) {
            CoreSession coreSession = getCoreSession();
            buildQuery(coreSession);
        }
    }

    protected CoreSession getCoreSession() {
        Map<String, Serializable> props = getProperties();
        CoreSession coreSession = (CoreSession) props.get(CORE_SESSION_PROPERTY);
        if (coreSession == null) {
            throw new ClientRuntimeException("cannot find core session");
        }
        return coreSession;
    }

    @Override
    public PageSelections<DocumentModel> getCurrentSelectPage() {
        checkQueryCache();
        return super.getCurrentSelectPage();
    }

    public String getCurrentQuery() {
        return query;
    }

    @Override
    public void refresh() {
        super.refresh();
        query = null;
        currentPageDocuments = null;
    }

}
