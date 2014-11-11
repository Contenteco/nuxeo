/*
 * (C) Copyright 2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 * $Id: $
 */
package org.nuxeo.ecm.platform.scheduler.core.service;

import java.text.ParseException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.platform.scheduler.core.EventJob;
import org.nuxeo.ecm.platform.scheduler.core.interfaces.Schedule;
import org.nuxeo.ecm.platform.scheduler.core.interfaces.SchedulerRegistry;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Extension;
import org.nuxeo.runtime.model.RuntimeContext;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

/**
 * Schedule registry service.
 *
 * @author <a href="mailto:fg@nuxeo.com">Florent Guillaume</a>
 */
public class SchedulerRegistryService extends DefaultComponent implements
        SchedulerRegistry {

    public static final ComponentName NAME = new ComponentName(
            "org.nuxeo.ecm.platform.scheduler.core.service.SchedulerService");

    private static final Log log = LogFactory.getLog(SchedulerRegistryService.class);

    private RuntimeContext bundle;

    private Scheduler scheduler;

    @Override
    public void activate(ComponentContext context) throws Exception {
        log.debug("Activate");
        bundle = context.getRuntimeContext();

        // Find a scheduler
        SchedulerFactory schedulerFactory = new StdSchedulerFactory();
        scheduler = schedulerFactory.getScheduler();
        scheduler.start();
        // server = MBeanServerFactory.createMBeanServer();
        // server.createMBean("org.quartz.ee.jmx.jboss.QuartzService",
        // quartzObjectName);
    }

    @Override
    public void deactivate(ComponentContext context) throws Exception {
        log.debug("Deactivate");
        scheduler.shutdown();
    }

    @Override
    public void registerExtension(Extension extension) {
        Object[] contribs = extension.getContributions();
        for (Object contrib : contribs) {
            Schedule schedule = (Schedule) contrib;
            registerSchedule(schedule);
        }
    }

    @Override
    public void unregisterExtension(Extension extension) {
        Object[] contribs = extension.getContributions();
        for (Object contrib : contribs) {
            Schedule schedule = (Schedule) contrib;
            unregisterSchedule(schedule);
        }
    }

    public RuntimeContext getContext() {
        return bundle;
    }

    public void registerSchedule(Schedule schedule) {
        log.info("Registering " + schedule);
        JobDetail job = new JobDetail(schedule.getId(), "nuxeo", EventJob.class);
        JobDataMap map = job.getJobDataMap();
        map.put("eventId", schedule.getEventId());
        map.put("eventCategory", schedule.getEventCategory());
        map.put("username", schedule.getUsername());
        map.put("password", schedule.getPassword());

        Trigger trigger;
        try {
            trigger = new CronTrigger(schedule.getId(), "nuxeo",
                    schedule.getCronExpression());
        } catch (ParseException e) {
            log.error(String.format(
                    "invalid cron expresion '%s' for schedule '%s'",
                    schedule.getCronExpression(), schedule.getId()), e);
            return;
        }
        // This is useful when testing to avoid multiple threads:
        // trigger = new SimpleTrigger(schedule.getId(), "nuxeo");

        try {
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            log.error(String.format(
                    "failed to schedule job with id '%s': %s",
                    schedule.getId(), e.getMessage()), e);
        }
    }

    public void unregisterSchedule(Schedule schedule) {
        log.info("Unregistering " + schedule);
        try {
            scheduler.deleteJob(schedule.getId(), "nuxeo");
        } catch (SchedulerException e) {
            log.error(String.format(
                    "failed to unschedule job with '%s': %s",
                    schedule.getId(), e.getMessage()), e);
        }
    }

}
