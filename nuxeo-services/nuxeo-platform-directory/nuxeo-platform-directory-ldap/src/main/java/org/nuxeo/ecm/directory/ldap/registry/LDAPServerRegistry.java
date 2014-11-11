/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and contributors.
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

package org.nuxeo.ecm.directory.ldap.registry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.directory.ldap.LDAPServerDescriptor;
import org.nuxeo.runtime.model.SimpleContributionRegistry;

/**
 * Registry for LDAP servers
 *
 * @since 5.6
 */
public class LDAPServerRegistry extends
        SimpleContributionRegistry<LDAPServerDescriptor> {

    public static final Log log = LogFactory.getLog(LDAPServerRegistry.class);

    @Override
    public String getContributionId(LDAPServerDescriptor contrib) {
        return contrib.getName();
    }

    @Override
    public void contributionUpdated(String id, LDAPServerDescriptor contrib,
            LDAPServerDescriptor newOrigContrib) {
        super.contributionUpdated(id, contrib, newOrigContrib);
        log.info("server registered: " + contrib.getName());
    }

    @Override
    public void contributionRemoved(String id, LDAPServerDescriptor origContrib) {
        super.contributionRemoved(id, origContrib);
        log.info("server unregistered: " + origContrib.getName());
    }

    // API

    public LDAPServerDescriptor getServer(String name) {
        return getCurrentContribution(name);
    }

}
