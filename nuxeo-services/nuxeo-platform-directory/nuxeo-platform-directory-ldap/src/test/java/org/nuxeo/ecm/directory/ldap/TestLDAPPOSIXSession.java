/*
 * (C) Copyright 2011-2014 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     mhilaire
 */
package org.nuxeo.ecm.directory.ldap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.directory.BaseSession;
import org.nuxeo.ecm.directory.Session;

public class TestLDAPPOSIXSession extends TestLDAPSession {

    @Override
    public List<String> getLdifFiles() {
        List<String> ldifFiles = new ArrayList<String>();
        ldifFiles.add("sample-users-posix.ldif");
        if (POSIXGROUP_IS_STRUCTURAL) {
            ldifFiles.add("sample-structural-posixgroups.ldif");
        } else {
            ldifFiles.add("sample-posixgroups.ldif");
        }
        if (HAS_DYNGROUP_SCHEMA) {
            ldifFiles.add("sample-dynamic-groups.ldif");
        }
        return ldifFiles;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        EXTERNAL_SERVER_SETUP = "TestDirectoriesWithExternalOpenLDAP-POSIX.xml";
        INTERNAL_SERVER_SETUP = "TestDirectoriesWithInternalApacheDS-POSIX.xml";
        super.setUp();
    }

    @Override
    @SuppressWarnings("unchecked")
    @Test
    public void testGetEntry2() throws ClientException {
        Session session = getLDAPDirectory("groupDirectory").getSession();
        try {
            DocumentModel entry = session.getEntry("administrators");
            assertNotNull(entry);
            assertEquals("administrators", entry.getId());
            assertEquals("administrators", entry.getProperty(TestLDAPSession.GROUP_SCHEMANAME, "groupname"));

            if (USE_EXTERNAL_TEST_LDAP_SERVER) {
                // LDAP references do not work with the internal test server
                List<String> members = (List<String>) entry.getProperty(GROUP_SCHEMANAME, "members");
                assertNotNull(members);
                assertEquals(1, members.size());
                assertTrue(members.contains("Administrator"));
            }

            entry = session.getEntry("members");
            assertNotNull(entry);
            assertEquals("members", entry.getId());
            assertEquals("members", entry.getProperty(GROUP_SCHEMANAME, "groupname"));

            if (USE_EXTERNAL_TEST_LDAP_SERVER) {
                // LDAP references do not work with the internal test server
                List<String> members = (List<String>) entry.getProperty(GROUP_SCHEMANAME, "members");
                assertEquals(3, members.size());
                assertTrue(members.contains("Administrator"));
                assertTrue(members.contains("user1"));
            }

            entry = session.getEntry("submembers");
            assertNotNull(entry);
            assertEquals("submembers", entry.getId());
            assertEquals("submembers", entry.getProperty(GROUP_SCHEMANAME, "groupname"));

            if (USE_EXTERNAL_TEST_LDAP_SERVER) {
                // LDAP references do not work with the internal test server
                assertEquals(Arrays.asList("user2"), entry.getProperty(GROUP_SCHEMANAME, "members"));
            }
        } finally {
            session.close();
        }
    }

    @Override
    @Test
    public void testCreateEntry2() throws Exception {
        if (USE_EXTERNAL_TEST_LDAP_SERVER) {
            Session session = getLDAPDirectory("groupDirectory").getSession();
            try {
                assertNotNull(session);
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("groupname", "group2");
                map.put("members", Arrays.asList("user1", "user2"));
                map.put("gidNumber", 9000);
                DocumentModel dm = session.createEntry(map);
                dm = session.getEntry("group2");
                assertNotNull(dm);
                assertEquals(Arrays.asList("user1", "user2"), dm.getProperty(GROUP_SCHEMANAME, "members"));

                map = new HashMap<String, Object>();
                map.put("groupname", "group1");
                map.put("members", Arrays.asList("Administrator"));
                map.put("gidNumber", 9001);
                dm = session.createEntry(map);
                dm = session.getEntry("group1");
                assertNotNull(dm);
                assertEquals(Arrays.asList("Administrator"), dm.getProperty(GROUP_SCHEMANAME, "members"));

                dm = session.getEntry("group2");
                assertNotNull(dm);

                map = new HashMap<String, Object>();
                map.put("groupname", "emptygroup");
                map.put("members", new ArrayList<String>());
                map.put("gidNumber", 9000);
                dm = session.createEntry(map);
                dm = session.getEntry("emptygroup");
                assertNotNull(dm);
                assertEquals("emptygroup", dm.getId());
                assertEquals("emptygroup", dm.getProperty(GROUP_SCHEMANAME, "groupname"));

                assertEquals(Arrays.asList(), dm.getProperty(GROUP_SCHEMANAME, "members"));
            } finally {
                session.close();
            }
        }
    }

