<doc-view>

<h2 id="_coherence_tls_enhancements">Coherence TLS Enhancements</h2>
<div class="section">
<p>Coherence has supported using SSL/TLS to secure communication between cluster members and Extend client for a long time.
From Coherence version 22.06, this functionality has been enhanced to allow a more flexible configuration and allow customisation via extensions.</p>

<ul class="ulist">
<li>
<p>Coherence no longer relies on Java keystore files, but now also supports the use of key and certificate files.</p>

</li>
<li>
<p>By default, keystores, keys and files are loaded from a configured URL, but this can be extended with custom code to allow any source to be used. For example keys and certs could be pulled from a third-party certificate service.</p>

</li>
<li>
<p>A refresh period can be configured to periodically refresh the keystores, keys or certs being used to support expiring keys and certs without the need for a server restart.</p>

</li>
</ul>
<div class="admonition note">
<p class="admonition-textlabel">Note</p>
<p ><p>The documentation here only covers the changes made in Coherence 22.06.
There are a number of additional configuration options, for example hostname verifiers, algorithms,
black lists, etc. The use of these remains unchanged and are documented in the main Coherence documentation.</p>
</p>
</div>
</div>

<h2 id="_configuration">Configuration</h2>
<div class="section">
<p>To use TLS in Coherence an SSL socket provider must be configured. This is documented in the main Coherence documentation. An SSL socket provider is made up of two parts, an identity manager and a trust manager (either or both of these can be configured, depending on the functionality required). The identity manager is used to configure the key and certificates used to identify the specific process. The trust manager is configured with the certificates and CA certificates used to verify a connection from a remote process.</p>


<h3 id="_configure_an_identity_manager">Configure an Identity Manager</h3>
<div class="section">
<p>An identity manager is configured in the <code>&lt;identity-manager&gt;</code> child element of the <code>&lt;ssl&gt;</code> element.
There are two different ways to configure an identity manager, depending on whether a Java keystore file is being used, or individual key and certificate files.</p>


<h4 id="_using_a_keystore">Using a KeyStore</h4>
<div class="section">
<p>Previous versions of Coherence only supported configuring a Java keystore as an identity manager.
The keystore is configured in the <code>&lt;key-store&gt;</code> child element of the <code>&lt;identity-manager&gt;</code> element.</p>

<p>The following xml configures an identity manager using a keystore file.</p>

<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;identity-manager&gt;
      &lt;key-store&gt;
        &lt;url&gt;file:/coherence/security/client.jks&lt;/url&gt;  <span class="conum" data-value="1" />
        &lt;password&gt;secret&lt;/password&gt;                     <span class="conum" data-value="2" />
      &lt;/key-store&gt;
      &lt;password&gt;private&lt;/password&gt;                      <span class="conum" data-value="3" />
    &lt;/identity-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

<ul class="colist">
<li data-value="1">The keystore is named <code>client.jks</code> located in the directory <code>/coherence/security</code>.</li>
<li data-value="2">The keystore is protected with a password, in this case the password is <code>secret</code></li>
<li data-value="3">The private key in the keystore is also protected with a password, in this case <code>private</code></li>
</ul>
<div class="admonition important">
<p class="admonition-textlabel">Important</p>
<p ><p>Hard coded passwords, such as in the example above, are not very secure.
Configuring a <code>PasswordProvider</code> to read a password from an external source is a better, and more secure, option.</p>
</p>
</div>
</div>

<h4 id="_using_a_private_key_and_certificate">Using a Private Key and Certificate</h4>
<div class="section">
<p>From Coherence 22.06, instead of providing a keystore, a private key and certificates can be used.
A private key is specified using the <code>&lt;key&gt;</code> element.
A certificates are specified using the <code>&lt;cert&gt;</code> element.
By default, the values of both the <code>&lt;key&gt;</code> element and <code>cert&gt;</code> element should be URLs to load the key or certificate data from (this can be extended as described <router-link to="#extending" @click.native="this.scrollFix('#extending')">Extensions</router-link> section).</p>

