/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and contributors.
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
package org.nuxeo.ecm.platform.layout.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.platform.forms.layout.api.BuiltinModes;
import org.nuxeo.ecm.platform.forms.layout.api.FieldDefinition;
import org.nuxeo.ecm.platform.forms.layout.api.LayoutDefinition;
import org.nuxeo.ecm.platform.forms.layout.api.LayoutRowDefinition;
import org.nuxeo.ecm.platform.forms.layout.api.RenderingInfo;
import org.nuxeo.ecm.platform.forms.layout.api.WidgetDefinition;
import org.nuxeo.ecm.platform.forms.layout.api.WidgetReference;
import org.nuxeo.ecm.platform.forms.layout.api.WidgetSelectOption;
import org.nuxeo.ecm.platform.forms.layout.api.WidgetSelectOptions;
import org.nuxeo.ecm.platform.forms.layout.api.WidgetTypeConfiguration;
import org.nuxeo.ecm.platform.forms.layout.api.WidgetTypeDefinition;
import org.nuxeo.ecm.platform.forms.layout.api.service.LayoutStore;
import org.nuxeo.ecm.platform.forms.layout.io.JSONLayoutExporter;
import org.nuxeo.ecm.platform.forms.layout.service.WebLayoutManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.NXRuntimeTestCase;

/**
 * @author Anahide Tchertchian
 * @since 5.4
 */
public class TestLayoutExport extends NXRuntimeTestCase {

