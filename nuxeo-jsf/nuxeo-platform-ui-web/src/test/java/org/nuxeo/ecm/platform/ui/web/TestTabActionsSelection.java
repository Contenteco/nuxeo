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
package org.nuxeo.ecm.platform.ui.web;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.nuxeo.ecm.platform.actions.Action;
import org.nuxeo.ecm.platform.actions.ejb.ActionManager;
import org.nuxeo.ecm.platform.ui.web.api.TabActionsSelection;
import org.nuxeo.ecm.platform.ui.web.api.WebActions;

/**
 * @since 5.4.2
 */
public class TestTabActionsSelection extends TestCase {

    ActionManager actionManager;

    static String DEFAULT_CATEGORY = WebActions.DEFAULT_TABS_CATEGORY;

    static String CUSTOM_CATEGORY = "custom_category";

    static enum TAB_ACTION {

        // standard tab
        TAB_VIEW(new String[] { DEFAULT_CATEGORY }),
        // custom tab with multiple categories
        TAB_CUSTOM_MULTICATS(new String[] { DEFAULT_CATEGORY, CUSTOM_CATEGORY }),
        // custom tab
        TAB_CUSTOM(new String[] { CUSTOM_CATEGORY }),
        // custom sub tab
        SUBTAB_CUSTOM(new String[] { "TAB_CUSTOM"
                + WebActions.SUBTAB_CATEGORY_SUFFIX });

        String[] categories;

        TAB_ACTION(String[] categories) {
            this.categories = categories;
        }

        public String getId() {
            return name();
        }

        public Action getAction() {
            return new Action(name(), categories);
        }

    }

    @Override
    protected void setUp() throws Exception {
        List<Action> testActions = new ArrayList<Action>();
        for (TAB_ACTION tabAction : TAB_ACTION.values()) {
            testActions.add(tabAction.getAction());
        }
        actionManager = new MockActionManager(testActions);
    }

    @Override
    protected void tearDown() throws Exception {
        actionManager = null;
    }

    public void testSetCurrentTabAction() throws Exception {
        TabActionsSelection sel = new TabActionsSelection();
        sel.setCurrentTabAction(CUSTOM_CATEGORY,
                TAB_ACTION.TAB_CUSTOM.getAction());
        assertEquals(TAB_ACTION.TAB_CUSTOM.getAction(),
                sel.getCurrentTabAction(CUSTOM_CATEGORY));
        assertEquals(TAB_ACTION.TAB_CUSTOM.getId(),
                sel.getCurrentTabId(CUSTOM_CATEGORY));
        assertEquals("custom_category:TAB_CUSTOM", sel.getCurrentTabIds());
        // add another one
        sel.setCurrentTabAction(DEFAULT_CATEGORY,
                TAB_ACTION.TAB_VIEW.getAction());
        assertEquals(TAB_ACTION.TAB_VIEW.getAction(),
                sel.getCurrentTabAction(DEFAULT_CATEGORY));
        assertEquals(TAB_ACTION.TAB_VIEW.getId(),
                sel.getCurrentTabId(DEFAULT_CATEGORY));
        // check nothing's changed for previous selection
        assertEquals(TAB_ACTION.TAB_CUSTOM.getAction(),
                sel.getCurrentTabAction(CUSTOM_CATEGORY));
        assertEquals(TAB_ACTION.TAB_CUSTOM.getId(),
                sel.getCurrentTabId(CUSTOM_CATEGORY));
        assertEquals("custom_category:TAB_CUSTOM,:TAB_VIEW",
                sel.getCurrentTabIds());
        // check reset
        sel.resetCurrentTabs(CUSTOM_CATEGORY);
        assertNull(sel.getCurrentTabAction(CUSTOM_CATEGORY));
        assertNull(sel.getCurrentTabId(CUSTOM_CATEGORY));
        assertEquals(TAB_ACTION.TAB_VIEW.getAction(),
                sel.getCurrentTabAction(DEFAULT_CATEGORY));
        assertEquals(TAB_ACTION.TAB_VIEW.getId(),
                sel.getCurrentTabId(DEFAULT_CATEGORY));
        assertEquals(":TAB_VIEW", sel.getCurrentTabIds());
    }

