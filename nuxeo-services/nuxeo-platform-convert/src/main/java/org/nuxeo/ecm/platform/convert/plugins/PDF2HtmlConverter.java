/*
 * (C) Copyright 2002-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 */

package org.nuxeo.ecm.platform.convert.plugins;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.cache.SimpleCachableBlobHolder;
import org.nuxeo.ecm.platform.commandline.executor.api.CmdParameters;

/**
 * Pdf2Html converter based on the pdftohtml command-line executable.
 *
 * @author tiry
 * @author Vincent Dutat
 */
public class PDF2HtmlConverter extends CommandLineBasedConverter {

    @Override
    protected BlobHolder buildResult(List<String> cmdOutput, CmdParameters cmdParams) {
        String outputPath = cmdParams.getParameters().get("outDirPath");
        File outputDir = new File(outputPath);
        File[] files = outputDir.listFiles();
        List<Blob> blobs = new ArrayList<Blob>();

        for (File file : files) {
            Blob blob;
            try {
                blob = Blobs.createBlob(file);
            } catch (IOException e) {
                throw new ConversionException("Cannot create Blob", e);
            }
            blob.setFilename(file.getName());

            if (file.getName().equalsIgnoreCase("index.html")) {
                blobs.add(0, blob);
            } else {
                blobs.add(blob);
            }
        }
        return new SimpleCachableBlobHolder(blobs);
    }

    @Override
    protected Map<String, Blob> getCmdBlobParameters(BlobHolder blobHolder, Map<String, Serializable> parameters)
            throws ConversionException {

        Map<String, Blob> cmdBlobParams = new HashMap<String, Blob>();
        try {
            cmdBlobParams.put("inFilePath", blobHolder.getBlob());
        } catch (ClientException e) {
            throw new ConversionException("Unable to get Blob for holder", e);
        }
        return cmdBlobParams;
    }

    @Override
    protected Map<String, String> getCmdStringParameters(BlobHolder blobHolder, Map<String, Serializable> parameters)
            throws ConversionException {

        Map<String, String> cmdStringParams = new HashMap<String, String>();

        String baseDir = getTmpDirectory(parameters);
        Path tmpPath = new Path(baseDir).append("pdf2html_" + System.currentTimeMillis());

        File outDir = new File(tmpPath.toString());
        boolean dirCreated = outDir.mkdir();
        if (!dirCreated) {
            throw new ConversionException("Unable to create tmp dir for transformer output");
        }
        cmdStringParams.put("outDirPath", outDir.getAbsolutePath());
        return cmdStringParams;
    }

}
