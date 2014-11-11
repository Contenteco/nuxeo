# (C) Copyright 2009 Nuxeo SAS <http://nuxeo.com>
# Author: bdelbosc@nuxeo.com
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License version 2 as published
# by the Free Software Foundation.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
# 02111-1307, USA.
#
"""
This modules is tied with the Nuxeo EP application.

TODO:

Folder

* emptyTrash()

* next() previous()

Dashboard

* wsRefresh()

Document

* files().add(filename, path)

* comments().add(comment)

* relation().add(uid)

* publish(section_uid)
"""
import random
import time
from urllib import quote_plus, quote
from webunit.utility import Upload
from utils import extractToken, extractJsfState, extractIframes, extractJsessionId
from funkload.utils import Data


def getTabParams(tab, subtab=None):
    """Return nuxeo tabids parameters"""
    if tab is None:
        return ''
    ret = "&tabIds=%3A" + tab
    if subtab:
        ret += '%3A' + subtab
    return ret


class BasePage:
    """Base class for nuxeo ep page."""
    fl = None

    def __init__(self, fl):
        self.fl = fl

    # helpers
    def getDocUid(self):
        fl = self.fl
        uid = extractToken(fl.getBody(), "var currentDocURL = 'default/", "'")
        fl.assert_(uid, 'Current document uid not found.')
        return uid

    def getConversationId(self):
        fl = self.fl
        cId = extractToken(fl.getBody(), "var currentConversationId = '", "'")
        fl.assert_(cId, 'Current conversation id not found')
        return cId

    def available(self):
        """Check if the server is available."""
        fl = self.fl
        fl.get(fl.server_url + '/login.jsp',
               description="Check if the server is alive")

    # pages
    def logout(self):
        """Log out the current user."""
        fl = self.fl
        fl.get(fl.server_url + '/logout',
               description="Log out")
        fl.assert_('login' in fl.getLastUrl(),
                   "Not redirected to login page.")
        fl.current_login = None
        return LoginPage(self.fl)

    def login(self, user, password):
        fl = self.fl
        fl.setHeader('Accept-Language', 'en')
        fl.post(fl.server_url + "/nxstartup.faces", params=[
            ['language', 'en_US'],
            ['user_name', user],
            ['user_password', password],
            ['form_submitted_marker', ''],
            ['requestedUrl', ''],
            ['Submit', 'Connexion']],
            description="Login " + user)
        fl.assert_('loginFailed=true' not in fl.getLastUrl(),
                   'Login failed for %s:%s' % (user, password))
        fl.assert_('"userMenuActions"' in fl.getBody(),
                   "No user menu found in the welcome page")
        fl.assert_(user in fl.getBody(),
                   "username not found on the page" + user)
        fl.current_login = user
        return FolderPage(self.fl)

    def loginInvalid(self, user, password):
        fl = self.fl
        fl.post(fl.server_url + "/nxstartup.faces", params=[
            ['user_name', user],
            ['user_password', password],
            ['form_submitted_marker', ''],
            ['Submit', 'Connexion']],
            description="Login invalid user " + user)
        fl.assert_('loginFailed=true' in fl.getLastUrl(),
                   'Invalid login expected for %s:%s.'  %  (user, password))
        return self

    def viewDocumentPath(self, path, description=None, raiseOn404=True,
                         tab='', subtab=None, outcome=None):
        """This method return None when raiseOn404 is False and the document
        does not exist"""
        fl = self.fl
        if not description:
            description = "View document path:" + path
        ok_codes = [200, 301, 302, 303, 307]
        if not raiseOn404:
            ok_codes.append(404)
        if not outcome:
            outcome = "view_documents"
        resp = fl.get(fl.server_url + "/nxpath/default/default-domain/" +
                      quote(path) + "@" + outcome + '?conversationId=0NXMAIN1' + getTabParams(tab, subtab),
                      description=description, ok_codes=ok_codes)
        if resp.code == 404:
            fl.logi('Document ' + path + ' does not exists.')
            return None
        return self

    def viewDocumentUid(self, uid, tab=None, subtab=None, description=None,
                        outcome=None):
        fl = self.fl
        if not description:
            description = "View document uid:" + uid + ' ' + getTabParams(tab, subtab)
        if not outcome:
            outcome = "view_documents"

        url = '/nxdoc/default/' + uid + '/' + outcome
        url += '?conversationId=0NXMAIN1' + getTabParams(tab, subtab)
        fl.get(fl.server_url + url,
               description=description)
        return self

    def getRootWorkspaces(self):
        return self.viewDocumentPath("workspaces")

    def getRootSections(self):
        return self.viewDocumentPath("sections")

    def adminCenter(self):
        fl = self.fl
        if "@view_admin" in fl.getLastUrl():
            # already in admin center
            return self
        self.viewDocumentUid(self.getDocUid(), outcome="view_admin",
                             description="Admin center page")
        fl.assert_("adminSelectorForm" in fl.getBody(),
                   "Wrong admin center page")
        return self

    def exitAdminCenter(self):
        fl = self.fl
        if not "@view_admin" in fl.getLastUrl():
            # not in admin center
            return self
        self.viewDocumentUid(self.getDocUid(), outcome="view_document",
                             description="Document Management")
        fl.assert_('id="document_content"' not in fl.getBody(),
                   "Fail to exit admin center")
        return self

    def usersAndGroupsPage(self):
        fl = self.fl
        if not "Exit admin center" in fl.getBody():
            self.adminCenter()
        fl.post(fl.server_url + "/view_admin.faces", params=[
            ['adminSelectorForm', 'adminSelectorForm'],
            ['javax.faces.ViewState', fl.getLastJsfState()],
            ['adminSelectorForm:adminSelectorList:6:item',
             'adminSelectorForm:adminSelectorList:6:item']],
            description="Users and groups page")
        fl.assert_('usersListingView' in fl.getBody(),
                   "Wrong user page")
        return self

    def createUser(self, username, email, password, firstname='',
                   lastname='', company='', groups=''):
        """This method does not raise exception if user already exists"""
        fl = self.fl
        if (not 'createUser:button_save_and_create' in fl.getBody() and not 'createUser:button_save_and_invite' in fl.getBody()):
            fl.assert_('createUserButton' in fl.getBody(),
                       "You should call usersAndGroupsPage first")
            fl.post(fl.server_url + "/view_admin.faces", params=[
                    ['AJAXREQUEST', 'usersListingView:cv_users_listing_search_only__region'],
                    ['usersListingView:createUserActionsForm', 'usersListingView:createUserActionsForm'],
                    ['javax.faces.ViewState', fl.getLastJsfState()],
                    ['usersListingView:createUserActionsForm:createUserButton', 'usersListingView:createUserActionsForm:createUserButton']],
                    description="View user creation form")
            fl.assert_('createUser:immediate_creation' in fl.getBody(),
                   'Wrong page')
	if (not 'createUser:button_save_and_create' in fl.getBody()):
            fl.post(fl.server_url + "/view_admin.faces", params=[
            		['createUserView:createUser:nxl_user_registration:nxw_username', ''],
            		['createUserView:createUser:nxl_user_registration:nxw_firstname', ''],
		        ['createUserView:createUser:nxl_user_registration:nxw_lastname', ''],
		        ['createUserView:createUser:nxl_user_registration:nxw_company', ''],
		        ['createUserView:createUser:nxl_user_registration:nxw_email', ''],
		        ['createUserView:createUser', 'createUserView:createUser'],
		        ['autoScroll', ''],
		        ['javax.faces.ViewState', fl.getLastJsfState()],
            		['createUserView:createUser:immediate_creation','on'],
            		['ajaxSingle', 'createUserView:createUser:immediate_creation'],
            		['AJAX:EVENTS_COUNT', '1']],
            		description="Immediate creation")
	# The assert is removed because the body contains now the Ajax response and not the body of the page so the assert will always fail
        #fl.assert_('createUser:button_save_and_create' in fl.getBody(),
        #           'Wrong page')

        jsfState = fl.getLastJsfState()
        fl.post(fl.server_url + "/site/automation/UserGroup.Suggestion",
                Data('application/json+nxrequest; charset=UTF-8',
                     '{"params":{"prefix":"' + groups + '"},"context":{}}'),
                description="Search group")
        fl.assert_(groups in fl.getBody(), "Group not found")

        # /tmp/tmp2uCzyC_funkload/watch0001.request
        fl.post(fl.server_url + "/view_admin.faces", params=[
            ['AJAXREQUEST', '_viewRoot'],
            ['createUserView:createUser:immediate_creation', 'on'],
            ['createUserView:createUser:nxl_user:nxw_username', username],
            ['createUserView:createUser:nxl_user:nxw_firstname', firstname],
            ['createUserView:createUser:nxl_user:nxw_lastname', lastname],
            ['createUserView:createUser:nxl_user:nxw_company', company],
            ['createUserView:createUser:nxl_user:nxw_email', email],
            ['createUserView:createUser:nxl_user:nxw_firstPassword', password],
            ['createUserView:createUser:nxl_user:nxw_secondPassword', password],
            ['createUserView:createUser:nxl_user:nxw_passwordMatcher', 'needed'],
            # ['createUserView:createUser:nxl_user:nxw_groups_select2_init', '[{"parentGroups":[],"grouplabel":"Members group","label":"Members group","description":"Group of users with read access rights","groupname":"members","subGroups":[],"members":[],"id":"members","type":"group","prefixed_id":"group:members"}]'],
            ['createUserView:createUser:nxl_user:nxw_groups_select2', groups],
            # ['createUserView:createUser:nxl_user:nxw_groups_select2_params', '{"multiple":"true","translateLabels":"true","template":"/select2/select2_multiple_user_widget_template.xhtml","hideVirtualGroups":"true","userSuggestionSearchType":"GROUP_TYPE","inlinejs":"function userformater(entry) {\\n   var markup = \\"<table><tr>\\";\\n   markup += \\"<td><img src=\'/nuxeo/icons/\\" + entry.type + \\".png\'/></td>\\";\\n   markup += \\"<td style=\'padding:2px\'>\\" + entry.label + \\"</td>\\";\\n   markup += \\"</tr></table>\\";\\n   return markup;\\n  }","minimumInputLength":"3","width":"300","searchType":"group","customFormater":"userformater","operationId":"UserGroup.Suggestion","placeholder":"S\xc3\xa9lectionnez une valeur."}'],
            ['createUserView:createUser', 'createUserView:createUser'],
            ['autoScroll', ''],
            ['javax.faces.ViewState', jsfState],
            ['createUserView:createUser:button_save_and_create', 'createUserView:createUser:button_save_and_create'],
            ['AJAX:EVENTS_COUNT', '1']],
            description="Create user")
        
        fl.assert_('User already exists' in fl.getBody() or
                   'User created' in fl.getBody())
        return self

    def dashboard(self):
        """jsf dashboard"""
        fl = self.fl
        self.viewDocumentUid(self.getDocUid(), outcome='user_dashboard',
                             description="View JSF dashboard")
        fl.assert_('My workspaces' in fl.getBody(),
                   'Not a dashboard page')
        return self

    def dashboardNew(self):
        """open social dashboard"""
        fl = self.fl
        server_url = fl.server_url
        fl.post(fl.server_url + "/view_documents.faces", params=[
            ['userServicesForm:userServicesActionsTable:0:userServicesActionCommandLink', 'userServicesForm:userServicesActionsTable:0:userServicesActionCommandLink'],
            ['javax.faces.ViewState', fl.getLastJsfState()],
            ['userServicesForm', 'userServicesForm']],
                description="Dashboard opensocial")

        ts = str(time.time())
        jid = extractJsessionId(fl)
        uid = extractToken(fl.getBody(), """return "{docRef:'""", "'")
        fl.assert_(len(uid) == 36)

        fl.get(server_url + "/org.nuxeo.opensocial.container.ContainerEntryPoint/org.nuxeo.opensocial.container.ContainerEntryPoint.nocache.js",
                 description="Get container entry point")
        data =  Data('text/x-gwt-rpc; charset=utf-8', '''5|0|17|''' + server_url + '''/org.nuxeo.opensocial.container.ContainerEntryPoint/|9CCFB53A0997F1E4596C8EE4765CCBAA|org.nuxeo.opensocial.container.client.service.api.ContainerService|getContainer|java.util.Map|java.util.HashMap/962170901|java.lang.String/2004016611|docRef|''' + uid + '''|clientUrl|''' + server_url + '''/|windowWidth|10|nxBaseUrl|userLanguage|fr|locale|1|2|3|4|1|5|6|6|7|8|7|9|7|10|7|11|7|12|7|13|7|14|-5|7|15|7|16|7|17|-10|''')
        fl.post(server_url + "/gwtcontainer", data,
                description="dashboard gwt container")
        fl.assert_('//OK' in fl.getBody())

        # Extract iframes from the gwtcontainer response
        iframes = extractIframes(fl.getBody())
        fl.assert_(len(iframes))

        i = 0
        for iframe in iframes:
            i += 1
            # print "iframe: " + iframe
            fl.get(server_url + iframe,
                   description="dashboard iframe %d" % i)
            fl.assert_(fl.getBody().startswith('<html>'))

        fl.get(server_url + "/opensocial/gadgets/makeRequest?refresh=3600&url=" + quote_plus(server_url) + "%2FrestAPI%2Fdashboard%2FUSER_SITES%3Fformat%3DJSON%26page%3D0%26domain%3Ddefault-domain%26lang%3Den%26ts%3D12766046361930.9475744903817575&httpMethod=GET&headers=Cache-control%3Dno-cache%252C%2520must-revalidate%26X-NUXEO-INTEGRATED-AUTH%3D" + jid + "&postData=&authz=&st=&contentType=JSON&numEntries=3&getSummaries=false&signOwner=true&signViewer=true&gadget=" + quote_plus(server_url) + "%2Fsite%2Fgadgets%2Fuserdocuments%2Fusersites.xml&container=default&bypassSpecCache=1&nocache=0",
               description="dashboard req1: user sites")
        fl.assert_('USER_SITES' in fl.getBody())

        fl.post(server_url + "/opensocial/gadgets/makeRequest", params=[
            ['authz', ''],
            ['signOwner', 'true'],
            ['contentType', 'JSON'],
            ['nocache', '0'],
            ['postData', ''],
            ['headers', 'Cache-control=no-cache%2C%20must-revalidate&X-NUXEO-INTEGRATED-AUTH=' + jid],
            ['url', server_url + '/restAPI/workflowTasks/default?mytasks=false&format=JSON&ts=' + ts + '&lang=en&labels=workflowDirectiveValidation,workflowDirectiveOpinion,workflowDirectiveVerification,workflowDirectiveCheck,workflowDirectiveDiffusion,label.workflow.task.name,label.workflow.task.duedate,label.workflow.task.directive'],
            ['numEntries', '3'],
            ['bypassSpecCache', '1'],
            ['st', ''],
            ['httpMethod', 'GET'],
            ['signViewer', 'true'],
            ['container', 'default'],
            ['getSummaries', 'false'],
            ['gadget', server_url + '/site/gadgets/waitingfor/waitingfor.xml']],
            description="dashboard req2: other tasks")
        fl.assert_('Tasks for' in fl.getBody())

        fl.get(server_url + "/opensocial/gadgets/makeRequest?refresh=3600&url=" + quote_plus(server_url) + "%2FrestAPI%2Fdashboard%2FUSER_WORKSPACES%3Fformat%3DJSON%26page%3D0%26domain%3Ddefault-domain%26lang%3Den%26ts%3D12766046364186.08350334148753&httpMethod=GET&headers=Cache-control%3Dno-cache%252C%2520must-revalidate%26X-NUXEO-INTEGRATED-AUTH%3DEB4D8F264629C549917996193637A4F4&postData=&authz=&st=&contentType=JSON&numEntries=3&getSummaries=false&signOwner=true&signViewer=true&gadget=" + quote_plus(server_url) + "%2Fsite%2Fgadgets%2Fuserworkspaces%2Fuserworkspaces.xml&container=default&bypassSpecCache=1&nocache=0",
               description="dashboard req3: user workspaces")
        fl.assert_('USER_WORKSPACES' in fl.getBody())

        fl.post(server_url + "/opensocial/gadgets/makeRequest", params=[
            ['authz', ''],
            ['signOwner', 'true'],
            ['contentType', 'JSON'],
            ['nocache', '0'],
            ['postData', ''],
            ['headers', 'Cache-control=no-cache%2C%20must-revalidate&X-NUXEO-INTEGRATED-AUTH=EB4D8F264629C549917996193637A4F4'],
            ['url', server_url + '/restAPI/workflowTasks/default?mytasks=true&format=JSON&ts=' + ts + '&lang=en&labels=workflowDirectiveValidation,workflowDirectiveOpinion,workflowDirectiveVerification,workflowDirectiveCheck,workflowDirectiveDiffusion,label.workflow.task.name,label.workflow.task.duedate,label.workflow.task.directive'],
            ['numEntries', '3'],
            ['bypassSpecCache', '1'],
            ['st', ''],
            ['httpMethod', 'GET'],
            ['signViewer', 'true'],
            ['container', 'default'],
            ['getSummaries', 'false'],
            ['gadget', server_url + '/site/gadgets/tasks/tasks.xml']],
            description="dashboard req4: my tasks")
        fl.assert_('Tasks for' in fl.getBody())

        fl.get(server_url + "/opensocial/gadgets/makeRequest?refresh=3600&url=" + quote_plus(server_url) + "%2FrestAPI%2Fdashboard%2FRELEVANT_DOCUMENTS%3Fformat%3DJSON%26page%3D0%26domain%3Ddefault-domain%26lang%3Den%26ts%3D12766046370131.186666326174645&httpMethod=GET&headers=Cache-control%3Dno-cache%252C%2520must-revalidate%26X-NUXEO-INTEGRATED-AUTH%3DEB4D8F264629C549917996193637A4F4&postData=&authz=&st=&contentType=JSON&numEntries=3&getSummaries=false&signOwner=true&signViewer=true&gadget=" + quote_plus(server_url) + "%2Fsite%2Fgadgets%2Fuserdocuments%2Fuserdocuments.xml&container=default&bypassSpecCache=1&nocache=0",
               description="dashboard req5: relevant docs")
        fl.assert_('RELEVANT_DOCUMENTS' in fl.getBody())
        return self

    def personalWorkspace(self):
        fl = self.fl
        fl.post(fl.server_url + "/view_documents.faces", params=[
                ['userServicesForm', 'userServicesForm'],
                ['javax.faces.ViewState', fl.getLastJsfState()],
                ['userServicesForm:menuActionCommand_SHOW_PERSONAL_WORKSPACE', 'userServicesForm:menuActionCommand_SHOW_PERSONAL_WORKSPACE']],
                description="View personal workspace")
        # XXX: not working: post initializes personal workspace if it does
        # not exist...
        #self.viewDocumentPath("UserWorkspaces/" + fl.current_login,
        #                      description="View personal workspace")
        return self

    def search(self, query, description=None):
        fl = self.fl
        description = description and description or 'Search ' + query

        if '/search_results_simple.faces' in fl.getBody():
            action = '/search/search_results_simple.faces'
        else:
            action = '/view_documents.faces'
        fl.post(fl.server_url + action, params=[
            ['userServicesSearchForm:faceted_search_suggest_box', query],
            ['userServicesSearchForm:faceted_search_suggestionBox_selection', ''],
            ['userServicesSearchForm:simpleSearchSubmitButton', 'Search'],
            ['userServicesSearchForm', 'userServicesSearchForm'],
            ['javax.faces.ViewState', fl.getLastJsfState()],
            ['AJAX:EVENTS_COUNT', '1']],
            description=description)
        # XXX AT: hack for NXP-9930: ajax response => need to force redirect
        fl.get(fl.server_url + "/facetedsearch/faceted_search_results.faces?conversationId=0NXMAIN",
               description="Get redirection to faceted search after " + description)

        fl.assert_('Default search' in fl.getBody(),
                   'Not a search result page')
        return self

    def edit(self):
        ret = self.viewDocumentUid(self.getDocUid(), tab='TAB_EDIT',
                                   description="View edit tab")
        self.fl.assert_('document_edit' in self.fl.getBody())
        return ret

    def files(self):
        ret = self.viewDocumentUid(self.getDocUid(), tab='TAB_FILES_EDIT',
                                   description="View files tab")
        self.fl.assert_('Upload your file' in self.fl.getBody())
        return ret

    def publish(self):
        ret = self.viewDocumentUid(self.getDocUid(), tab='TAB_PUBLISH',
                                   description="View publish tab")
        self.fl.assert_('Sections' in self.fl.getBody())
        return ret

    def publishOnFirstSection(self):
        """Publish in the first section"""
        fl = self.fl
        fl.post(fl.server_url + "/view_documents.faces", params=[
            ['AJAXREQUEST', '_viewRoot'],
            ['publishTreeForm:publishSelectTreeName', 'DefaultSectionsTree-default-domain'],
            ['publishTreeForm:publishTree:input', ''],
            ['publishTreeForm', 'publishTreeForm'],
            ['autoScroll', ''],
            ['javax.faces.ViewState', fl.getLastJsfState()],
            ['publishTreeForm:publishTree:publishRecursiveAdaptor:0::publishTreeNodeAjaxExpanded', 'true'],
            ['publishTreeForm:publishTree:publishRecursiveAdaptor:0::publishTreeNodeNodeExpanded', 'true']],
            description="Publish view section root")

        fl.post(fl.server_url + "/view_documents.faces", params=[
            ['AJAXREQUEST', '_viewRoot'],
            ['publishTreeForm:publishSelectTreeName', 'DefaultSectionsTree-default-domain'],
            ['publishTreeForm:publishTree:input', 'publishTreeForm:publishTree:publishRecursiveAdaptor:0:publishRecursiveAdaptor:0::publishTreeNode'],
            ['publishTreeForm', 'publishTreeForm'],
            ['autoScroll', ''],
            ['javax.faces.ViewState', fl.getLastJsfState()],
            ['publishTreeForm:publishTree:publishRecursiveAdaptor:0:publishRecursiveAdaptor:0::publishCommandLink', 'publishTreeForm:publishTree:publishRecursiveAdaptor:0:publishRecursiveAdaptor:0::publishCommandLink']],
            description="Publish document")

        fl.assert_("Unpublish" in fl.getBody())

        return self

    def relations(self):
        ret = self.viewDocumentUid(self.getDocUid(), tab='TAB_RELATIONS',
                                   description="View relations tab")
        self.fl.assert_('Add a new relation' in self.fl.getBody()
                        or 'No incoming or outgoing relation' in self.fl.getBody())
        return ret

    # NXP-12675/NXP-8924: this tab is now disabled, this method is deprecated
    def mySubscriptions(self):
        ret = self.viewDocumentUid(self.getDocUid(),
                                   tab='TAB_MY_SUBSCRIPTIONS',
                                   description="View my subscriptions tab")
        self.fl.assert_('notifications' in self.fl.getBody())
        return ret

    def manageSubscriptions(self):
        """Only available for manager."""
        ret = self.viewDocumentUid(self.getDocUid(),
                                   tab='TAB_MANAGE_SUBSCRIPTIONS',
                                   description="View manage subscriptions tab")
        self.fl.assert_('add_subscriptions' in self.fl.getBody())
        return ret

    def comments(self):
        ret = self.viewDocumentUid(self.getDocUid(), tab='view_comments',
                                   description="View comments tab")
        self.fl.assert_('Add a comment' in self.fl.getBody())
        return ret

    def history(self):
        ret = self.viewDocumentUid(self.getDocUid(),
                                   tab='TAB_CONTENT_HISTORY',
                                   description="View history tab")
        self.fl.assert_('Event log' in self.fl.getBody())
        return ret

    def manage(self):
        ret = self.viewDocumentUid(self.getDocUid(),
                                   tab='TAB_MANAGE',
                                   description="View manage tab")
        return ret

    def auto_DocumentQuery(self, nxql):
        data = Data('application/json+nxrequest',
                    '{"params":{"query":"' + nxql + '"},"context":{}}')
        self.fl.post(self.fl.server_url + '/site/automation/Document.Query',
                     data)


