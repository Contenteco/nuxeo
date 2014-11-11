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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.AbstractPageProvider;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.ClientRuntimeException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.PageSelections;
import org.nuxeo.ecm.core.api.SortInfo;

/**
 * @author Anahide Tchertchian
 */
public class CoreQueryAndFetchPageProvider extends
        AbstractPageProvider<Map<String, Serializable>> implements
        ContentViewPageProvider<Map<String, Serializable>> {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(CoreQueryDocumentPageProvider.class);

    public static final String CORE_SESSION_PROPERTY = "coreSession";

    public static final String CHECK_QUERY_CACHE_PROPERTY = "checkQueryCache";

    protected PageProviderDescriptor descriptor;

    protected String query;

    protected List<Map<String, Serializable>> currentItems;

    public void setPageProviderDescriptor(
            PageProviderDescriptor providerDescriptor) {
        descriptor = providerDescriptor;
    }

    @Override
    public List<Map<String, Serializable>> getCurrentPage() {
        checkQueryCache();
        if (currentItems == null) {
            errorMessage = null;
            error = null;

            if (query == null) {
                buildQuery();
            }
            if (query == null) {
                throw new ClientRuntimeException(String.format(
                        "Cannot perform null query: check provider '%s'",
                        getName()));
            }

            currentItems = new ArrayList<Map<String, Serializable>>();

            Map<String, Serializable> props = getProperties();
            CoreSession coreSession = (CoreSession) props.get(CORE_SESSION_PROPERTY);
            if (coreSession == null) {
                throw new ClientRuntimeException("cannot find core session");
            }

            IterableQueryResult result = null;
            try {

                if (log.isDebugEnabled()) {
                    log.debug(String.format(
                            "Perform query for provider '%s': '%s' with pageSize=%s, offset=%s",
                            getName(), query, Long.valueOf(getPageSize()),
                            Long.valueOf(offset)));
                }

                result = coreSession.queryAndFetch(query, "NXQL");
                resultsCount = result.size();
                if (offset < resultsCount) {
                    result.skipTo(offset);
                }

                Iterator<Map<String, Serializable>> it = result.iterator();
                int pos = 0;
                while (it.hasNext() && pos < pageSize) {
                    pos += 1;
                    Map<String, Serializable> item = it.next();
                    currentItems.add(item);
                }

                if (log.isDebugEnabled()) {
                    log.debug(String.format(
                            "Performed query for provider '%s': got %s hits",
                            getName(), Long.valueOf(resultsCount)));
                }

            } catch (ClientException e) {
                errorMessage = e.getMessage();
                error = e;
                log.warn(e.getMessage(), e);
            } finally {
                if (result != null) {
                    result.close();
                }
            }
        }

        return currentItems;
    }

    protected void buildQuery() {
        try {
            String originalQuery = descriptor.getPattern();

            SortInfo[] sortArray = null;
            if (sortInfos != null) {
                sortArray = sortInfos.toArray(new SortInfo[] {});
            }
            String newQuery = NXQLQueryBuilder.getQuery(originalQuery,
                    getParameters(), descriptor.getQuotePatternParameters(),
                    sortArray);

            if (!newQuery.equals(query)) {
                // query has changed => refresh
                refresh();
                query = newQuery;
            }
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

    @Override
    public PageSelections<Map<String, Serializable>> getCurrentSelectPage() {
        checkQueryCache();
        return super.getCurrentSelectPage();
    }

    protected void checkQueryCache() {
        // maybe handle refresh of select page according to query
        Map<String, Serializable> props = getProperties();
        if (props.containsKey(CHECK_QUERY_CACHE_PROPERTY)
                && Boolean.TRUE.equals(Boolean.valueOf((String) props.get(CHECK_QUERY_CACHE_PROPERTY)))) {
            buildQuery();
        }

    }

    public String getCurrentQuery() {
        return query;
    }

    @Override
    public void refresh() {
        super.refresh();
        query = null;
        currentItems = null;
    }

}
