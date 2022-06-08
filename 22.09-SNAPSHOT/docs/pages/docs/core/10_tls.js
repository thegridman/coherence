<doc-view>

<h2 id="_coherence_tls_enhancements">Coherence TLS Enhancements</h2>
<div class="section">
<p>Coherence has supported using SSL/TLS to secure communication between cluster members and Extend client for a long time.
From Coherence version 22.06, this functionality has been enhanced to allow a more flexible configuration and allow customisation via extensions.</p>

<div class="admonition note">
<p class="admonition-textlabel">Note</p>
<p ><p>The documentation here only covers the changes made in Coherence 22.06 that apply to
<a id="" title="" target="_blank" href="https://docs.oracle.com/en/middleware/standalone/coherence/14.1.1.0/secure/using-ssl-secure-communication.html#GUID-90E20139-3945-4993-9048-7FBC93B243A3">6 Using SSL to Secure Communication</a> in the commercial documentation on <em>Securing Oracle Coherence</em></p>
</p>
</div>
<ul class="ulist">
<li>
<p>The <router-link to="#passwords" @click.native="this.scrollFix('#passwords')">Encrypting SSL Passwords</router-link> section covers a new configuration option to read a password from a file or simple URL.</p>

</li>
<li>
<p>The <router-link to="#keys-and-certs" @click.native="this.scrollFix('#keys-and-certs')">Using Private Key and Certificate Files</router-link> section describes using private key and certificate files instead of using Java keystores.</p>

</li>
<li>
<p>The <router-link to="#custom-loaders" @click.native="this.scrollFix('#custom-loaders')">Custom Keystore, Private Key and Certificate Loaders</router-link> section describes loading keystores, private keys and certificates from custom sources and formats instead of from files.</p>

</li>
<li>
<p>The <router-link to="#refresh" @click.native="this.scrollFix('#refresh')">Refreshable KeyStores, Private Keys and Certificates</router-link> section shows how to configure automatic refresh of keystores, private keys and certificates.</p>

</li>
<li>
<p>The <router-link to="#operational" @click.native="this.scrollFix('#operational')">Operational Configuration Element Reference</router-link> section details changes to the
<a id="" title="" target="_blank" href="https://docs.oracle.com/en/middleware/standalone/coherence/14.1.1.0/develop-applications/operational-configuration-elements.html#GUID-6FD7679F-C4A3-4B74-8B81-8C5B7929BC18">Operational Configuration Element Reference</a> documentation based on the SSL configuration changes introduced in Coherence 22.06.</p>

</li>
</ul>
</div>

<h2 id="_6_using_ssl_to_secure_communication">6 Using SSL to Secure Communication</h2>
<div class="section">
<p>Oracle Coherence supports Secure Sockets Layer (SSL) to secure TCMP communication between cluster nodes and to secure the TCP communication between Oracle Coherence*Extend clients and proxies. Oracle Coherence supports the Transport Layer Security (TLS) protocol, which superseded the SSL protocol; however, the term SSL is used in this documentation because it is the more widely recognized term.</p>

</div>

<h2 id="passwords">Encrypting SSL Passwords</h2>
<div class="section">
<p>Entering passwords in an operational override file as clear text is not recommended beyond simple development and testing scenarios. Exposed passwords are a security risk and can lead to unwanted access to sensitive data. Coherence offers two alternatives to hard coded passwords,</p>

<ul class="ulist">
<li>
<p><router-link to="#password-url" @click.native="this.scrollFix('#password-url')">Read a password from a URL</router-link></p>

</li>
<li>
<p><router-link to="#password-provider" @click.native="this.scrollFix('#password-provider')">Use a custom Password Provider</router-link></p>

</li>
</ul>

<h3 id="password-url">Read Passwords from a URL</h3>
<div class="section">
<p>To load a password from a URL, such as a file on the file system, the <code>&lt;password-url&gt;</code> element can be used instead of the <code>&lt;password&gt;</code> element.</p>

<p><router-link to="#example-6-6" @click.native="this.scrollFix('#example-6-6')">Example 6-6</router-link> demonstrates an SSL socket provider configuration that reads the keystore and private key passwords from files on the file system.</p>

<p>In the example XML, the identity manager&#8217;s keystore password is read from the <code>/coherence/security/server-pass.txt</code> file.
The private key used by the identity manager is read from the <code>/coherence/security/key-pass.txt</code> file.
The keystore password used by the trust manager is read from the <code>/coherence/security/trust-pass.txt</code> file.</p>

</div>

<h3 id="example-6-6"><em>Example 6-6 Reading a Password From a File in the Configuration for SSL</em></h3>
<div class="section">
<markup
lang="xml"

>&lt;?xml version='1.0'?&gt;
&lt;coherence xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
   xmlns="http://xmlns.oracle.com/coherence/coherence-operational-config"
   xsi:schemaLocation="http://xmlns.oracle.com/coherence/coherence-operational-config
   coherence-operational-config.xsd"&gt;
   &lt;cluster-config&gt;
      &lt;socket-providers&gt;
         &lt;socket-provider id="mySSLConfig"&gt;
            &lt;ssl&gt;
               &lt;protocol&gt;TLS&lt;/protocol&gt;
               &lt;identity-manager&gt;
                  &lt;algorithm&gt;SunX509&lt;/algorithm&gt;
                  &lt;key-store&gt;
                     &lt;url&gt;file:server.jks&lt;/url&gt;
                     &lt;password-url&gt;file:/coherence/security/server-pass.txt&lt;/password-url&gt;
                     &lt;type&gt;JKS&lt;/type&gt;
                  &lt;/key-store&gt;
                  &lt;password-url&gt;file:/coherence/security/key-pass.txt&lt;/password-url&gt;
               &lt;/identity-manager&gt;
               &lt;trust-manager&gt;
                  &lt;algorithm&gt;SunX509&lt;/algorithm&gt;
                  &lt;key-store&gt;
                     &lt;url&gt;file:trust.jks&lt;/url&gt;
                     &lt;password-url&gt;file:/coherence/security/trust-pass.txt&lt;/password-url&gt;
                     &lt;type&gt;JKS&lt;/type&gt;
                  &lt;/key-store&gt;
               &lt;/trust-manager&gt;
               &lt;socket-provider&gt;tcp&lt;/socket-provider&gt;
            &lt;/ssl&gt;
         &lt;/socket-provider&gt;
      &lt;/socket-providers&gt;
   &lt;/cluster-config&gt;
&lt;/coherence&gt;</markup>

<p>By default, the <code>&lt;password-url&gt;</code> configuration will use all the data returned from the URL as the password.
This behaviour can be changed to only use the first line of data returned as the password by setting the <code>first-line-only</code> attribute of the <code>&lt;password-url&gt;</code> element to <code>true</code>.</p>

<p>For example, if the file <code>/secret.txt</code> contained the password followed by additional data on subsequent lines, the <code>&lt;password-url&gt;</code> element could be configured as shown below:</p>

<markup
lang="xml"

>&lt;password-url first-line-only="true"&gt;file:/secret.txt&lt;/password-url&gt;</markup>

</div>

<h3 id="password-provider">Custom Password Providers</h3>
<div class="section">
<p>Password providers allow you to obtain the SSL passwords from any source, including using encryption.</p>

<p>Password providers implement the <code>com.tangosol.net.PasswordProvider</code> interface. The class has a get method that returns a password for use in an SSL configuration. You can create your own password provider, or you can use the predefined <code>com.tangosol.coherence.config.xml.processor.PasswordProviderBuilderProcessor$DefaultPasswordProvider</code> class. The predefined password provider takes a password of type <code>string</code> and returns a password of type <code>char[]</code>.</p>

