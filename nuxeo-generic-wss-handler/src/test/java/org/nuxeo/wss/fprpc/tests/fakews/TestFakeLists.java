/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 */

package org.nuxeo.wss.fprpc.tests.fakews;

import javax.servlet.Filter;

import org.nuxeo.wss.fprpc.tests.fake.FakeRequest;
import org.nuxeo.wss.fprpc.tests.fake.FakeRequestBuilder;
import org.nuxeo.wss.fprpc.tests.fake.FakeResponse;
import org.nuxeo.wss.servlet.WSSFilter;

import junit.framework.TestCase;

public class TestFakeLists extends TestCase {


     public void testHandling() throws Exception {

            Filter filter=new WSSFilter();
            filter.init(null);

            FakeRequest request = FakeRequestBuilder.buildFromResource("WebUrlFromPageUrl.dump");
            FakeResponse response = new FakeResponse();

            filter.doFilter(request, response, null);

            String result= response.getOutput();

            //System.out.println(result);

            String[] lines = result.split("\n");

            assertEquals("<WebUrlFromPageUrlResult>http://localhost/</WebUrlFromPageUrlResult>", lines[4].trim());

        }

}
