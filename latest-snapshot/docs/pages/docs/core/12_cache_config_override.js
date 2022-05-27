<doc-view>

<h2 id="_cache_configuration_override">Cache Configuration Override</h2>
<div class="section">
<p>Coherence 22.06 introduced cache configuration override similar to the Coherence Cluster override. The same way how Coherence Cluster override file specified, cache configuration override can be specified using "xml-override" attribute in the root element of the cache configuration which can be referred as a "base" cache configuration file whose elements now can be overriden by putting override file in classpath or module path and if the specified XML override file is found on classpath or a module path, it will be loaded by the Coherence at runtime and overrides base configuration based on each module&#8217;s specific requirements.</p>


<h3 id="_coherence_cache_config_xml_snippet_with_xml_override_attribute_specified">Coherence cache config xml snippet with "xml-override" attribute specified</h3>
<div class="section">
<markup
lang="xml"

>    &lt;cache-config xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xmlns="http://xmlns.oracle.com/coherence/coherence-cache-config"
                  xsi:schemaLocation="http://xmlns.oracle.com/coherence/coherence-cache-config coherence-cache-config.xsd"
                  xml-override="{coherence.cacheconfig.override cache-config-override.xml}"&gt;
    ....
    &lt;/cache-config&gt;</markup>

<p>So based on above <code>xml-override</code> attribute specified, <code>cache-config-override.xml</code> is loaded and applied when found on the classpath or a module path of the application when no system property <code>coherence.cacheconfig.override</code> specified. And if <code>coherence.cacheconfig.override</code> is specified then XML override pointed by that system property is loaded and applied at runtime.</p>

</div>

<h3 id="_below_are_different_scenarios_or_uses_cases_for_overriding_coherence_cache_configuration">Below are different scenarios or uses cases for overriding Coherence cache configuration.</h3>
<div class="section">
<ul class="ulist">
<li>
<p>Cache config override has "caching-scheme-mapping/cache-mapping" that already exist in base cache config with same "cache-name"</p>
<div><p>In this case, the "cache-mapping" element within "caching-scheme-mapping" will be overridden from the override file as shown in below example</p>

<ul class="ulist">
<li>
<p>Base xml:</p>
<markup
lang="xml"

>     &lt;cache-mapping&gt;
        &lt;cache-name&gt;my-cache-*&lt;/cache-name&gt;
        &lt;scheme-name&gt;my-cache-scheme-parent&lt;/scheme-name&gt;
     &lt;/cache-mapping&gt;</markup>

</li>
<li>
<p>Override xml:</p>
<markup
lang="xml"

>     &lt;cache-mapping&gt;
        &lt;cache-name&gt;my-cache-*&lt;/cache-name&gt;
        &lt;scheme-name&gt;my-cache-scheme-override&lt;/scheme-name&gt;
     &lt;/cache-mapping&gt;</markup>

</li>
<li>
<p>The effective &lt;caching-scheme-mapping&gt; element after XML override applied to base XML</p>
<markup
lang="xml"

>     &lt;caching-scheme-mapping&gt;
        &lt;cache-mapping&gt;
           &lt;cache-name&gt;my-cache-*&lt;/cache-name&gt;
           &lt;scheme-name&gt;my-cache-scheme-override&lt;/scheme-name&gt;
        &lt;/cache-mapping&gt;
     &lt;/caching-scheme-mapping&gt;</markup>

</li>
</ul></div>

</li>
<li>
<p>Cache config override has "caching-scheme-mapping/cache-mapping" that does not exist in base cache config</p>
<div><p>In this case, new &lt;cache-mapping&gt; element from override file prepend to other elements within "caching-scheme-mapping" as shown in below example</p>

<ul class="ulist">
<li>
<p>Base xml:</p>
<markup
lang="xml"

>     &lt;cache-mapping&gt;
        &lt;cache-name&gt;my-cache-*&lt;/cache-name&gt;
        &lt;scheme-name&gt;my-cache-scheme-parent&lt;/scheme-name&gt;
     &lt;/cache-mapping&gt;</markup>