<p>Define password providers in an operational override file by overriding the <code>&lt;password-providers&gt;</code> element within the <code>&lt;cluster-config&gt;</code> element. The preferred approach is to use the <code>&lt;password-provider&gt;</code> element in an SSL configuration to reference a password provider that is defined within a <code>&lt;password-providers&gt;</code> node. However, the <code>&lt;password-provider&gt;</code> element can also be defined in-line when configuring a password for an SSL socket provider. Both approaches are demonstrated in this section.
See <a id="" title="" target="_blank" href="https://docs.oracle.com/pls/topic/lookup?ctx=en/middleware/standalone/coherence/14.1.1.0/secure&amp;id=COHDG-GUID-C4F23EE0-5EBF-49E4-A735-7EF41A53CD9D">password-provider</a> in <em>Developing Applications with Oracle Coherence</em>.</p>

<p><router-link to="#example-6-7" @click.native="this.scrollFix('#example-6-7')">Example 6-7</router-link> demonstrates an SSL socket provider configuration that references a password provider that is named <code>MyPasswordProvider</code> and is defined within the <code>&lt;password-providers&gt;</code> element. The password provider is used to access the identity manager private key and keystore and the trust manager keystore.</p>

</div>

<h3 id="example-6-7"><em>Example 6-7 Sample Password Provider Configuration for SSL</em></h3>
<div class="section">
<markup
lang="xml"

>&lt;?xml version='1.0'?&gt;
&lt;coherence xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
   xmlns="http://xmlns.oracle.com/coherence/coherence-operational-config"
   xsi:schemaLocation="http://xmlns.oracle.com/coherence/coherence-operational-config
   coherence-operational-config.xsd"&gt;
   &lt;cluster-config&gt;
      &lt;socket-providers&gt;
         &lt;socket-provider id="mySSLConfig"&gt;
            &lt;ssl&gt;
               &lt;protocol&gt;TLS&lt;/protocol&gt;
               &lt;identity-manager&gt;
                  &lt;algorithm&gt;SunX509&lt;/algorithm&gt;
                  &lt;key-store&gt;
                     &lt;url&gt;file:server.jks&lt;/url&gt;
                     &lt;password-provider&gt;
                        &lt;name&gt;MyPasswordProvider&lt;/name&gt;
                        &lt;init-params&gt;
                           &lt;init-param&gt;
                              &lt;param-name&gt;param_1&lt;/param-name&gt;
                              &lt;param-value&gt;private&lt;/param-value&gt;
                           &lt;/init-param&gt;
                        &lt;/init-params&gt;
                     &lt;/password-provider&gt;
                     &lt;type&gt;JKS&lt;/type&gt;
                  &lt;/key-store&gt;
                  &lt;password-provider&gt;
                     &lt;name&gt;MyPasswordProvider&lt;/name&gt;
                     &lt;init-params&gt;
                        &lt;init-param&gt;
                           &lt;param-name&gt;param_1&lt;/param-name&gt;
                           &lt;param-value&gt;private&lt;/param-value&gt;
                        &lt;/init-param&gt;
                     &lt;/init-params&gt;
                  &lt;/password-provider&gt;
               &lt;/identity-manager&gt;
               &lt;trust-manager&gt;
                  &lt;algorithm&gt;SunX509&lt;/algorithm&gt;
                  &lt;key-store&gt;
                     &lt;url&gt;file:trust.jks&lt;/url&gt;
                     &lt;password-provider&gt;
                        &lt;name&gt;MyPasswordProvider&lt;/name&gt;
                        &lt;init-params&gt;
                           &lt;init-param&gt;
                              &lt;param-name&gt;param_1&lt;/param-name&gt;
                              &lt;param-value&gt;private&lt;/param-value&gt;
                           &lt;/init-param&gt;
                        &lt;/init-params&gt;
                     &lt;/password-provider&gt;
                     &lt;type&gt;JKS&lt;/type&gt;
                  &lt;/key-store&gt;
               &lt;/trust-manager&gt;
               &lt;socket-provider&gt;tcp&lt;/socket-provider&gt;
            &lt;/ssl&gt;
         &lt;/socket-provider&gt;
      &lt;/socket-providers&gt;

      &lt;password-providers&gt;
          &lt;password-provider id="MyPasswordProvider"&gt;
              &lt;class-name&gt;package.MyPasswordProvider&lt;/class-name&gt;
              &lt;init-params&gt;
                  &lt;init-param&gt;
                      &lt;param-name&gt;param_1&lt;/param-name&gt;
                      &lt;param-value&gt;password&lt;/param-value&gt;
                  &lt;/init-param&gt;
              &lt;/init-params&gt;
          &lt;/password-provider&gt;
      &lt;password-providers&gt;
   &lt;/cluster-config&gt;
&lt;/coherence&gt;</markup>

<p>As an alternative, you can define a password provider in-line within an SSL socket provider configuration as shown
in <router-link to="#example-6-8" @click.native="this.scrollFix('#example-6-8')">Example 6-8</router-link>:</p>

</div>

<h3 id="example-6-8"><em>Example 6-8 Sample In-line Password Provider Configuration for SSL</em></h3>
<div class="section">
<markup
lang="xml"

>&lt;?xml version='1.0'?&gt;
&lt;coherence xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
   xmlns="http://xmlns.oracle.com/coherence/coherence-operational-config"
   xsi:schemaLocation="http://xmlns.oracle.com/coherence/coherence-operational-config
   coherence-operational-config.xsd"&gt;
   &lt;cluster-config&gt;
      &lt;socket-providers&gt;
         &lt;socket-provider id="mySSLConfig"&gt;
            &lt;ssl&gt;
               &lt;protocol&gt;TLS&lt;/protocol&gt;
               &lt;identity-manager&gt;
                  &lt;algorithm&gt;SunX509&lt;/algorithm&gt;
                  &lt;key-store&gt;
                     &lt;url&gt;file:server.jks&lt;/url&gt;
                     &lt;password-provider&gt;
                        &lt;class-name&gt;package.MyPasswordProvider&lt;/class-name&gt;
                        &lt;init-params&gt;
                           &lt;init-param&gt;
                              &lt;param-name&gt;param_1&lt;/param-name&gt;
                              &lt;param-value&gt;password&lt;/param-value&gt;
                           &lt;/init-param&gt;
                        &lt;/init-params&gt;
                     &lt;/password-provider&gt;
                     &lt;type&gt;JKS&lt;/type&gt;
                  &lt;/key-store&gt;
                  &lt;password-provider&gt;
                     &lt;class-name&gt;package.MyPasswordProvider&lt;/class-name&gt;
                     &lt;init-params&gt;
                        &lt;init-param&gt;
                           &lt;param-name&gt;param_1&lt;/param-name&gt;
                           &lt;param-value&gt;password&lt;/param-value&gt;
                        &lt;/init-param&gt;
                     &lt;/init-params&gt;
                  &lt;/password-provider&gt;
               &lt;/identity-manager&gt;
               &lt;trust-manager&gt;
                  &lt;algorithm&gt;SunX509&lt;/algorithm&gt;
                  &lt;key-store&gt;
                     &lt;url&gt;file:trust.jks&lt;/url&gt;
                     &lt;password-provider&gt;
                        &lt;class-name&gt;package.MyPasswordProvider&lt;/class-name&gt;
                        &lt;init-params&gt;
                           &lt;init-param&gt;
                              &lt;param-name&gt;param_1&lt;/param-name&gt;
                              &lt;param-value&gt;password&lt;/param-value&gt;
                           &lt;/init-param&gt;
                        &lt;/init-params&gt;
                     &lt;/password-provider&gt;
                     &lt;type&gt;JKS&lt;/type&gt;
                  &lt;/key-store&gt;
               &lt;/trust-manager&gt;
               &lt;socket-provider&gt;tcp&lt;/socket-provider&gt;
            &lt;/ssl&gt;
         &lt;/socket-provider&gt;
      &lt;/socket-providers&gt;
   &lt;/cluster-config&gt;