    @Override
    @Test
    public void testUpdateEntry() throws Exception {
        if (USE_EXTERNAL_TEST_LDAP_SERVER) {
            Session session = getLDAPDirectory("userDirectory").getSession();
            Session groupSession = getLDAPDirectory("groupDirectory").getSession();
            try {
                DocumentModel entry = session.getEntry("user1");
                assertNotNull(entry);

                // check that this entry is editable:
                assertFalse(BaseSession.isReadOnlyEntry(entry));

                entry.setProperty(USER_SCHEMANAME, "firstName", "toto");
                entry.setProperty(USER_SCHEMANAME, "lastName", "");
                entry.setProperty(USER_SCHEMANAME, "password", "toto");
                entry.setProperty(USER_SCHEMANAME, "intField", Long.valueOf(123));

                // try to tweak the DN read-only field
                entry.setProperty(USER_SCHEMANAME, "dn", "cn=this,ou=is,ou=a,ou=fake,o=dn");

                entry.setProperty(USER_SCHEMANAME, "employeeType", Arrays.asList("item3", "item4"));
                List<String> groups = Arrays.asList("administrators", "members");
                entry.setProperty(USER_SCHEMANAME, "groups", groups);
                session.updateEntry(entry);

                entry = session.getEntry("user1");
                assertNotNull(entry);
                assertEquals("toto", entry.getProperty(USER_SCHEMANAME, "firstName"));
                assertEquals("", entry.getProperty(USER_SCHEMANAME, "lastName"));
                assertEquals(Long.valueOf(123), entry.getProperty(USER_SCHEMANAME, "intField"));
                assertEquals(Arrays.asList("item3", "item4"), entry.getProperty(USER_SCHEMANAME, "employeeType"));
                if (HAS_DYNGROUP_SCHEMA) {
                    assertEquals(Arrays.asList("administrators", "dyngroup1", "dyngroup2", "members"),
                            entry.getProperty(USER_SCHEMANAME, "groups"));
                } else {
                    assertEquals(Arrays.asList("administrators", "members"),
                            entry.getProperty(USER_SCHEMANAME, "groups"));
                }

                // check that the referenced groups where edited properly
                entry = groupSession.getEntry("administrators");
                assertNotNull(entry);
                assertEquals(Arrays.asList("Administrator", "user1"), entry.getProperty(GROUP_SCHEMANAME, "members"));

                entry = groupSession.getEntry("members");
                assertNotNull(entry);
                assertEquals(Arrays.asList("Administrator", "user1", "user2"),
                        entry.getProperty(GROUP_SCHEMANAME, "members"));

            } finally {
                session.close();
                groupSession.close();
            }
            session = getLDAPDirectory("groupDirectory").getSession();
            try {
                DocumentModel entry = session.getEntry("administrators");
                assertNotNull(entry);
                assertEquals(Arrays.asList("Administrator", "user1"), entry.getProperty(GROUP_SCHEMANAME, "members"));
            } finally {
                session.close();
            }
        }
    }

