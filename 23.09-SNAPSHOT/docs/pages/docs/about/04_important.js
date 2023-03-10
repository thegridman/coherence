<doc-view>

<h2 id="_java_development_kit_requirements">Java Development Kit Requirements</h2>
<div class="section">
<p>Coherence CE 23.09 requires a minimum of version
17 of the Java Development Kit (JDK).</p>


<h3 id="_additional_jdk_command_line_arguments">Additional JDK Command Line Arguments</h3>
<div class="section">
<p>If using Oracle Coherence Portable Object Format (POF) and/or remote Lambdas,
it is recommended to start the JVM with:</p>

<div class="listing">
<pre>--add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED</pre>
</div>

</div>
</div>

<h2 id="_jakarta_ee_9_1_compatibility">Jakarta EE 9.1 Compatibility</h2>
<div class="section">
<p>Coherence CE 23.09 has migrated to Jakarta EE 9.1 from Java EE 8, importing types in <strong>jakarta</strong> packages instead of <strong>javax</strong> packages.
The following table describes the mapping of javax packages to jakarta packages and Maven artifacts in Coherence CE 23.09.</p>


<div class="table__overflow elevation-1  ">
<table class="datatable table">
<colgroup>
<col style="width: 20%;">
<col style="width: 20%;">
<col style="width: 20%;">
<col style="width: 20%;">
<col style="width: 20%;">
</colgroup>
<thead>
<tr>
<th>'javax' Package</th>
<th>'jakarta' Package</th>
<th>Maven Group ID</th>
<th>Maven Artifact ID</th>
<th>Version</th>
</tr>
</thead>
<tbody>
<tr>
<td class="">javax.activation</td>
<td class="">jakarta.activation</td>
<td class="">jakarta.activation</td>
<td class="">jakarta.activation-api</td>
<td class="">2.0.1</td>
</tr>
<tr>
<td class="">javax.annotation</td>
<td class="">jakarta.annotation</td>
<td class="">jakarta.annotation</td>
<td class="">jakarta.annotation-api</td>
<td class="">2.0.0</td>
</tr>
<tr>
<td class="">javax.enterprise</td>
<td class="">jakarta.enterprise</td>
<td class="">jakarta.enterprise</td>
<td class="">jakarta.enterprise.cdi-api</td>
<td class="">3.0.0</td>
</tr>
<tr>
<td class="">javax.inject</td>
<td class="">jakarta.inject</td>
<td class="">jakarta.inject</td>
<td class="">jakarta.inject-api</td>
<td class="">2.0.1</td>
</tr>
<tr>
<td class="">javax.interceptor</td>
<td class="">jakarta.interceptor</td>
<td class="">jakarta.interceptor</td>
<td class="">jakarta.interceptor-api</td>
<td class="">2.0.0</td>
</tr>
<tr>
<td class="">javax.jms</td>
<td class="">jakarta.jms</td>
<td class="">jakarta.jms</td>
<td class="">jakarta.jms-api</td>
<td class="">3.0.0</td>
</tr>
<tr>
<td class="">javax.json</td>
<td class="">jakarta.json</td>
<td class="">jakarta.json</td>
<td class="">jakarta.json-api</td>
<td class="">2.0.2</td>
</tr>
<tr>
<td class="">javax.json.bind</td>
<td class="">jakarta.json.bind</td>
<td class="">jakarta.json.bind</td>
<td class="">jakarta.json.bind-api</td>
<td class="">2.0.0</td>
</tr>
<tr>
<td class="">javax.resource</td>
<td class="">jakarta.resource</td>
<td class="">jakarta.resource</td>
<td class="">jakarta.resource-api</td>
<td class="">2.0.0</td>
</tr>
<tr>
<td class="">javax.ws.rs</td>
<td class="">jakarta.ws.rs</td>
<td class="">jakarta.ws.rs</td>
<td class="">jakarta.ws.rs-api</td>
<td class="">3.0.0</td>
</tr>
<tr>
<td class="">javax.xml.bind</td>
<td class="">jakarta.xml.bind</td>
<td class="">jakarta.xml.bind</td>
<td class="">jakarta.xml.bind-api</td>
<td class="">3.0.1</td>
</tr>
</tbody>
</table>
</div>
<p>We&#8217;ve updated our Coherence CE examples to use the jakarta packages where relevant.
These examples still hold for older versions of Coherence CE; in these cases
developers will need to change from <strong>jakarta</strong> to <strong>javax</strong>.</p>

<p>In addition to these standard APIs being migrated, we&#8217;ve also updated
some of our major dependent libraries that have undertaken this migration as well.
Most notably:</p>


<div class="table__overflow elevation-1  ">
<table class="datatable table">
<colgroup>
<col style="width: 50%;">
<col style="width: 50%;">
</colgroup>
<thead>
<tr>
<th>Library</th>
<th>Version</th>
</tr>
</thead>
<tbody>
<tr>
<td class="">Helidon</td>
<td class="">3.0.0</td>
</tr>
<tr>
<td class="">Jersey</td>
<td class="">3.0.5</td>
</tr>
<tr>
<td class="">Jackson</td>
<td class="">2.13.3</td>
</tr>
<tr>
<td class="">Jackson DataBind</td>
<td class="">2.13.3</td>
</tr>
<tr>
<td class="">Weld</td>
<td class="">4.0.3.Final</td>
</tr>
<tr>
<td class="">JAXB Core</td>
<td class="">3.0.2</td>
</tr>
<tr>
<td class="">JAXB Implementation</td>
<td class="">3.0.2</td>
</tr>
<tr>
<td class="">Eclipse MP Config</td>
<td class="">3.0.1</td>
</tr>
<tr>
<td class="">Eclipse MP Metrics</td>
<td class="">4.0</td>
</tr>
</tbody>
</table>
</div>
<div class="admonition note">
<p class="admonition-textlabel">Note</p>
<p ><p>If using the older <code>jackson-rs-base</code> and <code>jackson-jaxrs-json-provider</code> libraries,
it will be necessary to migrate to the 'jakarta' versions.  The Maven
groupId for the 'jakarta' versions is <code>com.fasterxml.jackson.jakarta.rs</code>
with the artifactIds being <code>jackson-jakarta-rs-base</code> and <code>jackson-jakarta-rs-json-provider</code>,
respectively.</p>
</p>
</div>
<div class="admonition note">
<p class="admonition-textlabel">Note</p>
<p ><p>If using the older <code>jackson-module-jaxb-annotations</code> library,
it will be necessary to migrate to the <code>jakarta</code> versions.  The maven
groupId for the 'jakarta' version remains the same (<code>com.fasterxml.jackson.module</code>),
however the artifactId should now be <code>jackson-module-jakarta-xmlbind-annotations</code></p>
</p>
</div>
</div>

<h2 id="_deprecated_code_removal">Deprecated Code Removal</h2>
<div class="section">
<p>The following deprecated packages have been removed from this release:</p>

<ul class="ulist">
<li>
<p>com.oracle.datagrid.persistence</p>

</li>
<li>
<p>com.tangosol.persistence</p>

</li>
<li>
<p>com.oracle.common.base (NOTE: these classes are now in com.oracle.coherence.common.base)</p>

</li>
</ul>
</div>
</doc-view>