&lt;/coherence&gt;</markup>

</div>
</div>

<h2 id="keys-and-certs">Using Private Key and Certificate Files</h2>
<div class="section">
<p>The examples in the previous sections used Java keystore files to store the private key and certificates used to establish trust and identity in Coherence SSL. Coherence also supports using private key and certificate files directly, instead of loading them into a keystore.</p>

<div class="admonition note">
<p class="admonition-textlabel">Note</p>
<p ><p>Out of the box, Coherence only supports file formats supported by the JDK. This is private key files in PEM format (i.e. a file with a header of <code>-----BEGIN RSA PRIVATE KEY-----</code> or <code>-----BEGIN ENCRYPTED PRIVATE KEY-----</code>) and X509 certificate files (i.e. a file with a header of <code>-----BEGIN CERTIFICATE-----</code>). Alternative formats can be read using custom loaders, see <router-link to="#custom-loaders" @click.native="this.scrollFix('#custom-loaders')">Custom Keystore, Private Key and Certificate Loaders</router-link></p>
</p>
</div>

<h3 id="_configuring_an_identity_manager">Configuring an Identity Manager</h3>
<div class="section">
<p>When configuring an <code>&lt;identity-manager&gt;</code> element of a socket provider, instead of the <code>&lt;keystore&gt;</code> element, the <code>&lt;key&gt;</code> and <code>&lt;cert&gt;</code> elements can be used to supply the private key a certificate file locations.
The value for both the <code>&lt;key&gt;</code> and <code>&lt;cert&gt;</code> element is a URL from which to load the key or certificate data.</p>

<p>&lt;&lt;example-6-9,Example 6-9] shows an <code>&lt;identity-manager&gt;</code> configuration that uses a private key loaded from the <code>/coherence/security/client.pem</code> file and a certificate loaded from the <code>/coherence/security/client.cert</code> file.</p>

</div>

<h3 id="example-6-9"><em>Example 6-9 Sample Identity Manager using a Private Key and Certificate File</em></h3>
<div class="section">
<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;identity-manager&gt;
      &lt;key&gt;file:/coherence/security/client.pem&lt;/key&gt;
      &lt;cert&gt;file:/coherence/security/client.cert&lt;/cert&gt;
    &lt;/identity-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

<p>When configuring an <code>&lt;identity-manager&gt;</code> element, the <code>&lt;keystore&gt;</code> element and the <code>&lt;key&gt;</code> and <code>&lt;cert&gt;</code> elements are mutually exclusive; either configure a keystore, or a key and certificate. The Coherence operational configuration XSD validation will not allow both.</p>

</div>

<h3 id="_configuring_a_trust_manager">Configuring a Trust Manager</h3>
<div class="section">
<p>When configuring an <code>&lt;trust-manager&gt;</code> element of a socket provider, instead of the <code>&lt;keystore&gt;</code> element, one or more <code>&lt;cert&gt;</code> elements can be used to supply the certificate file locations.
The value for the <code>&lt;cert&gt;</code> element is a URL from which to load the certificate data.</p>

<p>&lt;&lt;example-6-9,Example 6-10] shows a <code>&lt;trust-manager&gt;</code> configuration that uses a certificate loaded from the <code>/coherence/security/server-ca.cert</code> file.</p>

</div>

<h3 id="example-6-10"><em>Example 6-10 Sample Trust Manager using a Certificate File</em></h3>
<div class="section">
<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;trust-manager&gt;
      &lt;cert&gt;file:/coherence/security/server-ca.cert&lt;/cert&gt;
    &lt;/trust-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

<p>When configuring an <code>&lt;trust-manager&gt;</code> element, the <code>&lt;keystore&gt;</code> element and the <code>&lt;cert&gt;</code> elements are mutually exclusive; either configure a keystore, or one or more certificates. The Coherence operational configuration XSD validation will not allow both.</p>

</div>
</div>

<h2 id="custom-loaders">Custom Keystore, Private Key and Certificate Loaders</h2>
<div class="section">
<p>To support loading keystores, private keys and certificates from sources other than simple URLS or files, and to read different data formats, Coherence provides a way to configure custom loaders to read the required data from whatever external source is required. For example in the cloud, keys and certificates can be stored in a secrets service and loaded directly from secrets instead of files.
The <a id="" title="" target="_blank" href="https://github.com/oracle/coherence-oci">Coherence OCI</a> project on GitHub includes custom keystore, key and certificate loaders that can read data from secrets in the Oracle Cloud (OCI) Secrets Service.</p>


<h3 id="_custom_keystore_loader">Custom KeyStore Loader</h3>
<div class="section">
<p>If using Java Keystores, an instance of a <code>com.tangosol.net.ssl.KeyStoreLoader</code> can be implemented in application code and configured in the <code>&lt;key-store-loader&gt;</code> element, which is a child of the <code>&lt;key-store&gt;</code> element.
This class can load the contents of a Java KeyStore from any desired location.</p>

<p><router-link to="#example-6-11" @click.native="this.scrollFix('#example-6-11')">Example 6-11</router-link> Shows a custom implementation of the <code>KeyStoreLoader</code> interface.</p>


<h4 id="example-6-11"><em>Example 6-11 A Custom KeyStore Loader Class</em></h4>
<div class="section">
<markup
lang="java"
title="CustomKeyStoreLoader.java"
>package com.acme.coherence;

import com.tangosol.net.ssl.KeyStoreLoader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

public class CustomKeyStoreLoader
        implements KeyStoreLoader
    {
    @Override
    public KeyStore load(String sType, PasswordProvider password)
            throws GeneralSecurityException, IOException
        {
        // return a KeyStore of the required type
        }
    }</markup>

<p><router-link to="#example-6-12" @click.native="this.scrollFix('#example-6-12')">Example 6-12</router-link> shows how the <code>CustomKeyStoreLoader</code> class can be used in an <code>&lt;identity-manager&gt;</code> configuration.</p>

</div>

<h4 id="example-6-12"><em>Example 6-12 Configure an Identity Manager with a Custom KeyStore Loader Class</em></h4>
<div class="section">
<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;identity-manager&gt;
      &lt;key-store&gt;
        &lt;key-store-loader&gt;
          &lt;class-name&gt;com.acme.coherence.CustomKeyStoreLoader&lt;/class-name&gt;
        &lt;/key-store-loader&gt;
      &lt;/key-store&gt;
    &lt;/identity-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

<p><router-link to="#example-6-13" @click.native="this.scrollFix('#example-6-13')">Example 6-13</router-link> shows how the <code>CustomKeyStoreLoader</code> class can be used in an <code>&lt;trust-manager&gt;</code> configuration.</p>

</div>

<h4 id="example-6-13"><em>Example 6-13 Configure an Identity Manager with a Custom KeyStore Loader Class</em></h4>
<div class="section">
<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;trust-manager&gt;
      &lt;key-store&gt;
        &lt;key-store-loader&gt;
          &lt;class-name&gt;com.acme.coherence.CustomKeyStoreLoader&lt;/class-name&gt;
        &lt;/key-store-loader&gt;
      &lt;/key-store&gt;
    &lt;/trust-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

<p>As with other extension points in Coherence, the <code>&lt;key-store-loader&gt;</code> is an "instance" configuration that takes a <code>class-name</code> or a <code>class-factory-name</code> and <code>method-name</code> parameter. Optionally the configuration can also use <code>&lt;init-params&gt;</code> to pass parameters to the class constructor or factory method.</p>