    @Override
    @Test
    public void testUpdateEntry2() throws Exception {
        if (USE_EXTERNAL_TEST_LDAP_SERVER) {
            Session session = getLDAPDirectory("groupDirectory").getSession();
            try {
                DocumentModel entry = session.getEntry("members");
                assertNotNull(entry);

                // check that this entry is editable:
                assertFalse(BaseSession.isReadOnlyEntry(entry));
                assertEquals("cn=members,ou=editable,ou=groups,dc=example,dc=com",
                        entry.getProperty(GROUP_SCHEMANAME, "dn"));

                // edit description and members but not subGroups
                entry.setProperty(GROUP_SCHEMANAME, "description", "blablabla");
                entry.setProperty(GROUP_SCHEMANAME, "members", Arrays.asList("user1", "user2"));
                session.updateEntry(entry);

                entry = session.getEntry("members");
                assertNotNull(entry);
                assertEquals("blablabla", entry.getProperty(GROUP_SCHEMANAME, "description"));
                assertEquals(Arrays.asList("user1", "user2"), entry.getProperty(GROUP_SCHEMANAME, "members"));

                // edit both members and subGroups at the same time
                entry.setProperty(GROUP_SCHEMANAME, "members", Arrays.asList("user1", "user3"));
                session.updateEntry(entry);

                entry = session.getEntry("members");
                assertNotNull(entry);
                assertEquals("blablabla", entry.getProperty(GROUP_SCHEMANAME, "description"));
                assertEquals(Arrays.asList("user1", "user3"), entry.getProperty(GROUP_SCHEMANAME, "members"));
            } finally {
                session.close();
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    @Test
    public void testGetEntry3() throws ClientException {
        if (!HAS_DYNGROUP_SCHEMA) {
            return;
        }
        Session session = getLDAPDirectory("groupDirectory").getSession();
        try {
            DocumentModel entry = session.getEntry("dyngroup1");
            assertNotNull(entry);
            assertEquals("dyngroup1", entry.getId());
            assertEquals("dyngroup1", entry.getProperty(GROUP_SCHEMANAME, "groupname"));

            List<String> members = (List<String>) entry.getProperty(GROUP_SCHEMANAME, "members");
            assertEquals(Arrays.asList("user1", "user3"), members);

            List<String> subGroups = (List<String>) entry.getProperty(GROUP_SCHEMANAME, "subGroups");
            assertEquals(Arrays.asList("subgroup", "submembers", "subsubgroup", "subsubsubgroup"), subGroups);

            List<String> parentGroups = (List<String>) entry.getProperty(GROUP_SCHEMANAME, "parentGroups");
            assertNotNull(parentGroups);
            assertEquals(0, parentGroups.size());

            entry = session.getEntry("dyngroup2");
            assertNotNull(entry);
            assertEquals("dyngroup2", entry.getId());
            assertEquals("dyngroup2", entry.getProperty(GROUP_SCHEMANAME, "groupname"));

            members = (List<String>) entry.getProperty(GROUP_SCHEMANAME, "members");
            assertEquals(Arrays.asList("user1", "user3"), members);
            // user4 is not there since userDirectory is scoped 'onelevel'

            subGroups = (List<String>) entry.getProperty(GROUP_SCHEMANAME, "subGroups");
            assertNotNull(subGroups);
            assertEquals(0, subGroups.size());

            parentGroups = (List<String>) entry.getProperty(GROUP_SCHEMANAME, "parentGroups");
            assertNotNull(parentGroups);
            assertEquals(0, parentGroups.size());

            // test that submembers is a subgroup of dyngroup1 (inverse
            // reference resolution)
            entry = session.getEntry("submembers");
            assertNotNull(entry);
            assertEquals("submembers", entry.getId());
            assertEquals("submembers", entry.getProperty(GROUP_SCHEMANAME, "groupname"));

            members = (List<String>) entry.getProperty(GROUP_SCHEMANAME, "members");
            assertEquals(Arrays.asList("user2"), members);

            subGroups = (List<String>) entry.getProperty(GROUP_SCHEMANAME, "subGroups");
            assertNotNull(subGroups);
            assertEquals(0, subGroups.size());

            parentGroups = (List<String>) entry.getProperty(GROUP_SCHEMANAME, "parentGroups");
            assertEquals(Arrays.asList("dyngroup1"), parentGroups);
        } finally {
            session.close();
        }
    }

    @Override
    @Test
    public void testGetMandatoryAttributes() throws ClientException {
        if (USE_EXTERNAL_TEST_LDAP_SERVER) {
            LDAPSession session = (LDAPSession) getLDAPDirectory("userDirectory").getSession();
            try {
                List<String> mandatoryAttributes = session.getMandatoryAttributes();
                assertEquals(Arrays.asList("sn", "cn"), mandatoryAttributes);
            } finally {
                session.close();
            }
            session = (LDAPSession) getLDAPDirectory("groupDirectory").getSession();
            try {
                List<String> mandatoryAttributes = session.getMandatoryAttributes();
                List<String> expectedAttributes = Arrays.asList("cn", "gidNumber");
                Collections.sort(mandatoryAttributes);
                assertEquals(expectedAttributes, mandatoryAttributes);
            } finally {
                session.close();
            }
        }
    }
}