class LoginPage(BasePage):
    """The Login page."""
    def view(self):
        fl = self.fl
        fl.get(fl.server_url + '/login.jsp',
               description='View Login page')
        fl.assert_('user_password' in fl.getBody())
        return self


class FolderPage(BasePage):
    """Folder page"""

    def createWorkspace(self, title, description):
        fl = self.fl

        fl.post(fl.server_url + "/view_documents.faces", params=[
            ['javax.faces.ViewState', fl.getLastJsfState()],
            ['nxw_newWorkspace_form', 'nxw_newWorkspace_form'],
            ['nxw_newWorkspace_form:nxw_newWorkspace', 'nxw_newWorkspace_form:nxw_newWorkspace']],
            description="Create workspace form")
        fl.assert_('nxw_title' in fl.getBody(),
                   "Workspace creation form not found.")

        fl.post(fl.server_url + "/create_workspace.faces", params=[
            ['document_create', 'document_create'],
            ['document_create:nxl_heading:nxw_title', title],
            ['document_create:nxl_heading:nxw_description', description],
            ['document_create:create_doc_CREATE_WORKSPACE', 'Create'],
            ['document_create', 'document_create'],
            ['javax.faces.ViewState', fl.getLastJsfState()]],
                description="Create workspace submit")
        fl.assert_('Workspace saved' in fl.getBody())
        return self

    def createSection(self, title, description):
        fl = self.fl
        server_url = fl.server_url
        fl.post(server_url + "/view_documents.faces", params=[
            ['javax.faces.ViewState', fl.getLastJsfState()],
            ['nxw_newSection_form', 'nxw_newSection_form'],
            ['nxw_newSection_form:nxw_newSection', 'nxw_newSection_form:nxw_newSection']],
            description="Create a section form")
        fl.assert_('nxw_title' in fl.getBody(),
                   "Section creation form not found.")

        fl.post(server_url + "/create_document.faces", params=[
            ['javax.faces.ViewState', fl.getLastJsfState()],
            ['document_create:nxl_heading:nxw_description', description],
            ['document_create:nxl_heading:nxw_title', title],
            ['document_create:nxw_documentCreateButtons_CREATE_DOCUMENT', 'Create'],
            ['document_create', 'document_create']],
            description="Create a section submit")
        fl.assert_('Section saved' in fl.getBody())
        return self

    def createFolder(self, title, description):
        fl = self.fl

        fl.post(fl.server_url + "/view_documents.faces", params=[
            ['AJAXREQUEST', '_viewRoot'],
            ['nxw_newDocument_form', 'nxw_newDocument_form'],
            ['javax.faces.ViewState', fl.getLastJsfState()],
            ['ajaxSingle', 'nxw_newDocument_form:nxw_documentActionSubviewUpperList_newDocument_subview:nxw_documentActionSubviewUpperList_newDocument_link'],
            ['nxw_newDocument_form:nxw_documentActionSubviewUpperList_newDocument_subview:nxw_documentActionSubviewUpperList_newDocument_link', 'nxw_newDocument_form:nxw_documentActionSubviewUpperList_newDocument_subview:nxw_documentActionSubviewUpperList_newDocument_link'],
            ['AJAX:EVENTS_COUNT', '1']],
            description="Click on 'New' action")
        fl.assert_('Available document types' in fl.getBody())

        fl.post(fl.server_url + "/view_documents.faces", params=[
            ['nxw_documentActionSubviewUpperList_newDocument_fancyform', 'nxw_documentActionSubviewUpperList_newDocument_fancyform'],
            ['javax.faces.ViewState', fl.getLastJsfState()],
            ['nxw_documentActionSubviewUpperList_newDocument_fancyform:selectDocumentTypeForCreationTable:1:selectDocumentTypeForCreationCategory:0:selectDocumentTypeForCreationCategoryTable:1:selectDocumentTypeForCreationCategoryTitleLink', 'nxw_documentActionSubviewUpperList_newDocument_fancyform:selectDocumentTypeForCreationTable:1:selectDocumentTypeForCreationCategory:0:selectDocumentTypeForCreationCategoryTable:1:selectDocumentTypeForCreationCategoryTitleLink']],
            description="Create folder: New Folder")

        fl.assert_('document_create' in fl.getBody(),
                   "Folder form not found")
        fl.post(fl.server_url + "/create_document.faces", params=[
            ['document_create:nxl_heading:nxw_title', title],
            ['document_create:nxl_heading:nxw_description', description],
            #['parentDocumentPath', '/default-domain/workspaces/flnxtest-page-workspace.1237992970017'],
            ['document_create:nxw_documentCreateButtons_CREATE_DOCUMENT', 'Create'],
            ['document_create', 'document_create'],
            ['javax.faces.ViewState', fl.getLastJsfState()]],
            description="Create folder: Submit")
        fl.assert_('Folder saved' in fl.getBody())
        return self

    def createFile(self, title, description, file_path=None):
        fl = self.fl

        fl.post(fl.server_url + "/view_documents.faces", params=[
            ['AJAXREQUEST', '_viewRoot'],
            ['nxw_newDocument_form', 'nxw_newDocument_form'],
            ['javax.faces.ViewState', fl.getLastJsfState()],
            ['ajaxSingle', 'nxw_newDocument_form:nxw_documentActionSubviewUpperList_newDocument_subview:nxw_documentActionSubviewUpperList_newDocument_link'],
            ['nxw_newDocument_form:nxw_documentActionSubviewUpperList_newDocument_subview:nxw_documentActionSubviewUpperList_newDocument_link', 'nxw_newDocument_form:nxw_documentActionSubviewUpperList_newDocument_subview:nxw_documentActionSubviewUpperList_newDocument_link'],
            ['AJAX:EVENTS_COUNT', '1']],
            description="Click on 'New' action")
        fl.assert_('Available document types' in fl.getBody())

        fl.post(fl.server_url + "/view_documents.faces", params=[
            ['nxw_documentActionSubviewUpperList_newDocument_fancyform', 'nxw_documentActionSubviewUpperList_newDocument_fancyform'],
            ['javax.faces.ViewState', fl.getLastJsfState()],
            ['nxw_documentActionSubviewUpperList_newDocument_fancyform:selectDocumentTypeForCreationTable:0:selectDocumentTypeForCreationCategory:0:selectDocumentTypeForCreationCategoryTable:1:selectDocumentTypeForCreationCategoryTitleLink', 'nxw_documentActionSubviewUpperList_newDocument_fancyform:selectDocumentTypeForCreationTable:0:selectDocumentTypeForCreationCategory:0:selectDocumentTypeForCreationCategoryTable:1:selectDocumentTypeForCreationCategoryTitleLink']],
            description="Create file: New document")

        fl.assert_('document_create' in fl.getBody(),
                   "File form not found")
        fl.post(fl.server_url + "/create_document.faces", params=[
            ['document_create:nxl_heading:nxw_title', title],
            ['document_create:nxl_heading:nxw_description', description],
            ['document_create:nxl_file:nxw_file:nxw_file_file:choice',
             file_path and 'upload' or 'none'],
            ['document_create:nxl_file:nxw_file:nxw_file_file:upload',
             Upload(file_path or '')],
            ['document_create:nxw_documentCreateButtons_CREATE_DOCUMENT', 'Create'],
            ['document_create', 'document_create'],
            ['javax.faces.ViewState', fl.getLastJsfState()]],
            description="Create file: Submit")
        fl.assert_('File saved' in fl.getBody())
        return self

    def selectItem(self, title, item_type="Workspace"):
        fl = self.fl
        html = fl.getBody()
        if item_type in ['Section', 'SectionRoot']:
            start = html.find('form id="section_content"')
        else:
            start = html.find('form id="document_content"')
        end = html.find(title, start)
        fl.assert_(end > 0, 'Item with title "%s" not found.' % title)
        start = html.rfind('<tr class', start, end)

        checkbox_id = extractToken(html[start:end], 'input id="', '"')
        fl.assert_(checkbox_id, 'item "%s" not found.' % title)
        checkbox_ajax_onclick_id = checkbox_id + '_ajax_onclick'
        table_name = checkbox_id.split(':', 1)[0]

        params = [
            ['AJAXREQUEST', 'cv_' + table_name + '_0_region'],
            [checkbox_id, 'on'],
            ['javax.faces.ViewState', fl.getLastJsfState()],
            [checkbox_ajax_onclick_id, checkbox_ajax_onclick_id],
            [table_name, table_name]]
        fl.post(fl.server_url + "/view_documents.faces", params,
                description='Select document "%s"' % title)

        return self

    def deleteItem(self, title, item_type="Workspace"):
        fl = self.fl
        state = fl.getLastJsfState()
        self.selectItem(title, item_type)

        table_name = "document_content"
        button_name = "CURRENT_SELECTION_TRASH"
        if item_type in ['Section', 'SectionRoot']:
            table_name = "section_content"
            button_name = "CURRENT_SELECTION_SECTIONS_TRASH"

        form_name = table_name + '_buttons'
        button_id = 'nxw_' + button_name
        button_form = button_id + '_form'

        params = [
            ['javax.faces.ViewState', state],
            [form_name + ':' + button_form + ':' + button_id, 'Delete'],
            [form_name + ':' + button_form, form_name + ':' + button_form]]
        fl.post(fl.server_url + "/view_documents.faces", params,
                description='Delete document "%s"' % title)

        fl.assert_('Document(s) deleted' in fl.getBody())
        return self

    def view(self):
        """Default summary tab."""
        self.viewDocumentUid(self.getDocUid(), tab='')
        return self

    def rights(self):
        """Go to rights tab."""
        self.viewDocumentUid(self.getDocUid(), "TAB_MANAGE", "TAB_RIGHTS")
        self.fl.assert_('Local rights' in self.fl.getBody())
        return self

    def grant(self, permission, user):
        """Grant perm to user."""
        fl = self.fl
        fl.assert_('Local rights' in fl.getBody(),
                   'Current page is not a rights tab.')
        server_url = fl.server_url
        state = fl.getLastJsfState()
        fl.post(server_url + "/site/automation/UserGroup.Suggestion",
                Data('application/json+nxrequest; charset=UTF-8',
                     '{"params":{"prefix":"' + user + '"},"context":{}}'),
                description="Search user")
        fl.assert_(user in fl.getBody(), "User not found")

        fl.post(server_url + "/view_documents.faces", params=[
            ['add_rights_form', 'add_rights_form'],
            ['add_rights_form:nxl_user_group_suggestion:nxw_selection_select2_init', '[{"parentGroups":[],"grouplabel":"Members group","label":"Members group","description":"Group of users with read access rights","groupname":"members","subGroups":[],"members":[],"id":"members","type":"group","prefixed_id":"group:members"}]'],
            ['add_rights_form:nxl_user_group_suggestion:nxw_selection_select2', 'members'],
            ['add_rights_form:nxl_user_group_suggestion:nxw_selection_select2_params', '{"multiple":"true","translateLabels":"true","template":"/select2/select2_multiple_user_widget_template.xhtml","inlinejs":"function userformater(entry) {\\n   var markup = \\"<table><tr>\\";\\n   markup += \\"<td><img src=\'/nuxeo/icons/\\" + entry.type + \\".png\'/></td>\\";\\n   markup += \\"<td style=\'padding:2px\'>\\" + entry.label + \\"</td>\\";\\n   markup += \\"</tr></table>\\";\\n   return markup;\\n  }","placeholder":"Rechercher des utilisateurs ou des groupes","hideInstructionLabel":"true","minimumInputLength":"3","width":"300","customFormater":"userformater","operationId":"UserGroup.Suggestion"}'],
            ['add_rights_form:rights_grant_select', 'Grant'],
            ['add_rights_form:rights_permission_select', permission],
            ['add_rights_form:rights_add_button', 'Add'],
            ['javax.faces.ViewState', state]],
            description="Add permission to user")

        params = [
            ['validate_rights:document_rights_validate_button', 'Save local rights'],
            ['validate_rights', 'validate_rights'],
            ['javax.faces.ViewState', fl.getLastJsfState()]]
        fl.post(server_url + "/view_documents.faces", params,
                description="Grant perm apply")
        fl.assert_('Rights updated' in fl.getBody())
        return self

    def sort(self, column):
        fl = self.fl
        server_url = fl.server_url
        fl.assert_('document_content' in fl.getBody(),
                   'Not a folder listing page.')

        options = {'date': ['document_content:nxl_document_listing_ajax:listing_modification_date_header_sort',
                            'document_content:nxl_document_listing_ajax:listing_modification_date_header_sort'],
                   'author': ['document_content:nxl_document_listing_ajax:listing_author_header_sort',
                              'document_content:nxl_document_listing_ajax:listing_author_header_sort'],
                   'lifecycle': ['document_content:nxl_document_listing_ajax:listing_lifecycle_header_sort',
                                 'document_content:nxl_document_listing_ajax:listing_lifecycle_header_sort'],
                   'title': ['document_content:nxl_document_listing_ajax:listing_title_link_header_sort',
                             'document_content:nxl_document_listing_ajax:listing_title_link_header_sort'],
                   }
        fl.assert_(column in options.keys(), 'Invalid sort column')
        # date
        fl.post(server_url + "/view_documents.faces", params=[
            ['document_content', 'document_content'],
            ['javax.faces.ViewState', fl.getLastJsfState()],
            options[column]],
            description="Sort by " + column)
        return self

    def viewRandomDocument(self, pattern):
        fl = self.fl
        # hack to parse only the table listing instead of the broken html page
        table = extractToken(self.fl.getBody(),
                             '<table class="dataOutput">', '</table')
        self.fl._response.body = table
        hrefs = fl.listHref(content_pattern=pattern,
                            url_pattern='@view_documents')
        fl.assert_(len(hrefs), "No doc found with pattern: " + pattern)
        doc_url = random.choice(hrefs)
        fl.get(doc_url, description="View a random document")
        return DocumentPage(self.fl)

    def driveSynchronizeCurrentDocument(self):
        fl = self.fl
        if 'driveUnsynchronizeCurrentDocument' in fl.getBody():
            # Already sync
            return
        fl.assert_('driveSynchronizeCurrentDocument' in fl.getBody(),
                   "No sync button found")
        fl.post(fl.server_url + "/view_documents.faces", params=[
            ['nxw_documentActionsUpperButtons_1_driveSynchronizeCurrentDocument_form', 'nxw_documentActionsUpperButtons_1_driveSynchronizeCurrentDocument_form'],
            ['javax.faces.ViewState', extractJsfState(fl.getBody())],
            ['nxw_documentActionsUpperButtons_1_driveSynchronizeCurrentDocument_form:nxw_documentActionsUpperButtons_1_driveSynchronizeCurrentDocument', 'nxw_documentActionsUpperButtons_1_driveSynchronizeCurrentDocument_form:nxw_documentActionsUpperButtons_1_driveSynchronizeCurrentDocument']],
            description="Synchronize the current document with Drive")
        fl.assert_('driveUnsynchronizeCurrentDocument' in fl.getBody()
                   or 'currentUserSyncRoots' in fl.getBody(),
                   "Can not synchronize the folder")
        return self

    def driveUnsynchronizeCurrentDocument(self):
        fl = self.fl
        fl.post(fl.server_url + "/view_documents.faces", params=[
            ['nxw_documentActionsUpperButtons_1_driveUnsynchronizeCurrentDocument_form', 'nxw_documentActionsUpperButtons_1_driveUnsynchronizeCurrentDocument_form'],
            ['javax.faces.ViewState', extractJsfState(fl.getBody())],
            ['nxw_documentActionsUpperButtons_1_driveUnsynchronizeCurrentDocument_form:nxw_documentActionsUpperButtons_1_driveUnsynchronizeCurrentDocument', 'nxw_documentActionsUpperButtons_1_driveUnsynchronizeCurrentDocument_form:nxw_documentActionsUpperButtons_1_driveUnsynchronizeCurrentDocument']],
            description="Unsynchronize the current document with Drive")
        fl.assert_('driveSynchronizeCurrentDocument' in fl.getBody())
        return self

    def driveRevokeFirstToken(self):
        fl = self.fl
        fl.post(fl.server_url + "/view_home.faces", params=[
            ['AJAXREQUEST', '_viewRoot'],
            ['currentUserAuthTokenBindings', 'currentUserAuthTokenBindings'],
            ['autoScroll', ''],
            ['javax.faces.ViewState', extractJsfState(fl)],
            ['currentUserAuthTokenBindings:nxl_authTokenBindings_1:nxl_authTokenBindings_1_deleteButton', 'currentUserAuthTokenBindings:nxl_authTokenBindings_1:nxl_authTokenBindings_1_deleteButton'],
            ['AJAX:EVENTS_COUNT', '1']],
            description="Post /nuxeo/view_home.faces")
        return self


class DocumentPage(BasePage):
    """Document page."""