<p><router-link to="#example-6-14" @click.native="this.scrollFix('#example-6-14')">Example 6-14</router-link> shows how the <code>CustomKeyStoreLoader</code> can be refactored to add constructor arguments.</p>

</div>

<h4 id="example-6-14"><em>Example 6-14 a Custom KeyStore Loader With Parameters</em></h4>
<div class="section">
<markup
lang="java"
title="CustomKeyStoreLoader.java"
>package com.acme.coherence;

import com.tangosol.net.ssl.KeyStoreLoader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

public class CustomKeyStoreLoader
        implements KeyStoreLoader
    {
    private final String param1;

    private final String param2;

    public CustomKeyStoreLoader(String param1, String param2)
        {
        this.param1 = param1;
        this.param2 = param2;
        }

    @Override
    public KeyStore load(String sType, PasswordProvider password)
            throws GeneralSecurityException, IOException
        {
        // return a KeyStore of the required type
        }
    }</markup>

<p><router-link to="#example-6-15" @click.native="this.scrollFix('#example-6-15')">Example 6-14</router-link> shows how the parameterized <code>CustomKeyStoreLoader</code> can be configured.
With the example configuration, the <code>CustomKeyStoreLoader</code> constructor will be called with the parameters <code>foo</code> and <code>bar</code>.</p>

</div>

<h4 id="example-6-15"><em>Example 6-15 Configure a Custom KeyStore Loader With Parameters</em></h4>
<div class="section">
<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;identity-manager&gt;
      &lt;key-store&gt;
        &lt;key-store-loader&gt;
          &lt;class-name&gt;com.acme.coherence.CustomKeyStoreLoader&lt;/class-name&gt;
          &lt;init-params&gt;
            &lt;init-param&gt;
              &lt;param-type&gt;string&lt;/param-type&gt;
              &lt;param-value&gt;foo&lt;/param-value&gt;
            &lt;/init-param&gt;
            &lt;init-param&gt;
              &lt;param-type&gt;string&lt;/param-type&gt;
              &lt;param-value&gt;bar&lt;/param-value&gt;
            &lt;/init-param&gt;
          &lt;/init-params&gt;
        &lt;/key-store-loader&gt;
      &lt;/key-store&gt;
    &lt;/trust-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

<p>At runtime the <code>CustomKeyStoreLoader</code> class&#8217;s <code>load</code> method will be called to load the keystore.
In the configurations above the <code>type</code> parameter passed to the load method will be the default keystore type ("JKS").
The <code>PasswordProvider</code> passed to the load method will be the default null implementation that returns an empty password.</p>

<p><router-link to="#example-6-16" @click.native="this.scrollFix('#example-6-16')">Example 6-16</router-link> shows how to configure the keystore type and password, which will be passed as parameters
to the custom <code>KeyStoreLoader.load</code>. The example shows using the <code>&lt;password&gt;</code> element, but the <code>&lt;password-url&gt;</code> or <code>&lt;password-provider&gt;</code> elements can also be used to supply the password to the loader.</p>

</div>

<h4 id="example-6-16"><em>Example 6-16 Passing the Keystore Type and Password to a Custom KeyStore Loader</em></h4>
<div class="section">
<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;identity-manager&gt;
      &lt;key-store&gt;
        &lt;key-store-loader&gt;
          &lt;class-name&gt;com.acme.coherence.CustomKeyStoreLoader&lt;/class-name&gt;
        &lt;/key-store-loader&gt;
        &lt;password&gt;secret&lt;/password&gt;
        &lt;type&gt;PKCS12&lt;/type&gt;
      &lt;/key-store&gt;
    &lt;/identity-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

</div>
</div>

<h3 id="_custom_privatekey_loader">Custom PrivateKey Loader</h3>
<div class="section">
<p>If using private keys instead of keystores, an instance of a <code>com.tangosol.net.ssl.PrivateKeyLoader</code> can be implemented in application code and configured in the <code>&lt;key-loader&gt;</code> element.
The custom loader can then load a <code>PrivateKey</code> from any desired location in any required format.</p>

<p>As with other extension points in Coherence, the <code>&lt;key-loader&gt;</code> is an "instance" configuration that takes a <code>class-name</code> or a <code>class-factory-name</code> and <code>method-name</code> parameter. Optionally the configuration can also use <code>&lt;init-params&gt;</code> to pass parameters to the class constructor or factory method.</p>

<p><router-link to="#example-6-17" @click.native="this.scrollFix('#example-6-17')">Example 6-16</router-link> shows a custom <code>PrivateKeyLoader</code> class.</p>


<h4 id="example-6-17"><em>Example 6-17 a Custom Private Key Loader</em></h4>
<div class="section">
<markup
lang="java"
title="CustomKeyStoreLoader.java"
>package com.acme.coherence;

import com.tangosol.net.PasswordProvider;
import com.tangosol.net.ssl.PrivateKeyLoader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

public class CustomPrivateKeyLoader
        implements PrivateKeyLoader
    {
    @Override
    public PrivateKey load(PasswordProvider password)
            throws GeneralSecurityException, IOException
        {
        // return a PrivateKey (optionally encrypted with a password)
        }
    }</markup>

<p><router-link to="#example-6-18" @click.native="this.scrollFix('#example-6-18')">Example 6-18</router-link> shows how the <code>CustomPrivateKeyLoader</code> class could be configured in the <code>&lt;identity-manager&gt;</code> element.</p>

</div>

<h4 id="example-6-18"><em>Example 6-18 Configure a Custom Private Key Loader</em></h4>
<div class="section">
<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;identity-manager&gt;
        &lt;key-loader&gt;
           &lt;class-name&gt;com.acme.coherence.CustomPrivateKeyLoader&lt;/class-name&gt;
        &lt;/key-loader&gt;
    &lt;/identity-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

<p>At runtime the <code>CustomPrivateKeyLoader</code> class&#8217;s <code>load</code> method will be called to create the <code>PrivateKey</code> instance.
In the example above there was no password configured for the key, so the <code>PasswordProvider</code> passed to the <code>load</code> method will return an empty password (<code>new char[0]</code>).
A password can be added using one of the password elements allowed in the <code>&lt;identity-manager&gt;</code> elements.</p>

<p><router-link to="#example-6-19" @click.native="this.scrollFix('#example-6-19')">Example 6-16</router-link> shows an example configuration with a password. In this example, the <code>PasswordProvider</code> will return the contents fetched from the URL <code>file:/coherence/security/key-pass.txt</code> as the key password.</p>

</div>

<h4 id="example-6-19"><em>Example 6-19 Configure a Password for a Custom Private Key Loader</em></h4>
<div class="section">
<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;identity-manager&gt;
      &lt;key-loader&gt;
        &lt;class-name&gt;com.acme.coherence.CustomPrivateKeyLoader&lt;/class-name&gt;
      &lt;/key-loader&gt;
      &lt;password-url&gt;file:/coherence/security/key-pass.txt&lt;/password-url&gt;
    &lt;/identity-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

</div>
</div>

<h3 id="_custom_certificate_loader">Custom Certificate Loader</h3>
<div class="section">
<p>If using certificate files in the identity manager or trust manager, an instance of a <code>com.tangosol.net.ssl.CertificateLoader</code> can be implemented in application code and configured in the <code>&lt;cert-loader&gt;</code> element.
This class can load an array of <code>Certificate</code> instances from any desired location in the required format.</p>

<p>As with other extension points in Coherence, the <code>&lt;cert-loader&gt;</code> is an "instance" configuration that takes a <code>class-name</code> or a <code>class-factory-name</code> and <code>method-name</code> parameter. Optionally the configuration can also use <code>&lt;init-params&gt;</code> to pass parameters to the class constructor or factory method.</p>