    public void testSetCurrentTabActionWithSubTab() throws Exception {
        TabActionsSelection sel = new TabActionsSelection();
        sel.setCurrentTabAction(CUSTOM_CATEGORY,
                TAB_ACTION.TAB_CUSTOM.getAction());
        assertEquals(TAB_ACTION.TAB_CUSTOM.getAction(),
                sel.getCurrentTabAction(CUSTOM_CATEGORY));
        assertEquals(TAB_ACTION.TAB_CUSTOM.getId(),
                sel.getCurrentTabId(CUSTOM_CATEGORY));
        assertEquals("custom_category:TAB_CUSTOM", sel.getCurrentTabIds());
        // add a sub tab
        sel.setCurrentTabAction(TAB_ACTION.TAB_CUSTOM.getId()
                + WebActions.SUBTAB_CATEGORY_SUFFIX,
                TAB_ACTION.SUBTAB_CUSTOM.getAction());
        assertEquals(TAB_ACTION.SUBTAB_CUSTOM.getAction(),
                sel.getCurrentTabAction(TAB_ACTION.TAB_CUSTOM.getId()
                        + WebActions.SUBTAB_CATEGORY_SUFFIX));
        assertEquals(TAB_ACTION.SUBTAB_CUSTOM.getId(),
                sel.getCurrentTabId(TAB_ACTION.TAB_CUSTOM.getId()
                        + WebActions.SUBTAB_CATEGORY_SUFFIX));
        assertEquals("custom_category:TAB_CUSTOM:SUBTAB_CUSTOM",
                sel.getCurrentTabIds());
        // check override
        sel.setCurrentTabAction(CUSTOM_CATEGORY,
                TAB_ACTION.TAB_CUSTOM_MULTICATS.getAction());
        assertEquals(TAB_ACTION.TAB_CUSTOM_MULTICATS.getAction(),
                sel.getCurrentTabAction(CUSTOM_CATEGORY));
        assertEquals(TAB_ACTION.TAB_CUSTOM_MULTICATS.getId(),
                sel.getCurrentTabId(CUSTOM_CATEGORY));
        // check subtab is not there anymore
        assertNull(sel.getCurrentTabAction(TAB_ACTION.TAB_CUSTOM.getId()
                + WebActions.SUBTAB_CATEGORY_SUFFIX));
        assertNull(sel.getCurrentTabId(TAB_ACTION.TAB_CUSTOM.getId()
                + WebActions.SUBTAB_CATEGORY_SUFFIX));
        assertEquals("custom_category:TAB_CUSTOM_MULTICATS",
                sel.getCurrentTabIds());
    }

    public void testResetCurrentTabActionWithSubTab() throws Exception {
        TabActionsSelection sel = new TabActionsSelection();
        sel.setCurrentTabAction(CUSTOM_CATEGORY,
                TAB_ACTION.TAB_CUSTOM.getAction());
        sel.setCurrentTabAction(TAB_ACTION.TAB_CUSTOM.getId()
                + WebActions.SUBTAB_CATEGORY_SUFFIX,
                TAB_ACTION.SUBTAB_CUSTOM.getAction());
        // check reset
        sel.resetCurrentTabs(CUSTOM_CATEGORY);
        assertNull(sel.getCurrentTabAction(CUSTOM_CATEGORY));
        assertNull(sel.getCurrentTabId(CUSTOM_CATEGORY));
        assertNull(sel.getCurrentTabAction(TAB_ACTION.TAB_CUSTOM.getId()
                + WebActions.SUBTAB_CATEGORY_SUFFIX));
        assertNull(sel.getCurrentTabId(TAB_ACTION.TAB_CUSTOM.getId()
                + WebActions.SUBTAB_CATEGORY_SUFFIX));
        assertEquals("", sel.getCurrentTabIds());
    }

