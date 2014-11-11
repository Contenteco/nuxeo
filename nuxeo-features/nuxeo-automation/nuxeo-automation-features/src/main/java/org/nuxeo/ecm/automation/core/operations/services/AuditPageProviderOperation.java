package org.nuxeo.ecm.automation.core.operations.services;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.DocumentHelper;
import org.nuxeo.ecm.automation.core.util.Paginable;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.automation.jaxrs.io.audit.LogEntryList;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.SortInfo;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.platform.audit.api.AuditPageProvider;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.core.GenericPageProviderDescriptor;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.runtime.api.Framework;

/**
 * Operation to execute a query or a named provider against Audit with support
 * for Pagination
 *
 * @author Tiry (tdelprat@nuxeo.com)
 * @since 5.8
 */
@Operation(id = AuditPageProviderOperation.ID, category = Constants.CAT_FETCH, label = "AuditPageProvider", description = "Perform "
        + "a query or a named provider query against Audit logs. Result is "
        + "paginated. The query result will become the input for the next "
        + "operation. If no query or provider name is given, a query based on default Audit page provider will be executed.", addToStudio=false)
public class AuditPageProviderOperation {

    public static final String ID = "Audit.PageProvider";

    public static final String CURRENT_USERID_PATTERN = "$currentUser";

    public static final String CURRENT_REPO_PATTERN = "$currentRepository";

    private static final String SORT_PARAMETER_SEPARATOR = " ";

    @Context
    protected OperationContext context;

    @Context
    protected CoreSession session;

    @Context
    protected PageProviderService ppService;

    @Param(name = "providerName", required = false)
    protected String providerName;

    @Param(name = "query", required = false)
    protected String query;

    @Param(name = "language", required = false, widget = Constants.W_OPTION, values = { NXQL.NXQL })
    protected String lang = NXQL.NXQL;

    @Param(name = "page", required = false)
    @Deprecated
    protected Integer page;

    @Param(name = "currentPageIndex", required = false)
    protected Integer currentPageIndex;

    @Param(name = "pageSize", required = false)
    protected Integer pageSize;

    @Param(name = "sortInfo", required = false)
    protected StringList sortInfoAsStringList;

    @Param(name = "queryParams", required = false)
    protected StringList strParameters;

    @Param(name = "namedQueryParams", required = false)
    protected Properties namedQueryParams;

    @Param(name = "maxResults", required = false)
    protected Integer maxResults = 100;

    @SuppressWarnings("unchecked")
    @OperationMethod
    public Paginable<LogEntry> run() throws Exception {

        PageProviderService pps = Framework.getLocalService(PageProviderService.class);

        List<SortInfo> sortInfos = null;
        if (sortInfoAsStringList != null) {
            sortInfos = new ArrayList<SortInfo>();
            for (String sortInfoDesc : sortInfoAsStringList) {
                SortInfo sortInfo;
                if (sortInfoDesc.contains(SORT_PARAMETER_SEPARATOR)) {
                    String[] parts = sortInfoDesc.split(SORT_PARAMETER_SEPARATOR);
                    sortInfo = new SortInfo(parts[0],
                            Boolean.parseBoolean(parts[1]));
                } else {
                    sortInfo = new SortInfo(sortInfoDesc, true);
                }
                sortInfos.add(sortInfo);
            }
        }

        Object[] parameters = null;

        if (strParameters != null && !strParameters.isEmpty()) {
            parameters = strParameters.toArray(new String[strParameters.size()]);
            // expand specific parameters
            for (int idx = 0; idx < parameters.length; idx++) {
                String value = (String) parameters[idx];
                if (value.equals(CURRENT_USERID_PATTERN)) {
                    parameters[idx] = session.getPrincipal().getName();
                } else if (value.equals(CURRENT_REPO_PATTERN)) {
                    parameters[idx] = session.getRepositoryName();
                }
            }
        }
        if (parameters == null) {
            parameters = new Object[0];
        }

        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY,
                (Serializable) session);

        if (query == null
                && (providerName == null || providerName.length() == 0)) {
            // provide a defaut provider
            providerName = "AUDIT_BROWSER";
        }

        Long targetPage = null;
        if (page != null) {
            targetPage = Long.valueOf(page.longValue());
        }
        if (currentPageIndex != null) {
            targetPage = currentPageIndex.longValue();
        }
        Long targetPageSize = null;
        if (pageSize != null) {
            targetPageSize = Long.valueOf(pageSize.longValue());
        }

        if (query != null) {

            AuditPageProvider app = new AuditPageProvider();
            app.setProperties(props);
            if (maxResults != null && !maxResults.equals("-1")) {
                // set the maxResults to avoid slowing down queries
                app.getProperties().put("maxResults", maxResults);
                app.setMaxPageSize(maxResults);
            }
            GenericPageProviderDescriptor desc = new GenericPageProviderDescriptor();
            desc.setPattern(query);
            app.setParameters(parameters);
            app.setDefinition(desc);
            app.setSortInfos(sortInfos);
            app.setPageSize(targetPageSize);
            app.setCurrentPage(targetPage);
            return new LogEntryList(app);
        } else {

            DocumentModel searchDoc = null;
            if (namedQueryParams != null && namedQueryParams.size() > 0) {
                String docType = pps.getPageProviderDefinition(providerName).getWhereClause().getDocType();
                searchDoc = session.createDocumentModel(docType);
                DocumentHelper.setProperties(session, searchDoc,
                        namedQueryParams);
            }

            PageProvider<LogEntry> pp = (PageProvider<LogEntry>) pps.getPageProvider(
                    providerName, sortInfos, targetPageSize, targetPage, props,
                    parameters);
            if (searchDoc != null) {
                pp.setSearchDocumentModel(searchDoc);
            }
            //return new PaginablePageProvider<LogEntry>(pp);
            return new LogEntryList(pp);
        }

    }
}
