/*
 * (C) Copyright 2006-2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     bstefanescu
 */
package org.nuxeo.ecm.automation.jaxrs;

import java.io.IOException;
import java.io.OutputStream;

import org.nuxeo.ecm.automation.io.services.codec.ObjectCodecService;
import org.nuxeo.runtime.api.Framework;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
public class DefaultJsonAdapter implements JsonAdapter {

    protected Object object;

    public DefaultJsonAdapter(Object object) {
        this.object = object;
    }

    @Override
    public void toJSON(OutputStream out) throws IOException {
        ObjectCodecService service = Framework.getLocalService(ObjectCodecService.class);
        service.write(out, object);
    }

}