    public void testSetCurrentTabId() throws Exception {
        TabActionsSelection sel = new TabActionsSelection();
        sel.setCurrentTabId(actionManager, null, CUSTOM_CATEGORY,
                TAB_ACTION.TAB_CUSTOM.getId());
        assertEquals(TAB_ACTION.TAB_CUSTOM.getAction(),
                sel.getCurrentTabAction(CUSTOM_CATEGORY));
        assertEquals(TAB_ACTION.TAB_CUSTOM.getId(),
                sel.getCurrentTabId(CUSTOM_CATEGORY));
        assertEquals("custom_category:TAB_CUSTOM", sel.getCurrentTabIds());
        // add another one
        sel.setCurrentTabId(actionManager, null, DEFAULT_CATEGORY,
                TAB_ACTION.TAB_VIEW.getId());
        assertEquals(TAB_ACTION.TAB_VIEW.getAction(),
                sel.getCurrentTabAction(DEFAULT_CATEGORY));
        assertEquals(TAB_ACTION.TAB_VIEW.getId(),
                sel.getCurrentTabId(DEFAULT_CATEGORY));
        // check nothing's changed for previous selection
        assertEquals(TAB_ACTION.TAB_CUSTOM.getAction(),
                sel.getCurrentTabAction(CUSTOM_CATEGORY));
        assertEquals(TAB_ACTION.TAB_CUSTOM.getId(),
                sel.getCurrentTabId(CUSTOM_CATEGORY));
        assertEquals("custom_category:TAB_CUSTOM,:TAB_VIEW",
                sel.getCurrentTabIds());
        // check reset
        sel.resetCurrentTabs(CUSTOM_CATEGORY);
        assertNull(sel.getCurrentTabAction(CUSTOM_CATEGORY));
        assertNull(sel.getCurrentTabId(CUSTOM_CATEGORY));
        assertEquals(TAB_ACTION.TAB_VIEW.getId(),
                sel.getCurrentTabId(DEFAULT_CATEGORY));
        assertEquals(TAB_ACTION.TAB_VIEW.getId(),
                sel.getCurrentTabId(DEFAULT_CATEGORY));
        assertEquals(":TAB_VIEW", sel.getCurrentTabIds());
    }

    public void testSetCurrentTabIds() throws Exception {
        TabActionsSelection sel = new TabActionsSelection();
        sel.setCurrentTabIds(actionManager, null,
                "custom_category:TAB_CUSTOM:SUBTAB_CUSTOM,:TAB_VIEW");
        assertEquals(TAB_ACTION.TAB_VIEW.getAction(),
                sel.getCurrentTabAction(DEFAULT_CATEGORY));
        assertEquals(TAB_ACTION.TAB_VIEW.getId(),
                sel.getCurrentTabId(DEFAULT_CATEGORY));
        assertEquals(TAB_ACTION.TAB_CUSTOM.getAction(),
                sel.getCurrentTabAction(CUSTOM_CATEGORY));
        assertEquals(TAB_ACTION.TAB_CUSTOM.getId(),
                sel.getCurrentTabId(CUSTOM_CATEGORY));
        assertEquals(TAB_ACTION.SUBTAB_CUSTOM.getId(),
                sel.getCurrentTabId(TAB_ACTION.TAB_CUSTOM.getId()
                        + WebActions.SUBTAB_CATEGORY_SUFFIX));
        assertEquals("custom_category:TAB_CUSTOM:SUBTAB_CUSTOM,:TAB_VIEW",
                sel.getCurrentTabIds());
    }

    protected TabActionsSelection getTestSelectionToReset() {
        TabActionsSelection sel = new TabActionsSelection();
        sel.setCurrentTabIds(actionManager, null,
                "custom_category:TAB_CUSTOM:SUBTAB_CUSTOM,:TAB_VIEW");
        assertEquals(TAB_ACTION.TAB_VIEW.getId(),
                sel.getCurrentTabId(DEFAULT_CATEGORY));
        assertEquals(TAB_ACTION.TAB_CUSTOM.getId(),
                sel.getCurrentTabId(CUSTOM_CATEGORY));
        assertEquals(TAB_ACTION.SUBTAB_CUSTOM.getId(),
                sel.getCurrentTabId(TAB_ACTION.TAB_CUSTOM.getId()
                        + WebActions.SUBTAB_CATEGORY_SUFFIX));
        assertEquals("custom_category:TAB_CUSTOM:SUBTAB_CUSTOM,:TAB_VIEW",
                sel.getCurrentTabIds());
        return sel;
    }

