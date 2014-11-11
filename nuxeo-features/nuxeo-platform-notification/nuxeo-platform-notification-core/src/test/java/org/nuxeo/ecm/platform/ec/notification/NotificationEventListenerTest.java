/*
 * (C) Copyright 2006-2009 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 * $Id$
 */

package org.nuxeo.ecm.platform.ec.notification;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.storage.sql.SQLRepositoryTestCase;
import org.nuxeo.ecm.platform.ec.notification.service.NotificationService;
import org.nuxeo.runtime.api.Framework;

/**
 * Simple test Case for DocUIDGeneratorListener
 *
 * @author Julien Thimonier <jt@nuxeo.com>
 */
// FIXME: this test does not test anything (...)
public class NotificationEventListenerTest extends SQLRepositoryTestCase {

    private static final Log log = LogFactory.getLog(NotificationService.class);

    private final EmailHelperMock emailHelperMock = new EmailHelperMock();

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        openSession();
        deployBundle("org.nuxeo.ecm.core.persistence");
        deployBundle("org.nuxeo.ecm.platform.placeful.api");
        deployBundle("org.nuxeo.ecm.platform.placeful.core");
        deployBundle("org.nuxeo.ecm.platform.notification.core");
        deployBundle("org.nuxeo.ecm.platform.notification.api");
        deployBundle("org.nuxeo.ecm.core.event");
        deployBundle("org.nuxeo.ecm.platform.usermanager");
        deployBundle("org.nuxeo.ecm.directory");
        deployBundle("org.nuxeo.ecm.platform.types.api");
        deployBundle("org.nuxeo.ecm.platform.types.core");
        deployBundle("org.nuxeo.ecm.platform.url.api");
        deployBundle("org.nuxeo.ecm.platform.url.core");

        deployBundle("org.nuxeo.ecm.platform.notification.core.tests");

        // Injection of the EmailHelper Mock to track mails sending
        EventService eventService = Framework.getService(EventService.class);
        List<PostCommitEventListener> listeners = eventService.getPostCommitEventListeners();

        for (PostCommitEventListener postCommitEventListener : listeners) {
            if (postCommitEventListener.getClass().equals(
                    NotificationEventListener.class)) {
                ((NotificationEventListener) postCommitEventListener).setEmailHelper(emailHelperMock);
            }
        }
        log.info("setup Finnished");
    }

    @Override
    @After
    public void tearDown() throws Exception {
        closeSession();
        super.tearDown();
    }

    protected DocumentModel createNoteDocument() throws ClientException {
        DocumentModel folder = session.createDocumentModel("/",
                "test", "Folder");

        folder = session.createDocument(folder);
        session.saveDocument(folder);

        DocumentModel noteDoc = session.createDocumentModel("/test/",
                "testFile", "Note");

        noteDoc.setProperty("dublincore", "title", "TestFile");
        noteDoc.setProperty("dublincore", "description", "RAS");

        noteDoc = session.createDocument(noteDoc);

        session.saveDocument(noteDoc);
        session.save();

        return noteDoc;
    }

    protected void waitForAsyncExec() {
        Framework.getLocalService(EventService.class).waitForAsyncCompletion();
    }

    @Test
    public void testListener() throws ClientException {
        // EventService eventService =
        // Framework.getLocalService(EventService.class);
        // PlacefulServiceImpl placefulServiceImpl = (PlacefulServiceImpl)
        // Framework.getLocalService(PlacefulService.class);
        // DocumentModel noteDoc = createNoteDocument();
        // // Record notification
        // UserSubscription userSubscription = new UserSubscription(
        // "Workflow Change", "user:"
        // + session.getPrincipal().getName(),
        // noteDoc.getId());
        // placefulServiceImpl.setAnnotation(userSubscription);
        //
        // // Trigger notification
        // DocumentEventContext ctx = new
        // DocumentEventContext(session,
        // session.getPrincipal(), noteDoc);
        // ctx.setProperty("recipients", new Object[] { "jt@nuxeo.com" });
        // ctx.getProperties().put("comment", "RAS");
        // eventService.fireEvent(ctx.newEvent("workflowAbandoned"));
        // session.save();
        // waitForAsyncExec();
        // // Check that at least one email has been sending
        // assertTrue(emailHelperMock.getCompteur() > 0);
    }

}
