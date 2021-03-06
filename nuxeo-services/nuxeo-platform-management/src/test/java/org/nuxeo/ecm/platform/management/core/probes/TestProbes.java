/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     matic
 */
package org.nuxeo.ecm.platform.management.core.probes;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.management.api.ProbeInfo;
import org.nuxeo.ecm.core.management.api.ProbeManager;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.TransactionalFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.management.statuses.ProbeScheduler;
import org.nuxeo.runtime.management.ResourcePublisher;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @author Stephane Lacoin (Nuxeo EP Software Engineer)
 */
@RunWith(FeaturesRunner.class)
@Features({ TransactionalFeature.class, CoreFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.runtime.management", //
        "org.nuxeo.ecm.core.management", //
        "org.nuxeo.ecm.platform.management", //
})
public class TestProbes {

    @Inject
    protected ProbeScheduler scheduler;

    @Inject
    protected ResourcePublisher publisher;

    @Inject
    protected ProbeManager runner;

    @Test
    public void testScheduling() throws MalformedObjectNameException {
        assertFalse(scheduler.isEnabled());

        scheduler.enable();
        assertTrue(scheduler.isEnabled());

        scheduler.disable();
        assertFalse(scheduler.isEnabled());

        assertTrue(publisher.getResourcesName().contains(new ObjectName("org.nuxeo:name=probeScheduler,type=service")));
    }

    @Test
    public void testPopulateRepository() throws Exception {
        ProbeInfo info = runner.getProbeInfo("populateRepository");
        assertNotNull(info);
        info = runner.runProbe(info);
        assertFalse(info.isInError());
        String result = info.getStatus().getAsString();
        System.out.print("populateRepository Probe result : " + result);
    }

    @Test
    public void testQueryRepository() throws Exception {
        ProbeInfo info = runner.getProbeInfo("queryRepository");
        assertNotNull(info);
        info = runner.runProbe(info);
        assertFalse(info.isInError());
        System.out.print(info.getStatus().getAsString());
    }

}