<p><router-link to="#example-6-20" @click.native="this.scrollFix('#example-6-20')">Example 6-20</router-link> shows an example of a custom <code>CertificateLoader</code> class. The <code>load</code> method will be called to load the certificates.</p>


<h4 id="example-6-20"><em>Example 6-20 a Custom Certificate Loader</em></h4>
<div class="section">
<markup
lang="java"
title="CustomKeyStoreLoader.java"
>package com.acme.coherence;

public class CustomCertificateLoader
        implements CertificateLoader
    {
    @Override
    public Certificate[] load()
            throws GeneralSecurityException, IOException
        {
        // return a Certificate array
        }
    }</markup>

<p><router-link to="#example-6-21" @click.native="this.scrollFix('#example-6-21')">Example 6-21</router-link> shows how the <code>CustomCertificateLoader</code> class above could be configured in the identity manager.</p>

</div>

<h4 id="example-6-21"><em>Example 6-21 Configure a Custom Certificate Loader in an Identity Manager</em></h4>
<div class="section">
<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;identity-manager&gt;
      &lt;key&gt;server.pem&lt;/key&gt;
      &lt;cert-loader&gt;
        &lt;class-name&gt;com.acme.coherence.CustomCertificateLoader&lt;/class-name&gt;
      &lt;/cert-loader&gt;
    &lt;/identity-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

<p><router-link to="#example-6-22" @click.native="this.scrollFix('#example-6-22')">Example 6-22</router-link> shows how the <code>CustomCertificateLoader</code> class above could be configured in the trust manager.</p>

</div>

<h4 id="example-6-22"><em>Example 6-22 Configure a Custom Certificate Loader in an Trust Manager</em></h4>
<div class="section">
<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;trust-manager&gt;
      &lt;cert-loader&gt;
        &lt;class-name&gt;com.acme.coherence.CustomCertificateLoader&lt;/class-name&gt;
      &lt;/cert-loader&gt;
    &lt;/trust-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

<p>The <code>load()</code> method of the <code>CertificateLoader</code> returns an array of certificates, so it can load multiple certificates.
It is also possible to configure multiple <code>&lt;cert-loader&gt;</code> elements to use multiple custom loaders.
All the certificates provided by all the <code>&lt;cert&gt;</code> or <code>&lt;cert-loader&gt;</code> elements will be combined into a single set of certificates for the SSL context to use.</p>

<p><router-link to="#example-6-23" @click.native="this.scrollFix('#example-6-23')">Example 6-23</router-link> shows how multiple <code>&lt;cert&gt;</code> and custom loaders could be configured in a trust manager.</p>

</div>

<h4 id="example-6-23"><em>Example 6-23 Configure Multiple Certificates and Loaders in a Trust Manager</em></h4>
<div class="section">
<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;trust-manager&gt;
      &lt;cert&gt;server-ca.cert&lt;/cert&gt;
      &lt;cert-loader&gt;
        &lt;class-name&gt;com.acme.coherence.CustomCertificateLoader&lt;/class-name&gt;
        &lt;init-params&gt;
          &lt;init-param&gt;
            &lt;param-type&gt;string&lt;/param-type&gt;
            &lt;param-value&gt;foo&lt;/param-value&gt;
          &lt;/init-param&gt;
        &lt;/init-params&gt;
      &lt;/cert-loader&gt;
      &lt;cert-loader&gt;
        &lt;class-name&gt;com.acme.coherence.CustomCertificateLoader&lt;/class-name&gt;
        &lt;init-params&gt;
          &lt;init-param&gt;
            &lt;param-type&gt;string&lt;/param-type&gt;
            &lt;param-value&gt;bar&lt;/param-value&gt;
          &lt;/init-param&gt;
        &lt;/init-params&gt;
      &lt;/cert-loader&gt;
    &lt;/trust-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

</div>
</div>
</div>

<h2 id="refresh">Refreshable KeyStores, Private Keys and Certificates</h2>
<div class="section">
<p>In some environments, keys and certs used for TLS are created with relatively short lifetimes.
This means that a Coherence application needs to be able to renew the keys and certs, ideally without having to restart the JVM. In versions of Coherence prior to 22.06, this was not possible, as a Keystore was loaded once when the socket provider was instantiated. From version 22.06 it is possible to specify a refresh period, which will then schedule a refresh of the SSL context, reloading any configured keystores, private keys and certificates.</p>

<p>The <code>&lt;refresh-period&gt;</code> element is used to configure the refresh time.
This is a child element of the <code>ssl</code> element, meaning the setting applies to both the identity manager and trust manager.</p>

<p><router-link to="#example-6-24" @click.native="this.scrollFix('#example-6-24')">Example 6-24</router-link> configures a <code>&lt;refresh-period&gt;</code> element with a value of <code>24h</code>, so the keys and certs will be refreshed every 24 hours.</p>


<h3 id="example-6-24"><em>Example 6-24 Configure a Refresh Period</em></h3>
<div class="section">
<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;identity-manager&gt;
      &lt;key&gt;server.pem&lt;/key&gt;
      &lt;cert&gt;server.cert&lt;/cert&gt;
    &lt;/identity-manager&gt;
    &lt;refresh-period&gt;24h&lt;/refresh-period&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

<p>Refreshable keystores, keys and certs can easily be combined with custom keystore loaders, private key loaders, and certificate loaders, so that new versions of the required SSL artifacts can be pulled from an external source.</p>

</div>

<h3 id="_configuring_a_refresh_policy">Configuring a Refresh Policy</h3>
<div class="section">
<p>When using refreshable keys and certs it may sometimes be useful to have an additional check to determine whether a refresh should occur. This can be achieved by configuring a <code>&lt;refresh-policy&gt;</code> as well as a <code>&lt;refresh-period&gt;</code>.</p>

<p>The <code>&lt;refresh-policy&gt;</code> element is a standard Coherence <code>instance</code> configuration and should resolve to an instance
of a <code>com.tangosol.net.ssl.RefreshPolicy</code>.
When a scheduled refresh time is reached the policy is checked first (by calling the <code>RefreshPolicy.shouldRefresh()</code> method) to determine whether the refresh should go ahead.</p>

<p><router-link to="#example-6-25" @click.native="this.scrollFix('#example-6-25')">Example 6-25</router-link> shows an example of a custom <code>RefreshPolicy</code> implementation.</p>

</div>

<h3 id="example-6-25"><em>Example 6-25 A Custom Refresh Policy Class</em></h3>
<div class="section">
<markup
lang="java"
title="CustomRefreshPolicy.java"
>package com.acme.coherence;

public class CustomRefreshPolicy
        implements RefreshPolicy
    {
    @Override
    public boolean shouldRefresh(Dependencies deps, ManagerDependencies depsIdMgr, ManagerDependencies depsTrustMgr)
        {
        // perform some custom logic to determine whether it is time to refresh
        return true;
        }
    }</markup>

<p><router-link to="#example-6-26" @click.native="this.scrollFix('#example-6-26')">Example 6-26</router-link> shows how the custom refresh policy can then be configured as part of
the <code>&lt;ssl&gt;</code> element alongside the <code>&lt;refresh-period&gt;</code></p>

</div>

<h3 id="example-6-26"><em>Example 6-26 Configure a Custom Refresh Policy</em></h3>
<div class="section">
<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;identity-manager&gt;
      &lt;key&gt;server.pem&lt;/key&gt;
      &lt;cert&gt;server.cert&lt;/cert&gt;
    &lt;/identity-manager&gt;
    &lt;refresh-period&gt;24h&lt;/refresh-period&gt;
    &lt;refresh-policy&gt;
      &lt;class-name&gt;com.acme.coherence.CustomRefreshPolicy&lt;/class-name&gt;
    &lt;/refresh-policy&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

