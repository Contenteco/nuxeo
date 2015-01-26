/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     <a href="mailto:grenard@nuxeo.com">Guillaume Renard</a>
 */

/**
 * @since 6.0
 */
package org.nuxeo.ecm.webapp.filemanager;

import java.io.File;
import java.io.Serializable;

import org.nuxeo.ecm.core.api.Blob;

public class NxUploadedFile implements Serializable {

    private static final long serialVersionUID = 1L;

    protected final Blob blob;

    public NxUploadedFile(Blob blob) {
        this.blob = blob;
    }

    public Blob getBlob() {
        return blob;
    }

    public String getContentType() {
        return blob.getMimeType();
    }

    public File getFile() {
        return blob.getFile();
    }

    public String getName() {
        return blob.getFilename();
    }

}
