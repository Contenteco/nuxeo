/*
 * (C) Copyright 2007-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Eugen Ionica
 *     Anahide Tchertchian
 *     Florent Guillaume
 */

package org.nuxeo.ecm.webapp.action;

import static org.jboss.seam.ScopeType.CONVERSATION;
import static org.jboss.seam.ScopeType.EVENT;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.ejb.PostActivate;
import javax.ejb.PrePassivate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.Component;
import org.jboss.seam.annotations.Destroy;
import org.jboss.seam.annotations.Factory;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Observer;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.intercept.BypassInterceptors;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.actions.Action;
import org.nuxeo.ecm.platform.actions.ActionContext;
import org.nuxeo.ecm.platform.actions.ejb.ActionManager;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;
import org.nuxeo.ecm.platform.ui.web.api.TabActionsSelection;
import org.nuxeo.ecm.platform.ui.web.api.WebActions;
import org.nuxeo.ecm.webapp.helpers.EventNames;

/**
 * Component that handles actions retrieval as well as current tab(s)
 * selection.
 *
 * @author Eugen Ionica
 * @author Anahide Tchertchian
 * @author Florent Guillaume
 * @author <a href="mailto:cbaican@nuxeo.com">Catalin Baican</a>
 */
@Name("webActions")
@Scope(CONVERSATION)
@Install(precedence = Install.FRAMEWORK)
public class WebActionsBean implements WebActionsLocal, Serializable {

    private static final long serialVersionUID = 1959221536502251848L;

    private static final Log log = LogFactory.getLog(WebActionsBean.class);

    @In(create = true, required = false)
    protected transient ActionManager actionManager;

    @In(create = true, required = false)
    protected transient ActionContextProvider actionContextProvider;

    @In(create = true, required = false)
    protected transient NavigationContext navigationContext;

    protected List<Action> tabsActionsList;

    protected String subTabsCategory;

    protected List<Action> subTabsActionsList;

    protected TabActionsSelection currentTabActions = new TabActionsSelection();

    public void initialize() {
        log.debug("Initializing...");
    }

    @Destroy
    public void destroy() {
        log.debug("Removing Seam action listener...");
    }

    @PrePassivate
    public void saveState() {
        log.debug("PrePassivate");
    }

    @PostActivate
    public void readState() {
        log.debug("PostActivate");
    }

    // actions management

    public List<Action> getActionsList(String category, ActionContext context) {
        List<Action> list = new ArrayList<Action>();
        List<Action> actions = actionManager.getActions(category, context);
        if (actions != null) {
            list.addAll(actions);
        }
        return list;
    }

    public List<Action> getActionsList(String category) {
        return getActionsList(category, createActionContext());
    }

    public List<Action> getUnfiltredActionsList(String category,
            ActionContext context) {
        List<Action> list = new ArrayList<Action>();
        List<Action> actions = actionManager.getActions(category, context,
                false);
        if (actions != null) {
            list.addAll(actions);
        }
        return list;
    }

    public List<Action> getUnfiltredActionsList(String category) {
        return getUnfiltredActionsList(category, createActionContext());
    }

    public List<Action> getAllActions(String category) {
        return actionManager.getAllActions(category);
    }

    protected ActionContext createActionContext() {
        return actionContextProvider.createActionContext();
    }

    // tabs management

    protected Action getDefaultTab(String category) {
        if (DEFAULT_TABS_CATEGORY.equals(category)) {
            if (getTabsList() == null) {
                return null;
            }
            try {
                return tabsActionsList.get(0);
            } catch (IndexOutOfBoundsException e) {
                return null;
            }
        } else {
            // check if it's a subtab
            if (subTabsCategory != null && subTabsCategory.equals(category)) {
                if (getSubTabsList() == null) {
                    return null;
                }
                try {
                    return subTabsActionsList.get(0);
                } catch (IndexOutOfBoundsException e) {
                    return null;
                }
            }
            // retrieve actions in given category and take the first one found
            List<Action> actions = getActionsList(category,
                    createActionContext());
            if (actions != null && actions.size() > 0) {
                return actions.get(0);
            }
            return null;
        }

    }

    @Override
    public Action getCurrentTabAction(String category) {
        Action action = currentTabActions.getCurrentTabAction(category);
        if (action == null) {
            // return default action
            action = getDefaultTab(category);
        }
        return action;
    }

    @Override
    public void setCurrentTabAction(String category, Action tabAction) {
        currentTabActions.setCurrentTabAction(category, tabAction);
        // additional cleanup of this cache
        if (WebActions.DEFAULT_TABS_CATEGORY.equals(category)) {
            subTabsCategory = null;
            subTabsActionsList = null;
        }
    }

    @Override
    public Action getCurrentSubTabAction(String parentActionId) {
        return getCurrentTabAction(TabActionsSelection.getSubTabCategory(parentActionId));
    }

    @Override
    public String getCurrentTabId(String category) {
        Action action = getCurrentTabAction(category);
        if (action != null) {
            return action.getId();
        }
        return null;
    }

    @Override
    public void setCurrentTabId(String category, String tabId,
            String... subTabIds) {
        currentTabActions.setCurrentTabId(actionManager, createActionContext(),
                category, tabId, subTabIds);
    }

    @Override
    public String getCurrentTabIds() {
        return currentTabActions.getCurrentTabIds();
    }

    @Override
    public void setCurrentTabIds(String tabIds) {
        currentTabActions.setCurrentTabIds(actionManager,
                createActionContext(), tabIds);
    }

    @Override
    public void resetCurrentTabs() {
        currentTabActions.resetCurrentTabs();
    }