<p>For some policies, it may be useful to know what keystores, keys or certs are currently in use to determine whether they need to be refreshed. There are a number of default methods on <code>RefreshPolicy</code> that can be overridden for this purpose.</p>

<p><router-link to="#example-6-27" @click.native="this.scrollFix('#example-6-27')">Example 6-27</router-link> shows how certificates used by a trust store configuration can be captured and then used to verify whether they are close to expiry. In the <code>CustomRefreshPolicy</code> below, the <code>trustStoreLoaded</code> method is called when the trust store is created to notify the policy of the certificates used by the trust store. In the <code>shouldRefresh</code> method the certificates can then be checked to determine whether they will still be valid at the next refresh interval.</p>

</div>

<h3 id="example-6-27"><em>Example 6-27 A Detailed Custom Certificate Refresh Policy</em></h3>
<div class="section">
<markup
lang="java"
title="CustomRefreshPolicy.java"
>import com.oracle.coherence.common.net.SSLSocketProvider.Dependencies;
import com.oracle.coherence.common.util.Duration;
import com.tangosol.coherence.config.builder.SSLSocketProviderDependenciesBuilder.ManagerDependencies;
import com.tangosol.coherence.config.unit.Seconds;
import com.tangosol.net.ssl.RefreshPolicy;

import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;

public class CustomRefreshPolicy
        implements RefreshPolicy
    {
    private Certificate[] certs;

    @Override
    public void trustStoreLoaded(Certificate[] certs)
        {
        this.certs = certs;
        }

    @Override
    public boolean shouldRefresh(Dependencies deps, ManagerDependencies depsIdMgr, ManagerDependencies depsTrustMgr)
        {
        if (certs == null)
            {
            return true;
            }

        // get the refresh period from the dependencies
        Seconds secs = deps.getRefreshPeriod();
        // calculate the next refresh time as a Date
        Date nextRefresh = new Date(System.currentTimeMillis() + secs.as(Duration.Magnitude.MILLI));

        for (Certificate certificate : certs)
            {
            try
                {
                // The certs are all X509 certs, so check their validity on the next refresh date
                ((X509Certificate) certificate).checkValidity(nextRefresh);
                }
            catch (CertificateExpiredException | CertificateNotYetValidException e)
                {
                // a cert will have expired, so we need to update now
                return true;
                }
            }

        // no certs should have expired at the next refresh check
        return false;
        }
    }</markup>

<p><router-link to="#example-6-28" @click.native="this.scrollFix('#example-6-28')">Example 6-28</router-link> shows how the <code>CustomRefreshPolicy</code> class can then be configured in the <code>&lt;ssl&gt;</code> configuration.</p>

</div>

<h3 id="example-6-28"><em>Example 6-28 Configure the Custom Certificate Refresh Policy</em></h3>
<div class="section">
<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;trust-manager&gt;
      &lt;ca-cert&gt;server-ca.cert&lt;/ca-cert&gt;
      &lt;ca-cert&gt;client-ca.cert&lt;/ca-cert&gt;
    &lt;/trust-manager&gt;
    &lt;refresh-period&gt;24h&lt;/refresh-period&gt;
    &lt;refresh-policy&gt;
      &lt;class-name&gt;com.acme.coherence.CustomRefreshPolicy&lt;/class-name&gt;
    &lt;/refresh-policy&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

</div>
</div>

<h2 id="operational">Operational Configuration Element Reference</h2>
<div class="section">
<p>This section details changes to the
<a id="" title="" target="_blank" href="https://docs.oracle.com/en/middleware/standalone/coherence/14.1.1.0/develop-applications/operational-configuration-elements.html#GUID-6FD7679F-C4A3-4B74-8B81-8C5B7929BC18">Operational Configuration Element Reference</a> documentation based on the SSL configuration changes introduced in Coherence 22.06.</p>

<p>The following elements have changes</p>

<ul class="ulist">
<li>
<p><router-link to="#identity" @click.native="this.scrollFix('#identity')">identity-manager</router-link></p>

</li>
<li>
<p><router-link to="#keystore" @click.native="this.scrollFix('#keystore')">key-store</router-link></p>

</li>
<li>
<p><router-link to="#ssl" @click.native="this.scrollFix('#ssl')">ssl</router-link></p>

</li>
<li>
<p><router-link to="#trust" @click.native="this.scrollFix('#trust')">trust-manager</router-link></p>

</li>
</ul>

<h3 id="identity">identity-manager</h3>
<div class="section">
<p>Use in: <router-link to="#ssl" @click.native="this.scrollFix('#ssl')">ssl</router-link></p>

<p><strong>Description</strong>
The &lt;identity-manager&gt; element contains the configuration information for initializing a javax.net.ssl.KeyManager instance.</p>

<p>The identity manager is responsible for managing the key material which is used to authenticate the local connection to its peer. If no key material is available, the connection cannot present authentication credentials.</p>

<p><strong>Elements</strong></p>

<p><router-link to="#table-a-25" @click.native="this.scrollFix('#table-a-25')">Table A-25</router-link> describes the subelements of the <code>identity-manager</code> element.</p>

<p id="table-a-25"><strong>Table A-25 identity-manager Subelements</strong></p>


<div class="table__overflow elevation-1  ">
<table class="datatable table">
<colgroup>
<col style="width: 33.333%;">
<col style="width: 33.333%;">
<col style="width: 33.333%;">
</colgroup>
<thead>
<tr>
<th>Element</th>
<th>Required/ Optional</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td class=""><code>&lt;algorithm&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the algorithm used by the identity manager. The default value is <code>SunX509</code>.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><a id="" title="" target="_blank" href="https://docs.oracle.com/en/middleware/standalone/coherence/14.1.1.0/develop-applications/operational-configuration-elements.html#GUID-5BB03F1C-D70C-4F44-9B09-2480796E67D9"><code>&lt;provider&gt;</code></a></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the configuration for a security provider instance.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;key-store&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the configuration for a key store implementation.</p>

<p>The <code>&lt;key-store&gt;</code> element cannot be specified if the <code>&lt;key&gt;</code>, <code>&lt;key-loader&gt;</code>, <code>&lt;cert&gt;</code> or <code>&lt;cert-loader&gt;</code> elements are specified.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;key&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the URL to load a private key from.</p>

<p>The <code>&lt;key&gt;</code> element cannot be specified if the <code>&lt;key-store&gt;</code> or <code>&lt;key-loader&gt;</code> elements are specified.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;key-loader&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Configures a custom implementation of <code>com.tangosol.net.ssl.PrivateKeyLoader</code> that will provide a <code>PrivateKey</code>.</p>

<p>A <code>&lt;class-name&gt;</code> subelement is used to provide the name of a class that implements the <code>com.tangosol.net.ssl.PrivateKeyLoader</code> interface. As an alternative, use a <code>&lt;class-factory-name&gt;</code> subelement to specify a factory class for creating <code>PrivateKeyLoader</code> instances and a <code>&lt;method-name&gt;</code> subelement that specifies the name of a static factory method on the factory class which performs object instantiation. Either approach can specify initialization parameters using the <code>&lt;init-params&gt;</code> element.</p>

<p>The <code>&lt;key-loader&gt;</code> element cannot be specified if the <code>&lt;key&gt;</code> element is specified.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;cert&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the URL to load a certificate from.</p>

<p>The <code>&lt;cert&gt;</code> element cannot be specified if the <code>&lt;key-store&gt;</code> element is specified.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;cert-loader&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Configures a custom implementation of <code>com.tangosol.net.ssl.CertificateLoader</code> that will provide a <code>Certificate</code>.</p>

