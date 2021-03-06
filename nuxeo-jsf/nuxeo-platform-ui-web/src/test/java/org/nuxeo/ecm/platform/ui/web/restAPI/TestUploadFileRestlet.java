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
 *     Sun Seng David TAN (a.k.a. sunix) <stan@nuxeo.com>
 */
package org.nuxeo.ecm.platform.ui.web.restAPI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.TransactionalFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.ui.web.tag.fn.LiveEditConstants;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * Unit testing the save operation of upload file restlet
 *
 * @author Sun Seng David TAN (a.k.a. sunix) <stan@nuxeo.com>
 */
@RunWith(FeaturesRunner.class)
@Features({ TransactionalFeature.class, CoreFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.platform.ui")
public class TestUploadFileRestlet implements LiveEditConstants {

    @Inject
    protected CoreSession session;

    /**
     * Unit test of the upload file restlet.
     */
    @SuppressWarnings("serial")
    @Test
    public void testUploadRestletSave() throws Exception {
        // create a empty File document
        DocumentModel doc = session.createDocumentModel("/", "myFile", "File");
        doc = session.createDocument(doc);
        session.save();
        doc = session.getDocument(doc.getRef());
        assertNull("At the begining the file document shouldn't have any content", doc.getProperty("file", "content"));

        // saving the current version to be compared later
        Long major = (Long) doc.getPropertyValue("uid:major_version");
        Long minor = (Long) doc.getPropertyValue("uid:minor_version");

        // call the save of uploadfile restlet
        UploadFileRestlet restlet = new UploadFileRestlet() {
            @Override
            protected CoreSession getDocumentManager() {
                return session;
            }
        };
        String expectedFileName = "myfile.txt";
        String blobPropertyName = DEFAULT_SCHEMA + ":" + DEFAULT_BLOB_FIELD;
        String filenamePropertyName = DEFAULT_SCHEMA + ":" + DEFAULT_FILENAME_FIELD;
        restlet.saveFileToDocument(expectedFileName, doc, blobPropertyName, filenamePropertyName,
                new ByteArrayInputStream("Content of the file.".getBytes("UTF-8")));

        // check that the upload has been done correctly
        doc = session.getDocument(doc.getRef());
        assertEquals("The file name is", expectedFileName, doc.getProperty(DEFAULT_SCHEMA, DEFAULT_FILENAME_FIELD));
        assertEquals("The content of the file is", "Content of the file.",
                ((Blob) doc.getProperty(DEFAULT_SCHEMA, DEFAULT_BLOB_FIELD)).getString());

        // checking that no version has been created
        List<DocumentModel> versions = session.getVersions(doc.getRef());
        assertTrue("Should have at least one version snapshotted", versions == null || versions.size() <= 0);
        doc = session.getDocument(doc.getRef());
        assertEquals("The major version shouldn't have been incremented", major,
                doc.getPropertyValue("uid:major_version"));
        assertEquals("The minor version shoudln't have been incremented", minor,
                doc.getPropertyValue("uid:minor_version"));

    }

    /**
     * Unit testing autoversioning of the upload file restlet: minor increment
     */
    @SuppressWarnings("serial")
    @Test
    public void testUploadRestletSaveWithAutoIncr() throws Exception {
        // mock property setting
        Framework.getProperties().setProperty("org.nuxeo.ecm.platform.liveedit.autoversioning", "minor");

        // create a empty File document
        DocumentModel doc = session.createDocumentModel("/", "myFile", "File");
        doc = session.createDocument(doc);
        session.save();
        doc = session.getDocument(doc.getRef());
        assertNull("At the begining the file document shouldn't have any content", doc.getProperty("file", "content"));

        // saving the current version to be compared later
        Long major = (Long) doc.getPropertyValue("uid:major_version");
        Long minor = (Long) doc.getPropertyValue("uid:minor_version");

        // call the save of uploadfile restlet
        UploadFileRestlet restlet = new UploadFileRestlet() {
            @Override
            protected CoreSession getDocumentManager() {
                return session;
            }
        };
        String expectedFileName = "myfile.txt";
        String blobPropertyName = DEFAULT_SCHEMA + ":" + DEFAULT_BLOB_FIELD;
        String filenamePropertyName = DEFAULT_SCHEMA + ":" + DEFAULT_FILENAME_FIELD;
        restlet.saveFileToDocument(expectedFileName, doc, blobPropertyName, filenamePropertyName,
                new ByteArrayInputStream("Content of the file.".getBytes("UTF-8")));

        // check that the upload has been done correctly
        doc = session.getDocument(doc.getRef());
        assertEquals("The file name is", expectedFileName, doc.getProperty(DEFAULT_SCHEMA, DEFAULT_FILENAME_FIELD));
        assertEquals("The content of the file is", "Content of the file.",
                ((Blob) doc.getProperty(DEFAULT_SCHEMA, DEFAULT_BLOB_FIELD)).getString());

        // checking that version has been created and incremented
        List<DocumentModel> versions = session.getVersions(doc.getRef());
        assertTrue("Should have at least one version snapshotted", versions != null && versions.size() == 1);
        doc = session.getDocument(doc.getRef());
        assertEquals("The major version shouldn't have been incremented", major,
                doc.getPropertyValue("uid:major_version"));
        assertEquals("The minor version should have been incremented", new Long(minor + 1),
                doc.getPropertyValue("uid:minor_version"));

    }

}
