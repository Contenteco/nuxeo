<html>
<head>
<title> Nuxeo Server restarting </title>
<link rel="icon" type="image/png" href="${contextPath}/icons/favicon.png" />
<script type="text/javascript" src="${skinPath}/script/jquery/jquery.js"></script>
<style>
html, body {
  border:0 none;
  font-family: Verdana;
  margin:0;
  padding:0;
  color:#333;
}
</style>
</head>
<body>
<br/><br/>
<center>
<div id="loading"><h1>${nuxeoctl.restartServer()}</h1><br/><br/>
<img src="${Context.getServerURL().toString()}${contextPath}/img/big_loading.gif" /></div>
<br/><br/>
You will be automatically redirected to the login page when Nuxeo server is back online.<br>
(please, do not refresh this page)
</center>

<script type="text/javascript">
<!--
// be sure Ajax Requests will timeout quickly
$.ajaxSetup( {
  timeout: 8000
} );

// start polling after 15s to be sure the server begun the restart
setTimeout(startPolling, 15000);

// polls until login page is available again
function startPolling() {
  var intId = setInterval(function isNuxeoReady() {
    var sc = $("#reloadPage");
    if (sc) sc.remove();
    sc = $("<script></script>");
    sc.attr("id","reloadPage");
    sc.attr("src","${nuxeoctl.getServerURL()}/runningstatus?info=reload");
    $("body").append(sc);
  }, 10000);
}

function reload() {
  window.location.href='${nuxeoctl.getServerURL()}/';
}
-->
</script>

</body>
</html>