<p>A <code>&lt;class-name&gt;</code> subelement is used to provide the name of a class that implements the <code>com.tangosol.net.ssl.CertificateLoader</code> interface. As an alternative, use a <code>&lt;class-factory-name&gt;</code> subelement to specify a factory class for creating <code>CertificateLoader</code> instances and a <code>&lt;method-name&gt;</code> subelement that specifies the name of a static factory method on the factory class which performs object instantiation. Either approach can specify initialization parameters using the <code>&lt;init-params&gt;</code> element.</p>

<p>The <code>&lt;cert-loader&gt;</code> element cannot be specified if the <code>&lt;key-store&gt;</code> element is specified.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;password&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the private key password.</p>

<p>This element cannot be used with the <code>&lt;password-provider&gt;</code> or <code>&lt;password-url&gt;</code> elements.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;password-url&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the file or simple URL to read the private key password from.</p>

<p>This element cannot be used with the <code>&lt;password&gt;</code> or <code>&lt;password-provider&gt;</code> elements.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><a id="" title="" target="_blank" href="https://docs.oracle.com/en/middleware/standalone/coherence/14.1.1.0/develop-applications/operational-configuration-elements.html#GUID-C4F23EE0-5EBF-49E4-A735-7EF41A53CD9D"><code>&lt;password-provider&gt;</code></a></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies a password provider implementation for retrieving the private key password.</p>

<p>This element cannot be used with the <code>&lt;password&gt;</code> or <code>&lt;password-url&gt;</code> elements.</p>

</doc-view>
</td>
</tr>
</tbody>
</table>
</div>
</div>

<h3 id="keystore">key-store</h3>
<div class="section">
<p>Used in: <router-link to="#identity" @click.native="this.scrollFix('#identity')">identity-manager</router-link> and <router-link to="#trust" @click.native="this.scrollFix('#trust')">trust-manager</router-link>.</p>

<p><strong>Description</strong></p>

<p>The <code>key-store</code> element specifies the configuration for a key store implementation to use when implementing SSL. The key store implementation is an instance of the <code>java.security.KeyStore</code> class.</p>

<p><strong>Elements</strong></p>

<p><router-link to="#table-34" @click.native="this.scrollFix('#table-34')">Table A-34</router-link> describes the subelements of the <code>key-store</code> element.</p>

<p id="table-a-34"><strong>Table A-34 key-store Subelements</strong></p>


<div class="table__overflow elevation-1  ">
<table class="datatable table">
<colgroup>
<col style="width: 33.333%;">
<col style="width: 33.333%;">
<col style="width: 33.333%;">
</colgroup>
<thead>
<tr>
<th>Element</th>
<th>Required/Optional</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td class=""><code>&lt;url&gt;</code></td>
<td class="">Required if the <code>&lt;key-store-loader&gt;</code> is not specified</td>
<td class=""><doc-view>
<p>Specifies the Uniform Resource Locator (URL) to a key store.</p>

<p>The <code>&lt;url&gt;</code> element cannot be specified if the <code>&lt;key-store-loader&gt;</code> element is specified.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;key-store-loader&gt;</code></td>
<td class="">Required if the <code>&lt;url&gt;</code> is not specified</td>
<td class=""><doc-view>
<p>Configures a custom implementation of <code>com.tangosol.net.ssl.KeyStoreLoader</code> that will provide a <code>KeyStore</code>.</p>

<p>A <code>&lt;class-name&gt;</code> subelement is used to provide the name of a class that implements the <code>com.tangosol.net.ssl.KeyStoreLoader</code> interface. As an alternative, use a <code>&lt;class-factory-name&gt;</code> subelement to specify a factory class for creating <code>KeyStoreLoader</code> instances and a <code>&lt;method-name&gt;</code> subelement that specifies the name of a static factory method on the factory class which performs object instantiation. Either approach can specify initialization parameters using the <code>&lt;init-params&gt;</code> element.</p>

<p>The <code>&lt;key-store-loader&gt;</code> element cannot be specified if the <code>&lt;url&gt;</code> element is specified.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;password&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the password for the key store.</p>

<p>This element cannot be used with the <code>&lt;password-provider&gt;</code> or <code>&lt;password-url&gt;</code> elements.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;password-url&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the file or simple URL to read the the key store password.</p>

<p>This element cannot be used with the <code>&lt;password&gt;</code> or <code>&lt;password-provider&gt;</code> elements.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><a id="" title="" target="_blank" href="https://docs.oracle.com/en/middleware/standalone/coherence/14.1.1.0/develop-applications/operational-configuration-elements.html#GUID-C4F23EE0-5EBF-49E4-A735-7EF41A53CD9D"><code>&lt;password-provider&gt;</code></a></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies a password provider implementation for retrieving the key store password.</p>

<p>This element cannot be used with the <code>&lt;password&gt;</code> or <code>&lt;password-url&gt;</code> elements.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;type&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the type of a <code>java.security.KeyStore</code> instance. The default value is <code>JKS</code>.</p>

</doc-view>
</td>
</tr>
</tbody>
</table>
</div>
</div>

<h3 id="ssl">ssl</h3>
<div class="section">
<p>Used in: <a id="" title="" target="_blank" href="https://docs.oracle.com/en/middleware/standalone/coherence/14.1.1.0/develop-applications/operational-configuration-elements.html#GUID-FA62570C-D64E-417E-AFBB-133862864C65">socket-provider</a>.</p>

<p><strong>Description</strong></p>

<p>The <code>&lt;ssl&gt;</code> element contains the configuration information for a socket provider that produces socket and channel implementations which use SSL. If SSL is configured for the unicast listener, the listener must be configured to use well known addresses.</p>

<p><strong>Elements</strong></p>

<p><router-link to="#table-a-86" @click.native="this.scrollFix('#table-a-86')">Table A-86</router-link> describes the subelements of the <code>ssl</code> element.</p>

<p id="table-a-86"><strong>Table A-86 ssl Subelements</strong></p>


<div class="table__overflow elevation-1  ">
<table class="datatable table">
<colgroup>
<col style="width: 33.333%;">
<col style="width: 33.333%;">
<col style="width: 33.333%;">
</colgroup>
<thead>
<tr>
<th>Element</th>
<th>Required/ Optional</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td class=""><code>&lt;protocol&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the name of the protocol used by the socket and channel implementations produced by the SSL socket provider. The default value is <code>TLS</code>.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;provider&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the configuration for a security provider instance.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;executor&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the configuration information for an implementation of the <code>java.util.concurrent.Executor</code> interface.</p>

<p>A <code>&lt;class-name&gt;</code> subelement is used to provide the name of a class that implements the Executor interface. As an alternative, use a <code>&lt;class-factory-name&gt;</code> subelement to specify a factory class for creating Executor instances and a <code>&lt;method-name&gt;</code> subelement that specifies the name of a static factory method on the factory class which performs object instantiation. Either approach can specify initialization parameters using the <code>&lt;init-params&gt;</code> element.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><router-link to="#identity" @click.native="this.scrollFix('#identity')"><code>&lt;identity-manager&gt;</code></router-link></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the configuration information for initializing an identity manager instance.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><router-link to="#trust" @click.native="this.scrollFix('#trust')"><code>&lt;trust-manager&gt;</code></router-link></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the configuration information for initializing a trust manager instance.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;hostname-verifier&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Coherence provides a default implementation of HostnameVerifier.</p>

<p>Use the <code>&lt;action&gt;</code> subelement to specify the action that the default hostname verifier should take during the SSL handshake if there is a mismatch between the host name in the URL and the host name in the digital certificate that the server sends back as part of the SSL connection. <code>&lt;action&gt;</code> takes one of the following two possible values:</p>

<ul class="ulist">
<li>
<p><strong>allow</strong>: Allow all connections. There is no hostname verification. For backwards compatibility, this is the default option.</p>

