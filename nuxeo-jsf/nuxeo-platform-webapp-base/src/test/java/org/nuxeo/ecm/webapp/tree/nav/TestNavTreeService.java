/*
 * (C) Copyright 2009 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Thierry Delprat
 *     Florent Guillaume
 */
package org.nuxeo.ecm.webapp.tree.nav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.actions.Action;
import org.nuxeo.ecm.platform.actions.ejb.ActionManager;
import org.nuxeo.ecm.webapp.directory.DirectoryTreeDescriptor;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
@LocalDeploy({
        "org.nuxeo.ecm.platform.actions.core:OSGI-INF/actions-framework.xml",
        "org.nuxeo.ecm.webapp.base:OSGI-INF/navtree-framework.xml",
        "org.nuxeo.ecm.webapp.base:test-navtree-contrib-compat.xml",
        "org.nuxeo.ecm.webapp.base:test-navtree-contrib.xml" })
public class TestNavTreeService {

    @Test
    public void testServiceLookup() {
        NavTreeService service = Framework.getLocalService(NavTreeService.class);
        assertNotNull(service);
    }

    @Test
    public void testNavTrees() throws Exception {
        NavTreeService service = Framework.getLocalService(NavTreeService.class);
        assertNotNull(service);

        List<NavTreeDescriptor> descs = service.getTreeDescriptors();
        assertEquals(2, descs.size());

        NavTreeDescriptor desc = descs.get(0);
        assertEquals("/incl/tag_cloud.xhtml", desc.getXhtmlview());
        assertEquals("TAG_CLOUD", desc.getTreeId());
        assertFalse(desc.isDirectoryTreeBased());

        desc = descs.get(1);
        assertEquals("/incl/tag_cloud.xhtml", desc.getXhtmlview());
        assertEquals("TAG_CLOUD_COMPAT", desc.getTreeId());
        assertFalse(desc.isDirectoryTreeBased());
    }

    @Test
    public void testNavTreeActions() throws Exception {
        ActionManager am = Framework.getService(ActionManager.class);
        List<Action> actions = am.getAllActions(DirectoryTreeDescriptor.NAV_ACTION_CATEGORY);
        assertEquals(2, actions.size());
        Action a = actions.get(0);
        assertEquals("navtree_TAG_CLOUD", a.getId());
        assertEquals(100, a.getOrder());
        assertEquals("/img/TAG_CLOUD.png", a.getIcon());
        assertEquals("my cloud", a.getLabel());
        assertEquals("/incl/tag_cloud.xhtml", a.getLink());
        assertEquals("rest_document_link", a.getType());
        a = actions.get(1);
        assertEquals("navtree_TAG_CLOUD_COMPAT", a.getId());
        assertEquals(100, a.getOrder());
        assertEquals("/img/TAG_CLOUD_COMPAT.png", a.getIcon());
        assertEquals("navtree_TAG_CLOUD_COMPAT", a.getLabel());
        assertEquals("/incl/tag_cloud.xhtml", a.getLink());
        assertEquals("rest_document_link", a.getType());
    }

}