    private LayoutStore service;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        deployBundle("org.nuxeo.ecm.platform.forms.layout.core");
        deployContrib("org.nuxeo.ecm.platform.forms.layout.client",
                "OSGI-INF/layouts-framework.xml");
        deployContrib("org.nuxeo.ecm.platform.forms.layout.export.tests",
                "layouts-test-contrib.xml");
        service = Framework.getService(LayoutStore.class);
        assertNotNull(service);
    }

    @Test
    public void testWidgetTypeExport() throws Exception {
        WidgetTypeDefinition wTypeDef = service.getWidgetTypeDefinition(
                WebLayoutManager.JSF_CATEGORY, "test");
        assertNotNull(wTypeDef);

        File file = File.createTempFile("widgettype-export", ".json");
        FileOutputStream out = new FileOutputStream(file);
        JSONLayoutExporter.export(wTypeDef, out);

        InputStream written = new FileInputStream(file);
        InputStream expected = new FileInputStream(
                FileUtils.getResourcePathFromContext("widgettype-export.json"));

        String expectedString = FileUtils.read(expected).replaceAll("\r?\n", "");
        String writtenString = FileUtils.read(written).replaceAll("\r?\n", "");
        assertEquals(expectedString, writtenString);
    }

    @Test
    public void testWidgetTypesExport() throws Exception {
        WidgetTypeDefinition wTypeDef = service.getWidgetTypeDefinition(
                WebLayoutManager.JSF_CATEGORY, "test");
        assertNotNull(wTypeDef);

        File file = File.createTempFile("widgettypes-export", ".json");
        FileOutputStream out = new FileOutputStream(file);
        List<WidgetTypeDefinition> wTypeDefs = new ArrayList<WidgetTypeDefinition>();
        wTypeDefs.add(wTypeDef);
        JSONLayoutExporter.export(wTypeDefs, out);

        InputStream written = new FileInputStream(file);
        InputStream expected = new FileInputStream(
                FileUtils.getResourcePathFromContext("widgettypes-export.json"));

        String expectedString = FileUtils.read(expected).replaceAll("\r?\n", "");
        String writtenString = FileUtils.read(written).replaceAll("\r?\n", "");
        assertEquals(expectedString, writtenString);
    }

    @Test
    public void testWidgetTypeImport() throws Exception {
        checkWidgetTypeImport("widgettype-export.json", false);
        // check compat for old format
        checkWidgetTypeImport("widgettype-old-export.json", true);
    }

    protected void checkWidgetTypeImport(String filename, boolean isCompat)
            throws Exception {
        JSONObject json = null;
        InputStream in = new FileInputStream(
                FileUtils.getResourcePathFromContext(filename));
        try {
            byte[] bytes = FileUtils.readBytes(in);
            if (bytes.length != 0) {
                json = JSONObject.fromObject(new String(bytes, "UTF-8"));
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }

        WidgetTypeDefinition def = JSONLayoutExporter.importWidgetTypeDefinition(json);
        assertEquals("test", def.getName());
        assertEquals(
                "org.nuxeo.ecm.platform.layout.facelets.DummyWidgetTypeHandler",
                def.getHandlerClassName());
        Map<String, String> defProps = def.getProperties();
        assertNotNull(defProps);
        assertEquals(2, defProps.size());
        assertEquals("bar1", defProps.get("foo1"));
        assertEquals("bar2", defProps.get("foo2"));

        WidgetTypeConfiguration conf = def.getConfiguration();
        assertNotNull(conf);

        assertEquals("5.4", conf.getSinceVersion());
        assertEquals("Test widget type", conf.getTitle());
        assertEquals("This is a test widget type", conf.getDescription());
        assertEquals("test", conf.getDemoId());
        assertTrue(conf.isDemoPreviewEnabled());

        Map<String, Serializable> confProps = conf.getConfProperties();
        assertNotNull(confProps);
        assertEquals(2, confProps.size());
        assertEquals("foo", confProps.get("confProp"));
        assertEquals("dc:title", confProps.get("sortProperty"));

        List<String> supportedModes = conf.getSupportedModes();
        assertNotNull(supportedModes);
        assertEquals(2, supportedModes.size());
        assertEquals("edit", supportedModes.get(0));
        assertEquals("view", supportedModes.get(1));

        assertTrue(conf.isAcceptingSubWidgets());
        assertTrue(conf.isContainingForm());

        List<String> cats = conf.getCategories();
        assertNotNull(cats);
        assertEquals(2, cats.size());
        assertEquals("foo", cats.get(0));
        assertEquals("bar", cats.get(1));
        List<String> defaultTypes = conf.getDefaultFieldTypes();
        assertNotNull(defaultTypes);
        assertEquals(1, defaultTypes.size());
        assertEquals("string", defaultTypes.get(0));
        List<String> supportedTypes = conf.getSupportedFieldTypes();
        assertNotNull(supportedTypes);
        assertEquals(2, supportedTypes.size());
        assertEquals("string", supportedTypes.get(0));
        assertEquals("path", supportedTypes.get(1));

        List<FieldDefinition> defaultFieldDefs = conf.getDefaultFieldDefinitions();
        assertNotNull(defaultFieldDefs);
        assertEquals(2, defaultFieldDefs.size());
        assertEquals("dc:title", defaultFieldDefs.get(0).getPropertyName());
        assertEquals("data.ref", defaultFieldDefs.get(1).getPropertyName());

        Map<String, List<LayoutDefinition>> propLayouts = conf.getPropertyLayouts();
        assertNotNull(propLayouts);
        assertEquals(2, propLayouts.size());
        List<LayoutDefinition> anyLayouts = propLayouts.get(BuiltinModes.ANY);
        assertNotNull(anyLayouts);
        assertEquals(1, anyLayouts.size());
        LayoutDefinition anyLayout = anyLayouts.get(0);
        assertNull(anyLayout.getName());
        assertEquals(0, anyLayout.getTemplates().size());
        assertEquals(0, anyLayout.getProperties().size());
        Map<String, List<RenderingInfo>> lrenderingInfos = anyLayout.getRenderingInfos();
        assertNotNull(lrenderingInfos);
        assertEquals(1, lrenderingInfos.size());
        assertEquals("any", lrenderingInfos.keySet().iterator().next());
        List<RenderingInfo> linfos = lrenderingInfos.get("any");
        assertNotNull(linfos);
        assertEquals(1, linfos.size());
        RenderingInfo linfo = linfos.get(0);
        assertEquals("error", linfo.getLevel());
        assertEquals("This is my layout rendering message", linfo.getMessage());
        assertFalse(linfo.isTranslated());
        LayoutRowDefinition[] anyRows = anyLayout.getRows();
        assertEquals(1, anyRows.length);
        LayoutRowDefinition anyRow = anyRows[0];
        assertNull(anyRow.getName());
        assertEquals(0, anyRow.getProperties().size());
        String[] anyRowWidgets = anyRow.getWidgets();
        assertEquals(1, anyRowWidgets.length);
        assertEquals("required_property", anyRowWidgets[0]);

        Map<String, List<LayoutDefinition>> fieldLayouts = conf.getFieldLayouts();
        assertNotNull(fieldLayouts);
        assertEquals(1, fieldLayouts.size());
        assertEquals(1, fieldLayouts.get(BuiltinModes.ANY).size());
        assertEquals(1, fieldLayouts.get(BuiltinModes.ANY).get(0).getColumns());
        // don't test layout extensively: io code is shared with property
        // layouts

        WidgetDefinition requiredWidget = anyLayout.getWidgetDefinition("required_property");
        assertNotNull(requiredWidget);
        assertEquals("required_property", requiredWidget.getName());
        assertEquals(1, requiredWidget.getLabels().size());
        assertEquals("Required", requiredWidget.getLabel(BuiltinModes.ANY));
        assertEquals("text", requiredWidget.getType());
        assertEquals(1, requiredWidget.getFieldDefinitions().length);
        assertEquals("foo",
                requiredWidget.getFieldDefinitions()[0].getSchemaName());
        assertEquals("bar",
                requiredWidget.getFieldDefinitions()[0].getFieldName());
        assertEquals("foo:bar",
                requiredWidget.getFieldDefinitions()[0].getPropertyName());
        assertEquals(0, requiredWidget.getHelpLabels().size());
        assertEquals(0, requiredWidget.getModes().size());
        assertEquals(0, requiredWidget.getProperties().size());
        assertEquals(0, requiredWidget.getWidgetModeProperties().size());
        assertEquals(0, requiredWidget.getSelectOptions().length);
        assertEquals(0, requiredWidget.getSubWidgetDefinitions().length);
        Map<String, List<RenderingInfo>> wrenderingInfos = requiredWidget.getRenderingInfos();
        assertNotNull(wrenderingInfos);
        assertEquals(1, wrenderingInfos.size());
        assertEquals("any", wrenderingInfos.keySet().iterator().next());
        List<RenderingInfo> winfos = wrenderingInfos.get("any");
        assertNotNull(winfos);
        assertEquals(1, winfos.size());
        RenderingInfo winfo = winfos.get(0);
        assertEquals("error", winfo.getLevel());
        assertEquals("This is my widget rendering message", winfo.getMessage());
        assertFalse(winfo.isTranslated());

        List<LayoutDefinition> editLayouts = propLayouts.get(BuiltinModes.EDIT);
        assertNotNull(editLayouts);
        assertEquals(1, editLayouts.size());
        LayoutDefinition editLayout = editLayouts.get(0);
        assertNull(editLayout.getName());
        assertEquals(0, editLayout.getTemplates().size());
        assertEquals(0, editLayout.getProperties().size());
        LayoutRowDefinition[] editRows = editLayout.getRows();
        if (!isCompat) {
            assertEquals(4, editRows.length);
        } else {
            // no widget ref in compat export
            assertEquals(2, editRows.length);
        }

        LayoutRowDefinition editRow = editRows[0];
        assertNull(editRow.getName());
        assertEquals(0, editRow.getProperties().size());
        String[] editRowWidgets = editRow.getWidgets();
        assertEquals(1, editRowWidgets.length);
        assertEquals("rendered_property", editRowWidgets[0]);

        WidgetDefinition renderedWidget = editLayout.getWidgetDefinition("rendered_property");
        assertNotNull(renderedWidget);
        assertEquals("rendered_property", renderedWidget.getName());
        assertEquals(1, renderedWidget.getLabels().size());
        assertEquals("Rendered", renderedWidget.getLabel(BuiltinModes.ANY));
        assertEquals("text", renderedWidget.getType());
        assertEquals(1, renderedWidget.getFieldDefinitions().length);
        assertEquals("foo",
                renderedWidget.getFieldDefinitions()[0].getSchemaName());
        assertEquals("bar",
                renderedWidget.getFieldDefinitions()[0].getFieldName());
        assertEquals("foo:bar",
                renderedWidget.getFieldDefinitions()[0].getPropertyName());
        assertEquals(0, renderedWidget.getHelpLabels().size());
        assertEquals(1, renderedWidget.getModes().size());
        assertEquals(BuiltinModes.VIEW,
                renderedWidget.getMode(BuiltinModes.ANY));
        assertEquals(0, renderedWidget.getProperties().size());
        assertEquals(0, renderedWidget.getWidgetModeProperties().size());
        Map<String, Map<String, Serializable>> controls = renderedWidget.getControls();
        assertNotNull(controls);
        if (isCompat) {
            // no controls
            assertNull(controls.get(BuiltinModes.ANY));
        } else {
            assertNotNull(controls.get(BuiltinModes.ANY));
            assertEquals(1, controls.get(BuiltinModes.ANY).size());
            assertEquals(
                    "true",
                    controls.get(BuiltinModes.ANY).get("requireSurroundingForm"));
        }
        assertEquals(0, renderedWidget.getSelectOptions().length);
        assertEquals(1, renderedWidget.getSubWidgetDefinitions().length);

        WidgetDefinition subWidget = renderedWidget.getSubWidgetDefinitions()[0];
        assertEquals("subwidget", subWidget.getName());
        assertEquals(1, subWidget.getLabels().size());
        assertEquals("subwidget label", subWidget.getLabel(BuiltinModes.ANY));
        assertEquals("text", subWidget.getType());
        assertEquals(1, subWidget.getFieldDefinitions().length);
        assertEquals("foo", subWidget.getFieldDefinitions()[0].getSchemaName());
        assertEquals("bar", subWidget.getFieldDefinitions()[0].getFieldName());
        assertEquals("foo:bar",
                subWidget.getFieldDefinitions()[0].getPropertyName());
        assertEquals(0, subWidget.getHelpLabels().size());
        assertEquals(0, subWidget.getModes().size());
        assertEquals(0, subWidget.getProperties().size());
        assertEquals(0, subWidget.getWidgetModeProperties().size());
        assertEquals(0, subWidget.getSelectOptions().length);
        assertEquals(0, subWidget.getSubWidgetDefinitions().length);

        editRow = editRows[1];
        assertNull(editRow.getName());
        assertEquals(0, editRow.getProperties().size());
        editRowWidgets = editRow.getWidgets();
        assertEquals(1, editRowWidgets.length);
        assertEquals("selection_property", editRowWidgets[0]);

        WidgetDefinition selectionWidget = editLayout.getWidgetDefinition("selection_property");
        assertNotNull(selectionWidget);
        assertEquals("selection_property", selectionWidget.getName());
        assertEquals(1, selectionWidget.getLabels().size());
        assertEquals("Selection", selectionWidget.getLabel(BuiltinModes.ANY));
        assertEquals("selectOneListbox", selectionWidget.getType());
        assertEquals(1, selectionWidget.getFieldDefinitions().length);
        assertEquals("foo2",
                selectionWidget.getFieldDefinitions()[0].getSchemaName());
        assertEquals("bar2",
                selectionWidget.getFieldDefinitions()[0].getFieldName());
        assertEquals("foo2:bar2",
                selectionWidget.getFieldDefinitions()[0].getPropertyName());
        assertEquals(0, selectionWidget.getHelpLabels().size());
        assertEquals(0, selectionWidget.getModes().size());
        assertEquals(0, selectionWidget.getProperties().size());
        assertEquals(0, selectionWidget.getWidgetModeProperties().size());
        WidgetSelectOption[] options = selectionWidget.getSelectOptions();
        assertNotNull(options);
        assertEquals(5, options.length);
        assertFalse(options[0] instanceof WidgetSelectOptions);
        checkCommonSelectOption(options[0], null, null, "bar", "foo", null,
                null);
        assertFalse(options[1] instanceof WidgetSelectOptions);
        checkCommonSelectOption(options[1], "#{currentDocument}", "doc",
                "#{doc.id}", "#{doc.dc.title}", "false", "true");
        assertTrue(options[2] instanceof WidgetSelectOptions);
        checkMultipleSelectOption((WidgetSelectOptions) options[2],
                "#{myBean.myList}", "item", "#{item.id}", "#{item.title}",
                null, null, null, null);
        assertTrue(options[3] instanceof WidgetSelectOptions);
        checkMultipleSelectOption((WidgetSelectOptions) options[3],
                "#{documentList}", "doc", "#{doc.id}", "#{doc.dc.title}",
                "false", "true", "label", Boolean.TRUE);
        assertFalse(options[4] instanceof WidgetSelectOptions);
        checkCommonSelectOption(options[4], null, null, "bar2", "foo2", null,
                null);
        assertEquals(0, selectionWidget.getSubWidgetDefinitions().length);

        if (isCompat) {
            return;
        }

        // check widgets with subwidgets refs in new export
        editRow = editRows[2];
        assertNull(editRow.getName());
        assertEquals(0, editRow.getProperties().size());
        editRowWidgets = editRow.getWidgets();
        assertEquals(1, editRowWidgets.length);
        assertEquals("subwidgets", editRowWidgets[0]);

        WidgetDefinition withSubwidgets = editLayout.getWidgetDefinition("subwidgets");
        assertNotNull(withSubwidgets);
        assertEquals("subwidgets", withSubwidgets.getName());
        assertEquals(1, withSubwidgets.getLabels().size());
        assertEquals("Selection", withSubwidgets.getLabel(BuiltinModes.ANY));
        assertEquals("test", withSubwidgets.getType());
        assertEquals(1, withSubwidgets.getFieldDefinitions().length);
        assertEquals("foo2",
                withSubwidgets.getFieldDefinitions()[0].getSchemaName());
        assertEquals("bar2",
                withSubwidgets.getFieldDefinitions()[0].getFieldName());
        assertEquals("foo2:bar2",
                withSubwidgets.getFieldDefinitions()[0].getPropertyName());
        assertEquals(0, withSubwidgets.getHelpLabels().size());
        assertEquals(0, withSubwidgets.getModes().size());
        assertEquals(0, withSubwidgets.getProperties().size());
        assertEquals(0, withSubwidgets.getWidgetModeProperties().size());
        assertNotNull(withSubwidgets.getSelectOptions());
        assertEquals(0, withSubwidgets.getSelectOptions().length);
        assertEquals(1, withSubwidgets.getSubWidgetDefinitions().length);
        assertEquals(0, withSubwidgets.getSubWidgetReferences().length);

        WidgetDefinition subWidgetDef = withSubwidgets.getSubWidgetDefinitions()[0];
        assertEquals("subwidget", subWidgetDef.getName());
        assertEquals("text", subWidgetDef.getType());
        assertEquals(1, subWidgetDef.getLabels().size());
        assertEquals("subwidget label", subWidgetDef.getLabel(BuiltinModes.ANY));
        assertEquals(1, subWidgetDef.getFieldDefinitions().length);
        assertEquals("foo",
                subWidgetDef.getFieldDefinitions()[0].getSchemaName());
        assertEquals("bar",
                subWidgetDef.getFieldDefinitions()[0].getFieldName());
        assertEquals("foo:bar",
                subWidgetDef.getFieldDefinitions()[0].getPropertyName());
        assertEquals(0, subWidgetDef.getHelpLabels().size());
        assertEquals(0, subWidgetDef.getModes().size());
        assertEquals(0, subWidgetDef.getProperties().size());
        assertEquals(0, subWidgetDef.getWidgetModeProperties().size());
        assertNotNull(subWidgetDef.getSelectOptions());
        assertEquals(0, subWidgetDef.getSelectOptions().length);
        assertEquals(0, subWidgetDef.getSubWidgetDefinitions().length);
        assertEquals(0, subWidgetDef.getSubWidgetReferences().length);

        editRow = editRows[3];
        assertNull(editRow.getName());
        assertEquals(0, editRow.getProperties().size());
        editRowWidgets = editRow.getWidgets();
        assertEquals(1, editRowWidgets.length);
        assertEquals("subwidgetRefs", editRowWidgets[0]);

        WidgetDefinition withSubwidgetRefs = editLayout.getWidgetDefinition("subwidgetRefs");
        assertNotNull(withSubwidgetRefs);
        assertEquals("subwidgetRefs", withSubwidgetRefs.getName());
        assertEquals(1, withSubwidgetRefs.getLabels().size());
        assertEquals("Selection", withSubwidgetRefs.getLabel(BuiltinModes.ANY));
        assertEquals("test", withSubwidgetRefs.getType());
        assertEquals(0, withSubwidgetRefs.getFieldDefinitions().length);
        assertEquals(0, withSubwidgetRefs.getHelpLabels().size());
        assertEquals(0, withSubwidgetRefs.getModes().size());
        assertEquals(0, withSubwidgetRefs.getProperties().size());
        assertEquals(0, withSubwidgetRefs.getWidgetModeProperties().size());
        assertNotNull(withSubwidgetRefs.getSelectOptions());
        assertEquals(0, withSubwidgetRefs.getSelectOptions().length);
        assertEquals(0, withSubwidgetRefs.getSubWidgetDefinitions().length);
        assertEquals(1, withSubwidgetRefs.getSubWidgetReferences().length);

        WidgetReference subWidgetRef = withSubwidgetRefs.getSubWidgetReferences()[0];
        assertEquals("localSubWidget", subWidgetRef.getName());
        assertTrue(StringUtils.isBlank(subWidgetRef.getCategory()));

        WidgetDefinition subWidgetRefDef = editLayout.getWidgetDefinition("localSubWidget");
        assertNotNull(subWidgetRefDef);
        assertEquals("localSubWidget", subWidgetRefDef.getName());
        assertEquals("foo3",
                subWidgetRefDef.getFieldDefinitions()[0].getSchemaName());
        assertEquals("bar3",
                subWidgetRefDef.getFieldDefinitions()[0].getFieldName());
        assertEquals("foo3:bar3",
                subWidgetRefDef.getFieldDefinitions()[0].getPropertyName());
        assertEquals(0, subWidgetRefDef.getLabels().size());
        assertNull(subWidgetRefDef.getLabel(BuiltinModes.ANY));
        assertEquals("test", subWidgetRefDef.getType());
        assertEquals(1, subWidgetRefDef.getFieldDefinitions().length);
        assertEquals(0, subWidgetRefDef.getHelpLabels().size());
        assertEquals(0, subWidgetRefDef.getModes().size());
        assertEquals(0, subWidgetRefDef.getProperties().size());
        assertEquals(0, subWidgetRefDef.getWidgetModeProperties().size());
        assertNotNull(subWidgetRefDef.getSelectOptions());
        assertEquals(0, subWidgetRefDef.getSelectOptions().length);
        assertEquals(0, subWidgetRefDef.getSubWidgetDefinitions().length);
        assertEquals(0, subWidgetRefDef.getSubWidgetReferences().length);
    }

    protected void checkCommonSelectOption(WidgetSelectOption option,
            Object value, String var, String itemValue, String itemLabel,
            Object itemDisabled, Object itemRendered) {
        assertEquals(value, option.getValue());
        assertEquals(var, option.getVar());
        assertEquals(itemValue, option.getItemValue());
        assertEquals(itemLabel, option.getItemLabel());
        assertEquals(itemDisabled, option.getItemDisabled());
        assertEquals(itemRendered, option.getItemRendered());
    }

    protected void checkMultipleSelectOption(WidgetSelectOptions option,
            Object value, String var, String itemValue, String itemLabel,
            Object itemDisabled, Object itemRendered, String ordering,
            Boolean caseSensitive) {
        checkCommonSelectOption(option, value, var, itemValue, itemLabel,
                itemDisabled, itemRendered);
        assertEquals(ordering, option.getOrdering());
        assertEquals(caseSensitive, option.getCaseSensitive());
    }

}
