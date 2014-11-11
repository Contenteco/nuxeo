<@extends src="base.ftl">

<@block name="header_scripts">
<script>
function closePopup() {
 self.close();
}
</script>
</@block>

<@block name="body">
  <div class="successfulDownloadBox">
   <h3> Installation of ${pkg.title} (${pkg.id}) completed </h3>

    <#if installTask.isRestartRequired()>
     <div>
         You will need to restart your server to complete the installation.
         <br/>
         <form method="GET" action="${Root.path}/restartView">
         Click on the restart button to restart the server now : <input type="submit" value="Restart"/>
         </form>.
     </div>
    </#if>

    <br/>
    <#if source=="installer">
      <a href="javascript:closePopup()" class="button installButton"> Finish </a>
    <#else>
      <a href="${Root.path}/packages/${source}" class="button installButton"> Finish </a>
    </#if>
  </div>
</@block>
</@extends>