<p>Instead of using the <code>&lt;key-store&gt;</code> element, the <code>&lt;key&gt;</code> and <code>&lt;cert&gt;</code> elements are used, as shown below:</p>

<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;identity-manager&gt;
      &lt;key&gt;file:/coherence/security/client.pem&lt;/key&gt;    <span class="conum" data-value="1" />
      &lt;cert&gt;file:/coherence/security/client.cert&lt;/cert&gt; <span class="conum" data-value="2" />
    &lt;/identity-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

<ul class="colist">
<li data-value="1">The key file is named <code>client.pem</code> located in the directory <code>/coherence/security</code>.</li>
<li data-value="2">The certificate file is named <code>client.cert</code> located in the directory <code>/coherence/security</code>.</li>
</ul>
<p>If required, multiple <code>&lt;cert&gt;</code> elements can be used to provide multiple certificates.</p>

<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;identity-manager&gt;
      &lt;key&gt;file:/coherence/security/client.pem&lt;/key&gt;
      &lt;cert&gt;file:/coherence/security/client1.cert&lt;/cert&gt;
      &lt;cert&gt;file:/coherence/security/client2.cert&lt;/cert&gt;
    &lt;/identity-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

</div>

<h4 id="_using_an_encrypted_private_key">Using an Encrypted Private Key</h4>
<div class="section">
<p>If the key file is encrypted and protected with a password, this can be configured in the same way as the keystore example, by providing the private key password in a child element of the <code>&lt;identity-manager&gt;</code> element.</p>

<p>In this example the password is specified using the <code>&lt;password-url&gt;</code> element, so the password will be read from the data returned from reading the URL (in this case that is a file).</p>

<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;identity-manager&gt;
      &lt;key&gt;file:/coherence/security/client.pem&lt;/key&gt;    <span class="conum" data-value="1" />
      &lt;cert&gt;file:/coherence/security/client.cert&lt;/cert&gt; <span class="conum" data-value="2" />
      &lt;password-url&gt;file:/coherence/security/key-pass.txt&lt;/password-url&gt; <span class="conum" data-value="3" />
    &lt;/identity-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

<ul class="colist">
<li data-value="1">The encrypted private key file is named <code>client.pem</code> located in the directory <code>/coherence/security</code>.</li>
<li data-value="2">The certificate file is named <code>client.cert</code> located in the directory <code>/coherence/security</code>.</li>
<li data-value="3">The password for the private key will be read from the URL <code>file:/coherence/security/key-pass.txt</code></li>
</ul>
</div>
</div>

<h3 id="_configure_a_trust_manager">Configure a Trust Manager</h3>
<div class="section">
<p>A trust manager is configured to verify the certificates provided by a remote connection can be trusted.
Typically this means using a CA Certificate to verify the client certificate.</p>


<h4 id="_using_a_keystore_2">Using a KeyStore</h4>
<div class="section">
<p>Previous versions of Coherence,  prior to 22.06, only supported configuring a Java keystore as a trust manager.
The keystore is configured in the <code>&lt;key-store&gt;</code> child element of the <code>&lt;trust-manager&gt;</code> element.</p>

<p>In the example below, the keystore <code>file:/coherence/security/server-ca.jks</code> is used to provide the certificates to verify clients.</p>

<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;trust-manager&gt;
      &lt;key-store&gt;
        &lt;url&gt;file:/coherence/security/server-ca.jks&lt;/url&gt;
      &lt;/key-store&gt;
    &lt;/trust-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

</div>

<h4 id="_using_ca_certificates">Using CA Certificates</h4>
<div class="section">
<p>From Coherence 22.06 certificates can be used instead of a keystore.
In the example below, the certificate file <code>/coherence/security/server-ca.cert</code> is used.</p>

<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;trust-manager&gt;
      &lt;cert&gt;/coherence/security/server-ca.cert&lt;/cert&gt;
    &lt;/trust-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

<p>Multiple certificates can be added using multiple <code>&lt;cert&gt;</code> elements.
For example, the configuration below configures peer authentication using three certificates, <code>server-ca.cert</code>, and <code>client-ca.cert</code>.</p>

