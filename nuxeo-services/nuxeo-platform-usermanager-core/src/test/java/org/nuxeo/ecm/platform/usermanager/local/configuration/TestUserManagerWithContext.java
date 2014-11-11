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
 * $Id: JOOoConvertPluginImpl.java 18651 2007-05-13 20:28:53Z sfermigier $
 */

package org.nuxeo.ecm.platform.usermanager.local.configuration;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.impl.SimpleDocumentModel;
import org.nuxeo.ecm.core.storage.sql.DatabaseHelper;
import org.nuxeo.ecm.platform.usermanager.DefaultUserMultiTenantManagementMock;
import org.nuxeo.ecm.platform.usermanager.UserManagerImpl;
import org.nuxeo.ecm.platform.usermanager.UserMultiTenantManagement;
import org.nuxeo.ecm.platform.usermanager.UserService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.NXRuntimeTestCase;

/**
 * These Test Cases test the usermanager when a Directory Local Configuration is
 * set.
 *
 * @author Benjamin JALON
 */
public class TestUserManagerWithContext extends NXRuntimeTestCase {

    protected UserManagerImpl userManager;

    protected UserService userService;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        DatabaseHelper.DATABASE.setUp();

        deployBundle("org.nuxeo.ecm.core.schema");
        deployBundle("org.nuxeo.ecm.core.api");
        deployBundle("org.nuxeo.ecm.core");
        deployContrib("org.nuxeo.ecm.platform.usermanager.tests",
                "test-usermanagerimpl-multitenant/DirectoryServiceMock.xml");
        deployBundle("org.nuxeo.ecm.directory.api");
        deployBundle("org.nuxeo.ecm.directory");
        deployBundle("org.nuxeo.ecm.directory.sql");
        deployBundle("org.nuxeo.ecm.directory.multi");
        deployBundle("org.nuxeo.ecm.directory.types.contrib");
        deployBundle("org.nuxeo.ecm.platform.usermanager");

        deployContrib("org.nuxeo.ecm.platform.usermanager.tests",
                "test-usermanagerimpl-multitenant/directory-for-context-config.xml");
        deployContrib("org.nuxeo.ecm.platform.usermanager.tests",
                "test-usermanagerimpl/userservice-config.xml");

        userService = (UserService) Framework.getRuntime().getComponent(
                UserService.NAME);

        userManager = (UserManagerImpl) userService.getUserManager();
        UserMultiTenantManagement umtm = new DefaultUserMultiTenantManagementMock();
        // to simulate the directory local configuration
        userManager.multiTenantManagement = umtm;
    }

    @After
    public void tearDown() throws Exception {
        DatabaseHelper.DATABASE.tearDown();
        super.tearDown();
    }

    @Test
    public void testGetAdministratorTenanta() throws Exception {
        NuxeoPrincipal principal = userManager.getPrincipal("Administrator@tenanta");
        assertNotNull(principal);
        assertTrue(principal.isMemberOf("administrators-tenanta"));
        assertFalse(principal.isMemberOf("administrators-tenantb"));
    }

    @Test
    public void testShouldReturnOnlyUserFromTenantA() throws Exception {

        DocumentModelList users = userManager.searchUsers("%%", null);

        assertEquals(2, users.size());

        DocumentModel fakeDoc = new SimpleDocumentModel();
        users = userManager.searchUsers("Administrator", fakeDoc);

        assertEquals(1, users.size());
        assertEquals("Administrator@tenanta",
                users.get(0).getPropertyValue("username"));
    }

    @Test
    public void testShouldReturnOnlyGroupsFromTenantA() throws Exception {

        Map<String, Serializable> filter = new HashMap<String, Serializable>();
        HashSet<String> fulltext = new HashSet<String>();
        DocumentModel fakeDoc = new SimpleDocumentModel();

        DocumentModelList groups = userManager.searchGroups(filter,
                fulltext, null);
        assertEquals(4, groups.size());

        groups = userManager.searchGroups(filter, fulltext, fakeDoc);
        assertEquals(2, groups.size());

        filter.put("groupname", "administrators%");
        fulltext.add("groupname");
        groups = userManager.searchGroups(filter, fulltext, fakeDoc);
        assertEquals(1, groups.size());
        assertEquals("administrators-tenanta",
                groups.get(0).getPropertyValue("groupname"));
    }

    @Test
    public void testShouldAddPrefixToIdWhenGroupCreated() throws Exception {

        DocumentModel fakeDoc = new SimpleDocumentModel();

        DocumentModel newGroup = userManager.getBareGroupModel();
        newGroup.setPropertyValue("groupname", "test");
        userManager.createGroup(newGroup, fakeDoc);

        DocumentModel group = userManager.getGroupModel("test", fakeDoc);
        String groupIdValue = (String) group.getPropertyValue("groupname");
        assertTrue(groupIdValue.endsWith("-tenanta"));

    }

}
