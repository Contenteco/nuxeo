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
 * $Id$
 */
package org.nuxeo.ecm.platform.picture.api;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

import org.nuxeo.ecm.core.api.Blob;

/**
 * @author Max Stepanov
 *
 */
public interface ImagingService {

    /**
     * crop image
     *
     * @param in
     * @param x
     * @param y
     * @param width
     * @param height
     * @return resized image file created in temporary folder
     */
    InputStream crop(InputStream in, int x, int y, int width, int height);

    /**
     * Resize image
     *
     * @param in
     * @param format
     * @param width
     * @param height
     * @return resized image file created in temporary folder
     */
    InputStream resize(InputStream in, String format, int width, int height);

    /**
     * Rotate image
     *
     * @param in
     * @param angle
     * @return
     */
    InputStream rotate(InputStream in, int angle);

    /**
     * Retrieve metadata from an image
     *
     * @param in
     * @return metadata
     */
    @Deprecated
    Map<String, Object> getImageMetadata(InputStream in);
    @Deprecated
    Map<String, Object> getImageMetadata(File file);
    Map<String, Object> getImageMetadata(Blob blob);

    /**
     * return file mime-type
     *
     * @param file
     * @return
     */
    String getImageMimeType(File file);
    String getImageMimeType(InputStream in);
    
    /**
     * Retrieves the <b>ImageInfo</b> of the blob that is received as parameter.
     * The information provided by the <b>ImageInfo</b>, like width, height or
     * format, is obtained using ImageMagick(see
     * http://www.imagemagick.org/script/index.php for more details on
     * ImageMagick)
     * 
     * @param blob - the blob of a picture
     * @return - the <b>ImageInfo</b> of a blob
     */
    ImageInfo getImageInfo(Blob blob); 
    
    /**
     * Returns a map with the configurations that were registered for the
     * ImagingService.
     * 
     * @return
     */
    Map<String, String> getConfigurations();

    /**
     * Returns the value a configuration which name is received as parameter.
     * 
     * @param configurationName - the name of the configuration
     * @return the value of the configuration, which can be null in case no
     *         configuration with the specified name was registered
     */
    String getConfigurationValue(String configurationName);

    /**
     * Returns the value a configuration which name is received as parameter. In
     * case no configuration with the specified name was registered, the
     * received <b>defaultValue</b> parameter will be return.
     * 
     * @param configurationName - the name of the configuration
     * @param defaultValue the value of the configuration
     * @return
     */
    String getConfigurationValue(String configurationName, String defaultValue);

    /**
     * Sets the value of a configuration which could be used by the
     * ImagingService.
     * 
     * @param configurationName - the name of the configuration
     * @param configurationValue - the value of the configuration
     */
    void setConfigurationValue(String configurationName,
            String configurationValue);
}