<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;trust-manager&gt;
      &lt;cert&gt;server-ca.cert&lt;/cert&gt;
      &lt;cert&gt;client-ca.cert&lt;/cert&gt;
    &lt;/trust-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

</div>
</div>
</div>

<h2 id="_refreshable_keystores_keys_and_certificates">Refreshable KeyStores, Keys and Certificates</h2>
<div class="section">
<p>In some environments, keys and certs used for TLS are created with relatively short lifetimes.
This means that a process needs to be able to renew the keys and certs, ideally without having to restart the process. In versions of Coherence prior to 22.06, this was not possible, as a Keystore was loaded once when the socket provider was instantiated. From version 22.06 it is possible to specify a refresh period, which will then schedule a refresh.</p>

<p>The <code>&lt;refresh-period&gt;</code> element is used to configure the refresh time.
This is a child element of the <code>ssl</code> element, meaning it applies to both the identity manager and trust manager.</p>

<p>The example below configures a refresh schedule of <code>24h</code>, so the keys and certs will be refreshed every 24 hours.</p>

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

<p>Refreshable keystores, keys and certs can easily be combined with the extensions documented below so that new versions of the required files can be pulled from an external source.</p>


<h3 id="_configuring_a_refresh_policy">Configuring a Refresh Policy</h3>
<div class="section">
<p>When using refreshable keys and certs it may sometimes be useful to have an additional check to determine whether a refresh should occur. This can be achieved by configuring a <code>&lt;refresh-policy&gt;</code> as well as a <code>&lt;refresh-period&gt;</code>.</p>

<p>The <code>&lt;refresh-policy&gt;</code> element is a standard Coherence <code>instance</code> configuration and should resolve to an instance
of a <code>com.tangosol.net.ssl.RefreshPolicy</code>.
When a scheduled refresh time is reached the policy is checked first (by calling the <code>RefreshPolicy.shouldRefresh()</code> method) to determine whether the refresh should go ahead.</p>

<p>For example a custom policy might look like this:</p>

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

<p>The policy can then be configured as part of the <code>&lt;ssl&gt;</code> element alongside the <code>&lt;refresh-period&gt;</code></p>

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

<p>For example, certificates used by a trust store configuration can be captured and then used to verify whether they are close to expiry. In the <code>CustomRefreshPolicy</code> below, the <code>trustStoreLoaded</code> method is called when the trust store is created to notify the policy of the certificates used by the trust store. In the <code>shouldRefresh</code> method the certificates can then be checked to determine whether they will still be valid at the next refresh interval.</p>

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

<p>The <code>CustomRefreshPolicy</code> class can then be configured in the <code>&lt;ssl&gt;</code> configuration as shown below.</p>

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

<h2 id="_configuring_keystore_and_private_key_passwords">Configuring KeyStore and Private Key Passwords</h2>
<div class="section">
<p>When configuring the identity manager and trust manager there are elements in the configuration to provide optional passwords for the private key and the keystores. Prior to Coherence 12.2.1.4 the only way to configure a password was to use the <code>&lt;password&gt;</code> element. This would then use either a hard coded password, or a password from a System property. This is not particularly secure so in Coherence 12.2.1.4 the <code>&lt;password-provider&gt;</code> element was introduced where a custom <code>PasswordProvider</code> implementation could be configured, which can obtain a password from anywhere.
Although <code>PasswordProvider</code> was introduced in 12.2.1.4, Coherence did not include any out of the box implementations.</p>


<h3 id="_configure_a_password_from_a_url">Configure a Password From a URL</h3>
<div class="section">
<p>Starting with Coherence 22.06 a new password configuration element was introduced, <code>&lt;password-url&gt;</code>.
The <code>&lt;password-url&gt;</code> configures a <code>com.tangosol.net.URLPasswordProvider</code>, which is a <code>PasswordProvider</code> implementation that obtains a password by reading data from a specified URL.</p>

<p>For example, an identity manager could be configured with a private key password like this:</p>

<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;identity-manager&gt;
      &lt;key&gt;server.pem&lt;/key&gt;
      &lt;cert&gt;server.cert&lt;/cert&gt;
      &lt;password-url&gt;file:/secrets/key-pass.txt&lt;/password-url&gt;
    &lt;/identity-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

