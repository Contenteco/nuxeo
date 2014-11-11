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

package org.nuxeo.ecm.platform.filemanager.core.listener.tests;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.storage.sql.SQLRepositoryTestCase;

public class AbstractListener extends SQLRepositoryTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        openSession();
    }

    @Override
    public void tearDown() throws Exception {
        closeSession();
        super.tearDown();
    }

    protected DocumentModel createFileDocument(boolean setMimeType)
            throws ClientException {
        DocumentModel fileDoc = session.createDocumentModel("/",
                "testFile", "File");
        fileDoc.setProperty("dublincore", "title", "TestFile");

        Blob blob = new StringBlob("SOMEDUMMYDATA");
        blob.setFilename("test.pdf");
        if (setMimeType) {
            blob.setMimeType("application/pdf");
        }
        fileDoc.setProperty("file", "content", blob);

        fileDoc = session.createDocument(fileDoc);

        session.saveDocument(fileDoc);
        session.save();

        return fileDoc;
    }

}
