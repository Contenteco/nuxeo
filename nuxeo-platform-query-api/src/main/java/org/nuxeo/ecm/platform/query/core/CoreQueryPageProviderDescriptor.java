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
package org.nuxeo.ecm.platform.query.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.SortInfo;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.WhereClauseDefinition;

/**
 * Core Query page provider descriptor.
 *
 * @author Anahide Tchertchian
 * @since 5.4
 */
@XObject("coreQueryPageProvider")
public class CoreQueryPageProviderDescriptor implements PageProviderDefinition {

    private static final long serialVersionUID = 1L;

    @XNode("@name")
    protected String name;

    @XNode("@enabled")
    protected boolean enabled = true;

    @XNodeMap(value = "property", key = "@name", type = HashMap.class, componentType = String.class)
    protected Map<String, String> properties = new HashMap<String, String>();

    @XNodeList(value = "parameter", type = String[].class, componentType = String.class)
    protected String[] queryParameters;

    @XNode("pageSize")
    protected long pageSize = 0;

    @XNode("pageSizeBinding")
    protected String pageSizeBinding;

    @XNode("maxPageSize")
    protected Long maxPageSize;

    @XNode("sortable")
    protected boolean sortable = true;

    @XNodeList(value = "sort", type = ArrayList.class, componentType = SortInfoDescriptor.class)
    protected List<SortInfoDescriptor> sortInfos;

    @XNode("sortInfosBinding")
    protected String sortInfosBinding;

    protected String pattern;

    @XNode("pattern@quoteParameters")
    protected boolean quotePatternParameters = true;

    @XNode("pattern@escapeParameters")
    protected boolean escapePatternParameters = true;

    @XNode("whereClause")
    protected WhereClauseDescriptor whereClause;

    @XNode("pattern")
    public void setPattern(String pattern) {
        // remove new lines and following spaces
        if (pattern != null) {
            this.pattern = pattern.replaceAll("\r?\n\\s*", " ");
        }
    }

    public boolean getQuotePatternParameters() {
        return quotePatternParameters;
    }

    public boolean getEscapePatternParameters() {
        return escapePatternParameters;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public String[] getQueryParameters() {
        return queryParameters;
    }

    public String getPattern() {
        return pattern;
    }

    public WhereClauseDefinition getWhereClause() {
        return whereClause;
    }

    public boolean isSortable() {
        return sortable;
    }

    public List<SortInfo> getSortInfos() {
        List<SortInfo> res = new ArrayList<SortInfo>();
        if (sortInfos != null) {
            for (SortInfoDescriptor sortInfo : sortInfos) {
                res.add(sortInfo.getSortInfo());
            }
        }
        return res;
    }

    public long getPageSize() {
        return pageSize;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPageSizeBinding() {
        return pageSizeBinding;
    }

    public String getSortInfosBinding() {
        return sortInfosBinding;
    }

    public String getName() {
        return name;
    }

    @Override
    public Long getMaxPageSize() {
        return maxPageSize;
    }

    /**
     * @since 5.6
     */
    public CoreQueryPageProviderDescriptor clone() {
        CoreQueryPageProviderDescriptor clone = new CoreQueryPageProviderDescriptor();
        clone.name = getName();
        clone.enabled = isEnabled();
        Map<String, String> props = getProperties();
        if (props != null) {
            clone.properties = new HashMap<String, String>();
            clone.properties.putAll(props);
        }
        String[] params = getQueryParameters();
        if (params != null) {
            clone.queryParameters = params.clone();
        }
        clone.pageSize = getPageSize();
        clone.pageSizeBinding = getPageSizeBinding();
        clone.maxPageSize = getMaxPageSize();
        clone.sortable = isSortable();
        if (sortInfos != null) {
            clone.sortInfos = new ArrayList<SortInfoDescriptor>();
            for (SortInfoDescriptor item : sortInfos) {
                clone.sortInfos.add(item.clone());
            }
        }
        clone.sortInfosBinding = getSortInfosBinding();
        clone.pattern = getPattern();
        clone.quotePatternParameters = getQuotePatternParameters();
        clone.escapePatternParameters = getEscapePatternParameters();
        if (whereClause != null) {
            clone.whereClause = whereClause.clone();
        }
        return clone;
    }

}