    @Override
    public void resetCurrentTabs(String category) {
        currentTabActions.resetCurrentTabs(category);
    }

    // tabs management specific to the DEFAULT_TABS_CATEGORY

    public void resetCurrentTab() {
        resetCurrentTabs(DEFAULT_TABS_CATEGORY);
    }

    @Observer(value = { EventNames.USER_ALL_DOCUMENT_TYPES_SELECTION_CHANGED,
            EventNames.LOCATION_SELECTION_CHANGED }, create = false)
    @BypassInterceptors
    public void resetTabList() {
        tabsActionsList = null;
        subTabsCategory = null;
        subTabsActionsList = null;
        resetCurrentTab();
    }

    @Factory(value = "tabsActionsList", scope = EVENT)
    public List<Action> getTabsList() {
        if (tabsActionsList == null) {
            tabsActionsList = getActionsList(DEFAULT_TABS_CATEGORY);
        }
        return tabsActionsList;
    }

    @Factory(value = "subTabsActionsList", scope = EVENT)
    public List<Action> getSubTabsList() {
        if (subTabsActionsList == null) {
            String currentTabId = getCurrentTabId();
            if (currentTabId != null) {
                subTabsCategory = TabActionsSelection.getSubTabCategory(currentTabId);
                subTabsActionsList = getActionsList(subTabsCategory);
            }
        }
        return subTabsActionsList;
    }

    @Factory(value = "currentTabAction", scope = EVENT)
    @Deprecated
    public Action getCurrentTabAction() {
        return getCurrentTabAction(DEFAULT_TABS_CATEGORY);
    }

    @Deprecated
    public void setCurrentTabAction(Action currentTabAction) {
        setCurrentTabAction(DEFAULT_TABS_CATEGORY, currentTabAction);
    }

    @Factory(value = "currentSubTabAction", scope = EVENT)
    @Deprecated
    public Action getCurrentSubTabAction() {
        Action action = getCurrentTabAction();
        if (action != null) {
            return getCurrentTabAction(TabActionsSelection.getSubTabCategory(action.getId()));
        }
        return null;
    }

    @Deprecated
    public void setCurrentSubTabAction(Action tabAction) {
        if (tabAction != null) {
            String[] categories = tabAction.getCategories();
            if (categories == null || categories.length == 0) {
                log.error(String.format("Cannot set subtab with id '%s' "
                        + "as this action does not hold any category",
                        tabAction.getId()));
                return;
            }
            if (categories.length != 1) {
                log.error(String.format(
                        "Setting subtab with id '%s' with category '%s': "
                                + "use webActions#setCurrentTabAction(action, category) "
                                + "to specify another category",
                        tabAction.getId(), categories[0]));
            }
            setCurrentTabAction(categories[0], tabAction);
        }
    }

    @Deprecated
    public String getCurrentTabId() {
        Action currentTab = getCurrentTabAction();
        if (currentTab != null) {
            return currentTab.getId();
        }
        return null;
    }

    @Deprecated
    public void setCurrentTabId(String tabId) {
        setCurrentTabId(DEFAULT_TABS_CATEGORY, tabId);
    }

    @Deprecated
    public String getCurrentSubTabId() {
        Action currentSubTab = getCurrentSubTabAction();
        if (currentSubTab != null) {
            return currentSubTab.getId();
        }
        return null;
    }

    @Deprecated
    public void setCurrentSubTabId(String tabId) {
        Action action = getCurrentTabAction();
        if (action != null) {
            setCurrentTabId(
                    TabActionsSelection.getSubTabCategory(action.getId()),
                    tabId);
        }
    }

    // navigation API

    public String setCurrentTabAndNavigate(String currentTabActionId) {
        return setCurrentTabAndNavigate(navigationContext.getCurrentDocument(),
                currentTabActionId);
    }

    public String setCurrentTabAndNavigate(DocumentModel document,
            String currentTabActionId) {
        // navigate first because it will reset the tabs list
        String viewId = null;
        try {
            viewId = navigationContext.navigateToDocument(document);
        } catch (ClientException e) {

        }
        // force creation of new actions if needed
        getTabsList();
        // set current tab
        setCurrentTabId(currentTabActionId);
        return viewId;
    }

    // deprecated API

    @Deprecated
    public List<Action> getSubViewActionsList() {
        return getActionsList("SUBVIEW_UPPER_LIST");
    }

    @Deprecated
    public void selectTabAction() {
        // if (tabAction != null) {
        // setCurrentTabAction(tabAction);
        // }
    }

    @Deprecated
    public String getCurrentLifeCycleState() throws ClientException {
        // only user of documentManager in this bean, look it up by hand
        CoreSession documentManager = (CoreSession) Component.getInstance("documentManager");
        return documentManager.getCurrentLifeCycleState(navigationContext.getCurrentDocument().getRef());
    }

    @Deprecated
    public void setTabsList(List<Action> tabsList) {
        tabsActionsList = tabsList;
    }

    @Deprecated
    public void setSubTabsList(List<Action> tabsList) {
        subTabsActionsList = tabsList;
        subTabsCategory = null;
        if (tabsList != null) {
            // BBB code
            for (Action action : tabsList) {
                if (action != null) {
                    String[] categories = action.getCategories();
                    if (categories != null && categories.length > 0) {
                        subTabsCategory = categories[0];
                        break;
                    }
                }
            }
        }
    }

    @Deprecated
    public void setCurrentTabAction(String currentTabActionId) {
        setCurrentTabId(currentTabActionId);
    }

}