</li>
<li>
<p><strong>default</strong>: Use the default hostname verifier to verify the host. The host name verification passes if the host name in the certificate matches the host name in the URL to which the client connects. It also allows wild-card Subject Alternate Names (SAN) and Common Names (CN). If you do not want a match against CN if SAN DNS names are present, set the system property coherence.security.ssl.verifyCNAfterSAN to false. If the URL specifies localhost, you can set the system property coherence.security.ssl.allowLocalhost to true to allow <code>127.0.0.1</code>, or the default IP address of the local machine.</p>

</li>
</ul>
<p>You can also specify the configuration information for an implementation of the <code>javax.net.ssl.HostnameVerifier</code> interface. During the SSL handshake, if the URL&#8217;s host name and the server&#8217;s identification host name mismatch, the verification mechanism calls back to this instance to determine if the connection should be allowed.</p>

<p>A <code>&lt;class-name&gt;</code> subelement is used to provide the name of a class that implements the <code>HostnameVerifier</code> interface. As an alternative, use a <code>&lt;class-factory-name&gt;</code> subelement to specify a factory class for creating <code>HostnameVerifier</code> instances and a <code>&lt;method-name&gt;</code> subelement that specifies the name of a static factory method on the factory class which performs object instantiation. Either approach can specify initialization parameters using the <code>&lt;init-params&gt;</code> element.</p>

<p>For more information about host name verification, see
<a id="" title="" target="_blank" href="https://docs.oracle.com/pls/topic/lookup?ctx=en/middleware/standalone/coherence/14.1.1.0/develop-applications&amp;id=COHSG-GUID-436A16D3-509A-4F99-944D-C878B3A480D5">Using a Custom Host Name Verifier</a> in <em>Securing Oracle Coherence</em>.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;cipher-suites&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies a list of ciphers. Use the name element within the <code>cipher-suites</code> element to enter a cipher. Multiple name elements can be specified.</p>

<p>Use the <code>usage</code> attribute to specify whether the list of ciphers are allowed or disallowed. If the <code>usage</code> attribute value is <code>black-list</code>, then the specified ciphers are removed from the default enabled cipher list. If the usage attribute value is<code> white-list</code>, then the specified ciphers are the enabled ciphers. The default value if the usage attribute is not specified is <code>white-list</code>.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;protocol-versions&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies a list of protocol versions. Use the name element within the protocol-versions element to enter a protocol version. Multiple name elements can be specified.</p>

<p>Use the <code>usage</code> attribute to specify whether the list of protocol versions are allowed or disallowed. If the <code>usage</code> attribute value is <code>black-list</code>, then the specified protocol versions are removed from the default enabled protocol list. If the <code>usage</code> attribute value is <code>white-list</code>, then the specified protocol versions are the enabled protocols. The default value if the usage attribute is not specified is <code>white-list</code>.</p>

</doc-view>
</td>
</tr>
<tr>
<td class="">&lt;socket-provider&gt;</td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the configuration information for a delegate provider for SSL. Valid values are <code>tcp</code> and <code>sdp</code>. The default value is <code>tcp</code>.</p>

</doc-view>
</td>
</tr>
<tr>
<td class="">&lt;refresh-period&gt;</td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>The <code>refresh-period</code> element specifies the period to use to attempt to refresh keys and certs. This is used in cases where keys or certs have a short lifetime and need to be refreshed at runtime.</p>

<p>If this element is omitted or is set to a value less than or equal to zero, then keys and certs will not be refreshed.</p>

<p>The value of this element must be in the following format:</p>

<pre>`(\d)+((.)(\d)+)?(MS|ms|S|s|M|m|H|h|D|d)?`</pre>
<p>where the first non-digits (from left to right) indicate the unit of time duration:</p>

<pre>-MS or ms (milliseconds)</pre>
<pre>-S or s (seconds)</pre>
<pre>-M or m (minutes)</pre>
<pre>-H or h (hours)</pre>
<pre>-D or d (days)</pre>
<p>If the value does not contain a unit, a unit of seconds is assumed.</p>

</doc-view>
</td>
</tr>
<tr>
<td class="">&lt;refresh-policy&gt;</td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>The <code>refresh-policy</code> element contains the configuration info for a refresh policy that extends the <code>com.tangosol.net.ssl.RefreshPolicy</code> class.</p>

<p>A <code>&lt;class-name&gt;</code> subelement is used to provide the name of a class that implements the <code>RefreshPolicy</code> interface. As an alternative, use a <code>&lt;class-factory-name&gt;</code> subelement to specify a factory class for creating <code>RefreshPolicy</code> instances and a <code>&lt;method-name&gt;</code> subelement that specifies the name of a static factory method on the factory class which performs object instantiation. Either approach can specify initialization parameters using the <code>&lt;init-params&gt;</code> element.</p>

</doc-view>
</td>
</tr>
</tbody>
</table>
</div>
</div>

<h3 id="trust">trust-manager</h3>
<div class="section">
<p>Use in: <router-link to="#ssl" @click.native="this.scrollFix('#ssl')">ssl</router-link></p>

<p><strong>Description</strong></p>

<p>The <code>&lt;trust-manager&gt;</code> element contains the configuration information for initializing a javax.net.ssl.TrustManager instance.</p>

<p>A trust manager is responsible for managing the trust material that is used when making trust decisions and for deciding whether credentials presented by a peer should be accepted.</p>

<p>A valid trust-manager configuration contains at least one child element.</p>

<p><strong>Elements</strong></p>

<p><router-link to="#table-a-93" @click.native="this.scrollFix('#table-a-93')">Table A-93</router-link> describes the elements of the <code>trust-manager</code> element.</p>

<p id="table-a-93"><strong>Table A-93 trust-manager Subelements</strong></p>


<div class="table__overflow elevation-1  ">
<table class="datatable table">
<colgroup>
<col style="width: 33.333%;">
<col style="width: 33.333%;">
<col style="width: 33.333%;">
</colgroup>
<thead>
<tr>
<th>Element</th>
<th>Required/ Optional</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td class=""><code>&lt;algorithm&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the algorithm used by the trust manager. The default value is SunX509.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;provider&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the configuration for a security provider instance.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;key-store&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the configuration for a key store implementation.</p>

<p>The <code>&lt;key-store&gt;</code> element cannot be specified if the <code>&lt;cert&gt;</code> or <code>&lt;cert-loader&gt;</code> elements are specified.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;cert&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Specifies the URL to load a certificate from. Multiple <code>&lt;cert&gt;</code> elements can be specified to load multiple certificates.</p>

<p>The <code>&lt;cert&gt;</code> element cannot be specified if the <code>&lt;key-store&gt;</code> element is specified.</p>

</doc-view>
</td>
</tr>
<tr>
<td class=""><code>&lt;cert-loader&gt;</code></td>
<td class="">Optional</td>
<td class=""><doc-view>
<p>Configures a custom implementation of <code>com.tangosol.net.ssl.CertificateLoader</code> that will provide a <code>Certificate</code>. Multiple <code>&lt;cert-loader&gt;</code> elements can be specified to load multiple certificates.</p>

<p>A <code>&lt;class-name&gt;</code> subelement is used to provide the name of a class that implements the <code>com.tangosol.net.ssl.CertificateLoader</code> interface. As an alternative, use a <code>&lt;class-factory-name&gt;</code> subelement to specify a factory class for creating <code>CertificateLoader</code> instances and a <code>&lt;method-name&gt;</code> subelement that specifies the name of a static factory method on the factory class which performs object instantiation. Either approach can specify initialization parameters using the <code>&lt;init-params&gt;</code> element.</p>

<p>The <code>&lt;cert-loader&gt;</code> element cannot be specified if the <code>&lt;key-store&gt;</code> element is specified.</p>

</doc-view>
</td>
</tr>
</tbody>
</table>
</div>
</div>
</div>
</doc-view>
