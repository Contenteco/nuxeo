/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 * $Id: TestLayoutComponent.java 28493 2008-01-04 19:51:30Z sfermigier $
 */

package org.nuxeo.ecm.platform.layout.core.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.platform.forms.layout.api.BuiltinModes;
import org.nuxeo.ecm.platform.forms.layout.api.FieldDefinition;
import org.nuxeo.ecm.platform.forms.layout.api.LayoutDefinition;
import org.nuxeo.ecm.platform.forms.layout.api.LayoutRowDefinition;
import org.nuxeo.ecm.platform.forms.layout.api.WidgetDefinition;
import org.nuxeo.ecm.platform.forms.layout.api.service.LayoutStore;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.NXRuntimeTestCase;

/**
 * Test layout component extension points.
 *
 * @author <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 */
public class TestLayoutStoreComponent extends NXRuntimeTestCase {

    private LayoutStore service;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        deployBundle("org.nuxeo.ecm.platform.forms.layout.core");
        deployContrib("org.nuxeo.ecm.platform.forms.layout.core.tests",
                "layouts-core-test-contrib.xml");
        service = Framework.getService(LayoutStore.class);
        assertNotNull(service);
    }

    @Test
    public void testLayoutRegistration() {
        assertNull(service.getLayoutDefinition("fooCategory", "dublincore"));

        LayoutDefinition dublincore = service.getLayoutDefinition(
                "testCategory", "dublincore");
        assertNotNull(dublincore);
        assertEquals("dublincore", dublincore.getName());

        // test templates
        assertEquals("default_template",
                dublincore.getTemplate(BuiltinModes.ANY));
        assertEquals("view_template", dublincore.getTemplate(BuiltinModes.VIEW));
        assertEquals("edit_template", dublincore.getTemplate(BuiltinModes.EDIT));
        assertEquals("create_template",
                dublincore.getTemplate(BuiltinModes.CREATE));
        assertEquals("default_template", dublincore.getTemplate("lalal"));

        // test rows
        LayoutRowDefinition[] rows = dublincore.getRows();
        assertEquals(3, rows.length);
        assertEquals(1, rows[0].getWidgets().length);
        assertEquals("title", rows[0].getWidgets()[0]);
        assertEquals(1, rows[1].getWidgets().length);
        assertEquals("description", rows[1].getWidgets()[0]);
        assertEquals(3, rows[2].getWidgets().length);
        assertEquals("creationDate", rows[2].getWidgets()[0]);
        assertEquals("", rows[2].getWidgets()[1]);
        assertEquals("modificationDate", rows[2].getWidgets()[2]);
        assertEquals(3, dublincore.getColumns());

        // test widgets
        WidgetDefinition title = dublincore.getWidgetDefinition("title");
        assertNotNull(title);
        assertEquals("title", title.getName());
        assertEquals("text", title.getType());
        assertEquals("label.dublincore.title", title.getLabel(BuiltinModes.ANY));
        assertEquals("", title.getLabel(BuiltinModes.EDIT));
        assertEquals("help text", title.getHelpLabel(BuiltinModes.EDIT));
        assertTrue(title.isTranslated());
        assertTrue(title.isHandlingLabels());
        FieldDefinition[] fieldDefs = title.getFieldDefinitions();
        assertEquals(1, fieldDefs.length);
        assertNull(fieldDefs[0].getSchemaName());
        assertEquals("dc:title", fieldDefs[0].getFieldName());
        // props
        Map<String, Serializable> anyProps = title.getProperties(
                BuiltinModes.ANY, BuiltinModes.ANY);
        assertEquals(2, anyProps.size());
        assertEquals("styleClass", anyProps.get("styleClass"));
        assertEquals("#{!currentUser.administrator}", anyProps.get("required"));
        Map<String, Serializable> editProps = title.getProperties(
                BuiltinModes.EDIT, BuiltinModes.VIEW);
        assertEquals(3, editProps.size());
        assertEquals("styleClass", editProps.get("styleClass"));
        assertEquals("#{!currentUser.administrator}", editProps.get("required"));
        assertEquals("false", editProps.get("rendered"));

        WidgetDefinition subjects = dublincore.getWidgetDefinition("subjects");
        assertNotNull(subjects);
        assertEquals("subjects", subjects.getName());
        assertEquals("list", subjects.getType());
        assertEquals("list", subjects.getType());
        assertEquals("view", subjects.getMode(BuiltinModes.ANY));
        fieldDefs = subjects.getFieldDefinitions();
        assertEquals(1, fieldDefs.length);
        assertEquals("dublincore", fieldDefs[0].getSchemaName());
        assertEquals("subjects", fieldDefs[0].getFieldName());
        anyProps = subjects.getProperties(BuiltinModes.ANY, BuiltinModes.VIEW);
        assertEquals(3, anyProps.size());
        assertEquals("bar", anyProps.get("foo"));
        assertEquals("subject", anyProps.get("directory"));
        assertTrue(anyProps.get("list") instanceof String[]);
        String[] list = (String[]) anyProps.get("list");
        assertEquals(2, list.length);
        assertEquals("fooListItem", list[0]);
        assertEquals("barListItem", list[1]);
    }

    @Test
    public void testComplexLayoutRegistration() {
        LayoutDefinition filesLayout = service.getLayoutDefinition(
                "testCategory", "files");
        assertNotNull(filesLayout);
        assertEquals("files", filesLayout.getName());

        // test rows
        LayoutRowDefinition[] rows = filesLayout.getRows();
        assertEquals(1, rows.length);
        assertEquals(1, rows[0].getWidgets().length);
        assertEquals("files", rows[0].getWidgets()[0]);
        assertEquals(1, filesLayout.getColumns());

        // test widgets
        WidgetDefinition filesWidget = filesLayout.getWidgetDefinition("files");
        assertNotNull(filesWidget);
        assertEquals("complexList", filesWidget.getType());
        FieldDefinition[] fieldDefs = filesWidget.getFieldDefinitions();
        assertEquals(1, fieldDefs.length);
        assertEquals("files", fieldDefs[0].getSchemaName());
        assertEquals("files", fieldDefs[0].getFieldName());
        WidgetDefinition[] subWidgets = filesWidget.getSubWidgetDefinitions();
        assertEquals(2, subWidgets.length);
        // test blob
        assertEquals("blob", subWidgets[0].getName());
        assertEquals("file", subWidgets[0].getType());
        fieldDefs = subWidgets[0].getFieldDefinitions();
        assertEquals(1, fieldDefs.length);
        assertNull(fieldDefs[0].getSchemaName());
        assertEquals("blob", fieldDefs[0].getFieldName());
        // test filename
        assertEquals("filename", subWidgets[1].getName());
        assertEquals("text", subWidgets[1].getType());
        fieldDefs = subWidgets[1].getFieldDefinitions();
        assertEquals(1, fieldDefs.length);
        assertNull(fieldDefs[0].getSchemaName());
        assertEquals("filename", fieldDefs[0].getFieldName());
    }

    @Test
    public void testLayoutPropertiesRegistration() {
        LayoutDefinition layoutDef = service.getLayoutDefinition(
                "testCategory", "layoutPropertiesTest");
        assertNotNull(layoutDef);
        assertEquals("layoutPropertiesTest", layoutDef.getName());

        assertNotNull(layoutDef.getProperties("any"));
        assertEquals("layoutPropValue",
                layoutDef.getProperties("any").get("layoutPropName"));

        LayoutRowDefinition[] layoutRows = layoutDef.getRows();
        assertNotNull(layoutRows);
        assertEquals(1, layoutRows.length);
        LayoutRowDefinition layoutRow = layoutRows[0];
        assertNotNull(layoutRow.getProperties("any"));
        assertEquals("layoutRowPropValue",
                layoutRow.getProperties("any").get("layoutRowPropName"));
    }

    @Test
    public void testLayoutColumnsRegistration() {
        LayoutDefinition layoutDef = service.getLayoutDefinition(
                "testCategory", "layoutColumnsTest");
        assertNotNull(layoutDef);
        assertEquals("layoutColumnsTest", layoutDef.getName());

        LayoutRowDefinition[] layoutColumns = layoutDef.getRows();
        assertNotNull(layoutColumns);
        assertEquals(1, layoutColumns.length);
        LayoutRowDefinition layoutRow = layoutColumns[0];
        assertNotNull(layoutRow.getProperties("any"));
        assertEquals("layoutColumnPropValue",
                layoutRow.getProperties("any").get("layoutColumnPropName"));
    }

}
