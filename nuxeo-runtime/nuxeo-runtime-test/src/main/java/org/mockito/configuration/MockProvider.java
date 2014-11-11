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
package org.mockito.configuration;

import java.util.HashMap;
import java.util.Map;

import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.api.ServiceProvider;

/**
 *
 *
 * @since 5.7.8
 */
public class MockProvider implements ServiceProvider {

    public static MockProvider INSTANCE = MockProvider.install();

    private static Map<Class<?>, Object> mocks = new HashMap<>();

    public void bind(Class<?> klass, Object mock) {
        mocks.put(klass, mock);
    }

    public void clearBindings() {
        mocks.clear();
    }


    private MockProvider() {

    }

    private static MockProvider install() {
        return new MockProvider();
    }

    @Override
    public <T> T getService(Class<T> serviceClass) {
        if (mocks.containsKey(serviceClass)) {
            return (T) mocks.get(serviceClass);
        }
        return Framework.getRuntime().getService(serviceClass);
    }

}