    public void testSetCurrentTabIds_resetSubTab() throws Exception {
        TabActionsSelection sel = getTestSelectionToReset();
        sel.setCurrentTabIds(actionManager, null,
                "custom_category:TAB_CUSTOM,:TAB_VIEW");
        assertEquals(TAB_ACTION.TAB_VIEW.getId(),
                sel.getCurrentTabId(DEFAULT_CATEGORY));
        assertEquals(TAB_ACTION.TAB_CUSTOM.getId(),
                sel.getCurrentTabId(CUSTOM_CATEGORY));
        assertNull(sel.getCurrentTabId(TAB_ACTION.TAB_CUSTOM.getId()
                + WebActions.SUBTAB_CATEGORY_SUFFIX));
        assertEquals("custom_category:TAB_CUSTOM,:TAB_VIEW",
                sel.getCurrentTabIds());
    }

    public void testSetCurrentTabIds_resetDefaultTab() throws Exception {
        TabActionsSelection sel = getTestSelectionToReset();
        sel.setCurrentTabIds(actionManager, null, ":");
        assertNull(sel.getCurrentTabId(DEFAULT_CATEGORY));
        assertEquals(TAB_ACTION.TAB_CUSTOM.getId(),
                sel.getCurrentTabId(CUSTOM_CATEGORY));
        assertEquals(TAB_ACTION.SUBTAB_CUSTOM.getId(),
                sel.getCurrentTabId(TAB_ACTION.TAB_CUSTOM.getId()
                        + WebActions.SUBTAB_CATEGORY_SUFFIX));
        assertEquals("custom_category:TAB_CUSTOM:SUBTAB_CUSTOM",
                sel.getCurrentTabIds());
    }

    public void testSetCurrentTabIds_resetTabWithSubTab() throws Exception {
        TabActionsSelection sel = getTestSelectionToReset();
        sel.setCurrentTabIds(actionManager, null, "custom_category:,:TAB_VIEW");
        assertEquals(TAB_ACTION.TAB_VIEW.getId(),
                sel.getCurrentTabId(DEFAULT_CATEGORY));
        assertNull(sel.getCurrentTabId(CUSTOM_CATEGORY));
        assertNull(sel.getCurrentTabId(TAB_ACTION.TAB_CUSTOM.getId()
                + WebActions.SUBTAB_CATEGORY_SUFFIX));
        assertEquals(":TAB_VIEW", sel.getCurrentTabIds());
    }

    public void testSetCurrentTabIds_resetAll() throws Exception {
        TabActionsSelection sel = getTestSelectionToReset();
        sel.setCurrentTabIds(actionManager, null, "*:");
        assertNull(sel.getCurrentTabId(DEFAULT_CATEGORY));
        assertNull(sel.getCurrentTabId(CUSTOM_CATEGORY));
        assertNull(sel.getCurrentTabId(TAB_ACTION.TAB_CUSTOM.getId()
                + WebActions.SUBTAB_CATEGORY_SUFFIX));
        assertEquals("", sel.getCurrentTabIds());
    }

    public void testSetCurrentTabIds_resetAllAndSet() throws Exception {
        TabActionsSelection sel = getTestSelectionToReset();
        sel.setCurrentTabIds(actionManager, null, "*:,:TAB_VIEW");
        assertEquals(TAB_ACTION.TAB_VIEW.getId(),
                sel.getCurrentTabId(DEFAULT_CATEGORY));
        assertNull(sel.getCurrentTabId(CUSTOM_CATEGORY));
        assertNull(sel.getCurrentTabId(TAB_ACTION.TAB_CUSTOM.getId()
                + WebActions.SUBTAB_CATEGORY_SUFFIX));
        assertEquals(":TAB_VIEW", sel.getCurrentTabIds());
    }

}
