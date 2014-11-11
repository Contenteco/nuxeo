/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Benoit Delbosc
 */
package org.nuxeo.ecm.platform.query.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.api.AbstractSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.storage.sql.SQLRepositoryTestCase;
import org.nuxeo.ecm.platform.query.api.AggregateDefinition;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 5.9.6
 */
public class TestPageProviderAggregates extends SQLRepositoryTestCase {

    protected PageProviderService pps;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        openSession();
        pps = Framework.getService(PageProviderService.class);
        assertNotNull(pps);
    }

    @After
    public void tearDown() throws Exception {
        closeSession();
        super.tearDown();
    }

    @Override
    protected void deployRepositoryContrib() throws Exception {
        super.deployRepositoryContrib();
        deployContrib("org.nuxeo.ecm.platform.query.api",
                "OSGI-INF/pageprovider-framework.xml");
        deployContrib("org.nuxeo.ecm.platform.query.api.test",
                "test-pageprovider-contrib.xml");
        deployContrib("org.nuxeo.ecm.platform.query.api.test",
                "test-schemas-contrib.xml");
    }

    @Test
    public void testAggregateDefinitionEmpty() throws Exception {

        PageProviderDefinition ppd = pps
                .getPageProviderDefinition("CURRENT_DOCUMENT_CHILDREN");
        List<AggregateDefinition> aggs = ppd.getAggregates();
        assertNotNull(aggs);
        assertEquals(0, aggs.size());
        assertEquals(Collections.<AggregateDefinition> emptyList(), aggs);
    }

    @Test
    public void testAggregateDefinition() throws Exception {
        PageProviderDefinition ppd = pps
                .getPageProviderDefinition("TEST_AGGREGATES");
        List<AggregateDefinition> aggs = ppd.getAggregates();
        assertNotNull(aggs);
        assertEquals(5, aggs.size());
        AggregateDefinition source_agg = aggs.get(0);
        AggregateDefinition coverage_agg = aggs.get(1);
        AggregateDefinition subject_agg = aggs.get(2);
        AggregateDefinition size_agg = aggs.get(3);
        AggregateDefinition created_agg = aggs.get(4);

        assertEquals("source_agg", source_agg.getId());
        assertEquals("terms", source_agg.getType());
        assertEquals("dc:source", source_agg.getDocumentField());
        assertEquals("advanced_search", source_agg.getSearchField().getSchema());
        assertEquals("source_agg", source_agg.getSearchField().getName());
        assertTrue(source_agg.getProperties().containsKey("minDocSize"));

        assertEquals("coverage_agg", coverage_agg.getId());

        assertNotNull(source_agg.getProperties());
        assertEquals(2, source_agg.getProperties().size());
        assertEquals(0, subject_agg.getProperties().size());
        assertEquals(0, source_agg.getRanges().size());
        // range
        assertEquals("size_agg", size_agg.getId());
        assertEquals(3, size_agg.getRanges().size());
        assertEquals("AggregateRangeDescriptor(small, null, 1024.0)", size_agg
                .getRanges().get(0).toString());
        assertEquals("AggregateRangeDescriptor(medium, 1024.0, 4096.0)",
                size_agg.getRanges().get(1).toString());
        assertEquals("AggregateRangeDescriptor(big, 4096.0, null)", size_agg
                .getRanges().get(2).toString());
        // date range
        assertEquals("created_agg", created_agg.getId());
        assertEquals(0, created_agg.getRanges().size());
        assertEquals(3, created_agg.getDateRanges().size());
        assertEquals(
                "AggregateRangeDateDescriptor(long_time_ago, null, NOW-10M/M)",
                created_agg.getDateRanges().get(0).toString());
        assertEquals(
                "AggregateRangeDateDescriptor(last_month, NOW-1M/M, null)",
                created_agg.getDateRanges().get(2).toString());
    }

    @Test
    public void testAggregateSelection() throws Exception {
        PageProviderDefinition ppd = pps
                .getPageProviderDefinition("TEST_AGGREGATES");
        HashMap<String, Serializable> props = new HashMap<String, Serializable>();
        props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY,
                (AbstractSession) session);
        DocumentModel searchDoc = new DocumentModelImpl("/", "doc",
                "AdvancedSearch");
        String[] query = {"for search", "you know"};
        searchDoc.setPropertyValue("search:source_agg", query);
        PageProvider<?> pp = pps.getPageProvider("TEST_AGGREGATES", ppd,
                searchDoc, null, Long.valueOf(1), Long.valueOf(0), null);
        assertNotNull(pp);

        List<AggregateDefinition> aggDefs = pp.getAggregateDefinitions();
        assertEquals(5, aggDefs.size());
        for (AggregateDefinition def : aggDefs) {
            AggregateBase agg = new AggregateBase(def, pp.getSearchDocumentModel());
            switch (agg.getId()) {
                case "source_agg":
                    assertEquals("Aggregate(source_agg, terms, dc:source, [for search, you know], null)",
                            agg.toString());
                    break;
                case "coverage_agg":
                    assertEquals("Aggregate(coverage_agg, histogram, dc:coverage, [], null)",
                            agg.toString());
            }
        }
    }
}
