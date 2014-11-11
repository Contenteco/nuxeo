/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     dmetzler
 */
package org.nuxeo.ecm.automation.server;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XObject;

/**
 *
 *
 * @since 5.8
 */
@XObject("marshaller")
public class MarshallerDescriptor {

    @XNodeList(value = "writer", componentType = Class.class, type = ArrayList.class)
    private List<Class<? extends MessageBodyWriter<?>>> writers;

    @XNodeList(value = "reader", componentType = Class.class, type = ArrayList.class)
    private List<Class<? extends MessageBodyReader<?>>> readers;

    public List<Class<? extends MessageBodyWriter<?>>> getWriters() {
        return writers;
    }

    public List<Class<? extends MessageBodyReader<?>>> getReaders() {
        return readers;
    }
}