</li>
<li>
<p>Override xml:</p>
<markup
lang="xml"

>     &lt;cache-mapping&gt;
        &lt;cache-name&gt;my-cache-override&lt;/cache-name&gt;
        &lt;scheme-name&gt;my-cache-scheme-override&lt;/scheme-name&gt;
     &lt;/cache-mapping&gt;</markup>

</li>
<li>
<p>The effective &lt;caching-scheme-mapping&gt; element after above XML override applied to base XML</p>
<markup
lang="xml"

>     &lt;caching-scheme-mapping&gt;
        &lt;cache-mapping&gt;
            &lt;cache-name&gt;my-cache-override&lt;/cache-name&gt;
            &lt;scheme-name&gt;my-cache-scheme-override&lt;/scheme-name&gt;
        &lt;/cache-mapping&gt;
        &lt;cache-mapping&gt;
            &lt;cache-name&gt;my-cache-*&lt;/cache-name&gt;
            &lt;scheme-name&gt;my-cache-scheme-parent&lt;/scheme-name&gt;
        &lt;/cache-mapping&gt;
     &lt;/caching-scheme-mapping&gt;</markup>

</li>
</ul></div>

</li>
<li>
<p>Cache config override has "caching-scheme" with same "scheme-name" that exist in base configuration file</p>
<div><p>Only that "caching-scheme" with the given "scheme-name" will be merged with the relevant content from the override file as shown in below example</p>

<ul class="ulist">
<li>
<p>Base xml content:</p>
<markup
lang="xml"

>    &lt;caching-schemes&gt;
       &lt;distributed-scheme&gt;
          &lt;scheme-name&gt;my-cache-scheme&lt;/scheme-name&gt;
          &lt;service-name&gt;MyCache&lt;/service-name&gt;
          &lt;backing-map-scheme&gt;
             &lt;local-scheme&gt;
                &lt;unit-calculator&gt;BINARY&lt;/unit-calculator&gt;
             &lt;/local-scheme&gt;
          &lt;/backing-map-scheme&gt;
          &lt;partitioned-quorum-policy-scheme&gt;
             &lt;write-quorum&gt;3&lt;/write-quorum&gt;
          &lt;/partitioned-quorum-policy-scheme&gt;
          &lt;autostart&gt;true&lt;/autostart&gt;
       &lt;/distributed-scheme&gt;
       &lt;distributed-scheme&gt;
          &lt;scheme-name&gt;my-cache-scheme-two&lt;/scheme-name&gt;
          &lt;service-name&gt;MyCache2&lt;/service-name&gt;
       &lt;/distributed-scheme&gt;
    &lt;caching-schemes&gt;</markup>

</li>
<li>
<p>Override xml content:</p>
<markup
lang="xml"

>    &lt;caching-schemes&gt;
       &lt;distributed-scheme&gt;
          &lt;scheme-name&gt;my-cache-scheme&lt;/scheme-name&gt;
          &lt;partitioned-quorum-policy-scheme&gt;
             &lt;write-quorum&gt;1&lt;/write-quorum&gt;
          &lt;/partitioned-quorum-policy-scheme&gt;
       &lt;/distributed-scheme&gt;
    &lt;/caching-schemes&gt;</markup>

</li>
<li>
<p>The effective &lt;caching-schemes&gt; element after XML override applied</p>
<markup
lang="xml"

>    &lt;caching-schemes&gt;
       &lt;distributed-scheme&gt;
          &lt;scheme-name&gt;my-cache-scheme&lt;/scheme-name&gt;
          &lt;service-name&gt;MyCache&lt;/service-name&gt;
          &lt;backing-map-scheme&gt;
             &lt;local-scheme&gt;
                &lt;unit-calculator&gt;BINARY&lt;/unit-calculator&gt;
             &lt;/local-scheme&gt;
          &lt;/backing-map-scheme&gt;
          &lt;partitioned-quorum-policy-scheme&gt;
             &lt;write-quorum&gt;1&lt;/write-quorum&gt;
          &lt;/partitioned-quorum-policy-scheme&gt;
          &lt;autostart&gt;true&lt;/autostart&gt;
       &lt;/distributed-scheme&gt;
       &lt;distributed-scheme&gt;
          &lt;scheme-name&gt;my-cache-scheme-two&lt;/scheme-name&gt;
          &lt;service-name&gt;MyCache2&lt;/service-name&gt;
       &lt;/distributed-scheme&gt;
    &lt;caching-schemes&gt;</markup>