<p>The password for the private key will be obtained from the URL <code>file:/secrets/key-pass.txt</code>, which in this case is a file, but could be any simple URL. The <code>URLPasswordProvider</code> will effectively read all the data returned by <code>new URL("file:/secrets/key-pass.txt").openStream()</code> and use that as the password.</p>

<p>By default, the <code>URLPasswordProvider</code> will use all the data returned from the URL as the password.
This behaviour can be changed to only use the first line of data returned as the password by setting the <code>first-line-only</code> attribute of the <code>password-url</code> to <code>true</code>.</p>

<p>For example:</p>

<markup
lang="xml"

>&lt;password-url first-line-only="true"&gt;file:/secret.txt&lt;/password-url&gt;</markup>

<p>The above configuration will use only the first line of the file <code>/secret.txt</code> as the password.</p>

</div>

<h3 id="_read_a_password_from_an_inputstream">Read a Password From an InputStream</h3>
<div class="section">
<p>The super class of the <code>URLPasswordProvider</code> mentioned above is <code>com.tangosol.net.InputStreamPasswordProvider</code> which reads a password from an <code>InputStream</code>.
This class is abstract, but is a useful base class to extend when writing custom <code>PasswordProvider</code> implementations.
The only method to implement is 'protected InputStream getInputStream()` which returns the <code>InputStream</code> to read the password from.</p>

<p>For example:</p>

<markup
lang="java"
title="CustomPasswordProvider.java"
>package com.acme.coherence;

public class CustomPasswordProvider
        extends InputStreamPasswordProvider
    {
    protected InputStream getInputStream() throws IOException
        {
        InputStream in; // get the password data as an InputStream...
        return in;
        }
    }</markup>

<p>Now the <code>CustomPasswordProvider</code> can be used in configuration.</p>

<markup
lang="xml"

>&lt;socket-provider&gt;
  &lt;ssl&gt;
    &lt;identity-manager&gt;
      &lt;key-store&gt;
        &lt;key&gt;server.pem&lt;/key&gt;
        &lt;cert&gt;server.cert&lt;/cert&gt;
      &lt;/key-store&gt;
      &lt;password-provider&gt;
        &lt;class-name&gt;com.acme.coherence.CustomPasswordProvider&lt;/class-name&gt;
      &lt;/password-provider&gt;
    &lt;/identity-manager&gt;
  &lt;/ssl&gt;
&lt;/socket-provider&gt;</markup>

</div>
</div>

<h2 id="extending">Extending TLS Configuration</h2>
<div class="section">
<p>The core Coherence library only supports loading keystores, keys and certificates from the file system, or from a basic URL. The types of private key and certificate that can be loaded are also restricted to those supported by the JVM. To be able to load data from other sources and support different types of keys and certificates, Coherence provides some extension points in the configuration.</p>


<h3 id="_custom_keystore_loader">Custom KeyStore Loader</h3>
<div class="section">
<p>If using Java Keystores, an instance of a <code>com.tangosol.net.ssl.KeyStoreLoader</code> can be implemented in application code and configured in the <code>&lt;key-store-loader&gt;</code> element, which is a child of the <code>&lt;key-store&gt;</code> element.
This class can load the contents of a Java KeyStore from any desired location.</p>

<markup
lang="java"
title="CustomKeyStoreLoader.java"
>package com.acme.coherence;

public class CustomKeyStoreLoader
        implements KeyStoreLoader
    {
    @Override
    public KeyStore load(String sType, PasswordProvider password)
            throws GeneralSecurityException, IOException
        {
        // return a KeyStore of the required type, based on the value of the sName parameter
        }
    }</markup>

<p>For example, the <code>CustomKeyStoreLoader</code> class above could be configured in the identity manager configuration as shown below.</p>

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

<p>Or the <code>CustomKeyStoreLoader</code> class above could be configured in the trust manager configuration as shown below.</p>

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

<p>The <code>CustomKeyStoreLoader</code> can be refactored to add constructor arguments.
These can then be passed in from configuration.</p>

