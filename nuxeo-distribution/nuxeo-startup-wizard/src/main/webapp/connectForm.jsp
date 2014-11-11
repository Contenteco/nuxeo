<%@ include file="includes/header.jsp" %>
<%@page import="org.nuxeo.wizard.helpers.ConnectRegistrationHelper"%>
<%
String cbUrl = (String) request.getAttribute("callBackUrl");

String connectUrl = collector.getConfigurationParam("org.nuxeo.connect.url");
if (connectUrl.equals("")) {
    connectUrl = "https://connect.nuxeo.com/nuxeo/site/";
}
String formUrl = connectUrl + "../../register/#/embedded";
formUrl = formUrl + "?wizardCallbackUrl=" + cbUrl;
formUrl = formUrl + "&pkg=" + ctx.getDistributionKey();

boolean showRegistrationForm = !ctx.isConnectRegistrationDone();

if (ConnectRegistrationHelper.isConnectRegistrationFileAlreadyPresent(ctx)) {
    showRegistrationForm = false;
}

%>
<script>
var connectFormLoaded=false;
function setSize() {
 $('#connectForm').css('height','600px');
 $('#connectForm').css('display','block');
 connectFormLoaded=true;
}

function handleFallbackIfNeeded() {
  if(!connectFormLoaded) {
  $('#fallback').css('display','block');
  }
}

window.setTimeout(handleFallbackIfNeeded, 25000);

</script>

<% if (showRegistrationForm) { %>

<iframe id="connectForm" src="<%=formUrl%>" onload="setSize()" width="100%" marginwidth="0" marginheight="0" frameborder="0" vspace="0" hspace="0" style="overflow:auto; width:100%; display:none"></iframe>

<div style="display:none" id="fallback">

<p>
<fmt:message key="label.connectForm.loadError1" />
</p>
<p>
<fmt:message key="label.connectForm.loadError2" />
</p>
<input type="button" id="btnNext" class="glossyButton" value="<fmt:message key="label.action.next"/>" onclick="navigateTo('<%=currentPage.next().next().getAction()%>');"/>

</div>

<% } else { %>

<h1> <fmt:message key="label.connectFinish.ok" /> </h1>
<div class="formPadding">
<fmt:message key="label.connectFinish.ok.details" />
<%@ include file="includes/prevnext.jsp" %>

<script>
  window.setTimeout(function() {navigateTo('<%=currentPage.next().getAction()%>')}, 3000);
</script>
<%} %>


<%@ include file="includes/footer.jsp" %>