</li>
</ul></div>

</li>
<li>
<p>Cache config override with "caching-schemes" without any "scheme-name"</p>
<div><p>All the type of that "caching-scheme" will be modified with the relevant content from the override file as shown in below example</p>

<ul class="ulist">
<li>
<p>Base xml content:</p>
<markup
lang="xml"

>    &lt;caching-schemes&gt;
       &lt;distributed-scheme&gt;
          &lt;scheme-name&gt;my-cache-scheme&lt;/scheme-name&gt;
          &lt;service-name&gt;MyCache&lt;/service-name&gt;
          &lt;backing-map-scheme&gt;
             &lt;local-scheme&gt;
                &lt;unit-calculator&gt;BINARY&lt;/unit-calculator&gt;
             &lt;/local-scheme&gt;
          &lt;/backing-map-scheme&gt;
          &lt;partitioned-quorum-policy-scheme&gt;
             &lt;write-quorum&gt;3&lt;/write-quorum&gt;
          &lt;/partitioned-quorum-policy-scheme&gt;
          &lt;autostart&gt;true&lt;/autostart&gt;
       &lt;/distributed-scheme&gt;
       &lt;distributed-scheme&gt;
          &lt;scheme-name&gt;my-cache-scheme-two&lt;/scheme-name&gt;
          &lt;service-name&gt;MyCache2&lt;/service-name&gt;
       &lt;/distributed-scheme&gt;
    &lt;caching-schemes&gt;</markup>

</li>
<li>
<p>Override xml content:</p>
<markup
lang="xml"

>    &lt;caching-schemes&gt;
       &lt;distributed-scheme&gt;
          &lt;partitioned-quorum-policy-scheme&gt;
             &lt;write-quorum&gt;1&lt;/write-quorum&gt;
          &lt;/partitioned-quorum-policy-scheme&gt;
       &lt;/distributed-scheme&gt;
    &lt;/caching-schemes&gt;</markup>

</li>
<li>
<p>The effective &lt;caching-schemes&gt; element after XML override applied</p>
<markup
lang="xml"

>    &lt;caching-schemes&gt;
       &lt;distributed-scheme&gt;
          &lt;scheme-name&gt;my-cache-scheme&lt;/scheme-name&gt;
          &lt;service-name&gt;MyCache&lt;/service-name&gt;
          &lt;backing-map-scheme&gt;
             &lt;local-scheme&gt;
                &lt;unit-calculator&gt;BINARY&lt;/unit-calculator&gt;
             &lt;/local-scheme&gt;
          &lt;/backing-map-scheme&gt;
          &lt;partitioned-quorum-policy-scheme&gt;
             &lt;write-quorum&gt;1&lt;/write-quorum&gt;
          &lt;/partitioned-quorum-policy-scheme&gt;
          &lt;autostart&gt;true&lt;/autostart&gt;
       &lt;/distributed-scheme&gt;
       &lt;distributed-scheme&gt;
          &lt;scheme-name&gt;my-cache-scheme-two&lt;/scheme-name&gt;
          &lt;service-name&gt;MyCache2&lt;/service-name&gt;
          &lt;partitioned-quorum-policy-scheme&gt;
             &lt;write-quorum&gt;1&lt;/write-quorum&gt;
          &lt;/partitioned-quorum-policy-scheme&gt;
       &lt;/distributed-scheme&gt;
    &lt;caching-schemes&gt;</markup>

</li>
</ul></div>

</li>
</ul>
</div>
</div>
</doc-view>