<markup
lang="java"
title="CustomKeyStoreLoader.java"
>package com.acme.coherence;

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
        // return a KeyStore of the required type, based on the value of the sName parameter
        }
    }</markup>

<p>The parameter can now be added to the configuration:</p>

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

<p>With the above configuration the <code>CustomKeyStoreLoader</code> constructor will be called with the parameters <code>foo</code> and <code>bar</code>.</p>

<p>At runtime the <code>CustomKeyStoreLoader</code> class&#8217;s <code>load</code> method will be called to load the keystore.
In the configurations above the <code>type</code> parameter passed to the load method will be the default keystore type ("JKS").
The <code>PasswordProvider</code> passed to the load method will be the default null implementation that returns an empty password.</p>

<p>Optionally, a keystore type and password may also be configured, which will be passed as parameters
to the custom <code>KeyStoreLoader.load()</code> method:</p>

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

<h3 id="_custom_privatekey_loader">Custom PrivateKey Loader</h3>
<div class="section">
<p>If using keys and certs, an instance of a <code>com.tangosol.net.ssl.PrivateKeyLoader</code> can be implemented in application code and configured in the <code>&lt;key-loader&gt;</code> element.
This class can load a <code>PrivateKey</code> from any desired location in the required format.</p>

<p>As with other extension points in Coherence, the <code>&lt;key-loader&gt;</code> is an "instance" configuration that takes a <code>class-name</code> or a <code>class-factory-name</code> and <code>method-name</code> parameter. Optionally the configuration can also use <code>&lt;init-params&gt;</code> to pass parameters to the class constructor or factory method.</p>

<markup
lang="java"
title="CustomKeyStoreLoader.java"
>package com.acme.coherence;

public class CustomPrivateKeyLoader
        implements PrivateKeyLoader
    {
    @Override
    public PrivateKey load(PasswordProvider password)
            throws GeneralSecurityException, IOException
        {
        // return a PrivateKey
        }
    }</markup>

<p>The custom loader can then be added to the configuration using the <code>&lt;key-loader&gt;</code> element, which is a child of the
For example, the <code>CustomPrivateKeyLoader</code> class above could be configured in the identity manager configuration as shown below.</p>

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

<p>At runtime the <code>CustomPrivateKeyLoader</code> class&#8217;s <code>load</code> method will be called to create the <code>PrivateKey</code> instance.</p>

<p>In the example above there was no password configured for the key, so the <code>PasswordProvider</code> passed to the <code>load</code> method will return an empty password (<code>new char[0]</code>).</p>

<p>A password can be added using one of the password elements allowed in the <code>&lt;identity-manager&gt;</code> elements.</p>

<p>An example configuration with a password might look like the XML below.
In this example, the <code>PasswordProvider</code> will return the contents fetched from the URL <code>file:/coherence/security/key-pass.txt</code> as the key password.</p>

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

<h3 id="_custom_certificate_loader">Custom Certificate Loader</h3>
<div class="section">
<p>If using certificate files in the identity manager or trust manager, an instance of a <code>com.tangosol.net.ssl.CertificateLoader</code> can be implemented in application code and configured in the <code>&lt;cert-loader&gt;</code> element.
This class can load an array of <code>Certificate</code> instances from any desired location in the required format.</p>

<p>As with other extension points in Coherence, the <code>&lt;cert-loader&gt;</code> is an "instance" configuration that takes a <code>class-name</code> or a <code>class-factory-name</code> and <code>method-name</code> parameter. Optionally the configuration can also use <code>&lt;init-params&gt;</code> to pass parameters to the class constructor or factory method.</p>

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
        // return a Certificate array based on the value of the sName parameter
        }
    }</markup>

<p>For example, the <code>CustomCertificateLoader</code> class above could be configured in the identity manager configuration as shown below.</p>

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

<p>Or it can be used in a <code>&lt;trust-manager&gt;</code> configuration:</p>

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

<p>The configuration above will use the <code>server-ca.cert</code> certificate, as well as the certificates provided by the two instances of the <code>CustomCertificateLoader</code>.</p>

</div>
</div>
</doc-view>
