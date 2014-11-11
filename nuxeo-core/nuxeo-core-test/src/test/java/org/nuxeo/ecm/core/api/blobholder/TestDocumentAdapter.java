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

package org.nuxeo.ecm.core.api.blobholder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestDocumentAdapter {

    @Inject
    protected CoreSession session;

    @Test
    public void testFileAdapters() throws Exception {
        DocumentModel file = session.createDocumentModel("File");
        file.setPathInfo("/", "TestFile");

        Blob blob = new StringBlob("BlobContent");
        blob.setFilename("TestFile.txt");
        blob.setMimeType("text/plain");
        file.setProperty("dublincore", "title", "TestFile");
        file.setProperty("file", "content", blob);
        file.setProperty("file", "filename", "TestFile-fn.txt");
        file = session.createDocument(file);
        session.save();

        BlobHolder bh = file.getAdapter(BlobHolder.class);
        assertNotNull(bh);
        assertTrue(bh instanceof DocumentBlobHolder);
        assertEquals("/TestFile/TestFile.txt", bh.getFilePath());

        // test blob content
        Blob b = bh.getBlob();
        assertEquals("TestFile.txt", b.getFilename());
        assertEquals("text/plain", b.getMimeType());
        assertEquals("BlobContent", b.getString());

        // test write
        blob = new StringBlob("OtherContent");
        blob.setFilename("other.txt");
        blob.setMimeType("text/html");
        bh.setBlob(blob);
        session.saveDocument(file);
        session.save();

        // reread
        assertEquals("/TestFile/other.txt", bh.getFilePath());
        b = bh.getBlob();
        assertEquals("other.txt", b.getFilename());
        assertEquals("text/html", b.getMimeType());
        assertEquals("OtherContent", b.getString());
        // check filename property updated as well
        assertEquals("other.txt", file.getPropertyValue("file:filename"));

        // test set null blob
        bh.setBlob(null);
        session.saveDocument(file);
        session.save();
        b = bh.getBlob();
        assertNull(b);
        assertNull(file.getPropertyValue("file:filename"));
    }

    @Test
    public void testNoteAdapters() throws Exception {
        DocumentModel note = session.createDocumentModel("Note");
        note.setPathInfo("/", "TestNote");

        note.setProperty("dublincore", "title", "Title");
        note.setProperty("note", "note", "Text of the note");
        note = session.createDocument(note);

        BlobHolder bh = note.getAdapter(BlobHolder.class);
        assertNotNull(bh);
        assertTrue(bh instanceof DocumentStringBlobHolder);

        // test blob content
        Blob b = bh.getBlob();
        assertEquals("Title.txt", b.getFilename());
        assertEquals("text/plain", b.getMimeType());
        assertEquals("Text of the note", b.getString());

        // test write
        StringBlob blob = new StringBlob("Other text for note");
        blob.setFilename("other.txt");
        blob.setMimeType("text/html");
        bh.setBlob(blob);
        session.saveDocument(note);
        session.save();

        // reread
        assertEquals("/TestNote/Title.html", bh.getFilePath());
        b = bh.getBlob();
        assertEquals("Title.html", b.getFilename());
        assertEquals("text/html", b.getMimeType());
        assertEquals("Other text for note", b.getString());

        // test set null blob
        bh.setBlob(null);
        session.saveDocument(note);
        session.save();
        b = bh.getBlob();
        assertNull(b);
    }

    @Test
    public void testFolderAdapters() throws Exception {
        DocumentModel folder = session.createDocumentModel("Folder");
        folder.setPathInfo("/", "TestFolder");

        folder.setProperty("dublincore", "title", "TestFolder");

        folder = session.createDocument(folder);

        BlobHolder bh = folder.getAdapter(BlobHolder.class);
        assertNull(bh);
    }

    @Test
    public void testMultiFileAdapters() throws Exception {
        DocumentModel file = session.createDocumentModel("File");
        file.setPathInfo("/", "TestDoc");

        Blob blob = new StringBlob("BlobContent");
        blob.setFilename("TestFile.txt");
        blob.setMimeType("text/plain");
        file.setProperty("dublincore", "title", "TestDoc");
        file.setProperty("file", "content", blob);
        file.setProperty("file", "filename", "TestFile-fn.txt");

        List<Map<String, Serializable>> blobs = new ArrayList<Map<String, Serializable>>();
        for (int i = 1; i <= 5; i++) {
            String name = "TestFile" + i + ".txt";
            Blob nblob = new StringBlob("BlobContent" + i);
            nblob.setFilename(name);
            nblob.setMimeType("text/plain");

            Map<String, Serializable> filesEntry = new HashMap<String, Serializable>();
            filesEntry.put("file", (Serializable) nblob);
            filesEntry.put("filename", name);
            blobs.add(filesEntry);
        }
        file.setPropertyValue("files:files", (Serializable) blobs);

        file = session.createDocument(file);
        session.save();

        BlobHolder bh = file.getAdapter(BlobHolder.class);
        assertNotNull(bh);
        assertTrue(bh instanceof DocumentBlobHolder);
        assertEquals("/TestDoc/TestFile.txt", bh.getFilePath());

        // test blob content
        Blob b = bh.getBlob();
        assertEquals("TestFile.txt", b.getFilename());
        assertEquals("text/plain", b.getMimeType());
        assertEquals("BlobContent", b.getString());

        // test blobs and ordering
        List<Blob> extractedBlobs = bh.getBlobs();
        assertEquals(6, extractedBlobs.size());

        assertEquals("TestFile.txt", extractedBlobs.get(0).getFilename());
        for (int i = 1; i <= 5; i++) {
            assertEquals("TestFile" + i + ".txt",
                    extractedBlobs.get(i).getFilename());
        }

    }

}
