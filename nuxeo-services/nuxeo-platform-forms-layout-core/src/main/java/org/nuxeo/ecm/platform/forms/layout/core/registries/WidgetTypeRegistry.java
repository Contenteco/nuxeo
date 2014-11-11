/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.platform.forms.layout.core.registries;

import org.nuxeo.ecm.platform.forms.layout.api.WidgetType;
import org.nuxeo.runtime.model.SimpleContributionRegistry;

/**
 * Registry holding widget type instances for a given category.
 *
 * @since 5.5
 */
public class WidgetTypeRegistry extends SimpleContributionRegistry<WidgetType> {

    protected final String category;

    public WidgetTypeRegistry(String category) {
        super();
        this.category = category;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public String getContributionId(WidgetType contrib) {
        return contrib.getName();
    }

    public WidgetType getWidgetType(String id) {
        return getCurrentContribution(id);
    }

}
