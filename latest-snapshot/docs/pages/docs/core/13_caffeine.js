<doc-view>

<h2 id="_caffeine_integration">Caffeine Integration</h2>
<div class="section">
<p>Coherence 22.06 adds <a id="" title="" target="_blank" href="https://github.com/ben-manes/caffeine">Caffeine</a> backing map implementation,
  allowing you to use Caffeine wherever the standard Coherence local cache can be used: as a local cache,
  as a backing map for a partitioned cache, or as a front map for a near cache.</p>


<h3 id="_about_caffeine">About Caffeine</h3>
<div class="section">
<p>Caffeine is a high performance, near optimal caching library. It improves upon Coherence&#8217;s standard local
  cache by offering better read and write concurrency, as well as a higher hit rate.</p>

<p>Caffeine implements an adaptive eviction policy that can achieve a significantly higher hit rate across a large
  variety of workloads. This can be leveraged to either reduce latencies or maintain the same performance with
  smaller caches. That may allow for decreasing the operational costs due to requiring fewer resources for the
  same workload.</p>

<p>The <a id="" title="" target="_blank" href="https://dl.acm.org/doi/10.1145/3274808.3274816">adaptive</a> nature of this policy, nicknamed
  <a id="" title="" target="_blank" href="https://dl.acm.org/doi/10.1145/3149371">W-TinyLFU</a>, allows it to stay robustly performant despite changes in the
  runtime workload. Those changes may be caused by variations in the external request pattern or differences
  caused by the application&#8217;s evolution. This self-optimizing, O(1) algorithm avoids the need to manually analyze
  the application and tune the cache to a more optimal eviction policy.</p>

<p>The following table shows cache hit rates for Caffeine&#8217;s W-TinyLFU vs other commonly used cache eviction policies, for various types of workloads:</p>


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
<th>Workload</th>
<th>W-TinyLFU</th>
<th>Hybrid</th>
<th>LRU</th>
<th>LFU</th>
</tr>
</thead>
<tbody>
<tr>
<td class="">An analytical loop</td>
<td class=""><strong>32.7%</strong></td>
<td class="">2.6%</td>
<td class="">1.0%</td>
<td class="">1.4%</td>
</tr>
<tr>
<td class="">Blockchain mining</td>
<td class="">32.3%</td>
<td class="">12.1%</td>
<td class=""><strong>33.3%</strong></td>
<td class="">0.0%</td>
</tr>
<tr>
<td class="">OLTP</td>
<td class=""><strong>40.2%</strong></td>
<td class="">15.4%</td>
<td class="">33.2%</td>
<td class="">9.6%</td>
</tr>
<tr>
<td class="">Search</td>
<td class=""><strong>42.5%</strong></td>
<td class="">31.3%</td>
<td class="">12.0%</td>
<td class="">29.3%</td>
</tr>
<tr>
<td class="">Database</td>
<td class=""><strong>44.8%</strong></td>
<td class="">37.0%</td>
<td class="">20.2%</td>
<td class="">39.1%</td>
</tr>
</tbody>
</table>
</div>
<p>For more in-depth introduction to Caffeine, we strongly recommend Ben Manes' articles on HighScalability.com:</p>

<ol style="margin-left: 15px;">
<li>
<a id="" title="" target="_blank" href="http://highscalability.com/blog/2016/1/25/design-of-a-modern-cache.html">Design of a Modern Cache, Part 1</a>

</li>
<li>
<a id="" title="" target="_blank" href="http://highscalability.com/blog/2019/2/25/design-of-a-modern-cachepart-deux.html">Design of a Modern Cache, Part 2</a>

</li>
</ol>
</div>

<h3 id="_using_caffeine">Using Caffeine</h3>
<div class="section">
<p>Caffeine is integrated tightly into the Coherence, and is almost as easy to use as any of the built-in backing map
  implementations Coherence provides. The only difference is that it requires that you add dependency on Caffeine
  to your project&#8217;s POM file, as it is defined as an optional dependency within Coherence POM.</p>


<h4 id="_adding_a_dependency_on_caffeine">Adding a Dependency on Caffeine</h4>
<div class="section">
<p>To be able to use Caffeine, you need to add the following dependency to your POM file:</p>

<markup
lang="xml"

>&lt;dependency&gt;
  &lt;groupId&gt;com.github.ben-manes.caffeine&lt;/groupId&gt;
  &lt;artifactId&gt;caffeine&lt;/artifactId&gt;
  &lt;version&gt;${caffeine.version}&lt;/version&gt;
&lt;/dependency&gt;</markup>

<p>The supported Caffeine versions are <code>3.1.0</code> or higher.</p>

</div>

<h4 id="_caffeine_configuration">Caffeine Configuration</h4>
<div class="section">
<p>Once the dependency above is added, Caffeine is as easy to use as a standard local cache implementation.</p>

<p>Coherence provides <code>caffeine-scheme</code> configuration element, which can be used anywhere the <code>local-scheme</code> element
  is currently used: standalone, as a definition of a local cache scheme, within <code>distributed-scheme</code> element as
  a <code>backing-map</code> for a partitioned cache, or within <code>near-scheme</code> element as a <code>front-map</code>.</p>


<h5 id="_local_cache">Local Cache</h5>
<div class="section">
<markup
lang="xml"

> &lt;caffeine-scheme&gt;
    &lt;scheme-name&gt;caffeine-local-scheme&lt;/scheme-name&gt;
 &lt;/caffeine-scheme&gt;</markup>

</div>

<h5 id="_distributed_cache">Distributed Cache</h5>
<div class="section">
<markup
lang="xml"

>&lt;distributed-scheme&gt;
    &lt;scheme-name&gt;caffeine-distributed-scheme&lt;/scheme-name&gt;
    &lt;backing-map-scheme&gt;
        &lt;caffeine-scheme /&gt;
    &lt;/backing-map-scheme&gt;
    &lt;autostart&gt;true&lt;/autostart&gt;
&lt;/distributed-scheme&gt;</markup>

</div>

<h5 id="_near_cache">Near Cache</h5>
<div class="section">
<markup
lang="xml"

>&lt;near-scheme&gt;
    &lt;scheme-name&gt;caffeine-near-scheme&lt;/scheme-name&gt;
    &lt;front-scheme&gt;
        &lt;caffeine-scheme /&gt;
    &lt;/front-scheme&gt;
    &lt;back-scheme&gt;
        &lt;distributed-scheme&gt;
            &lt;scheme-ref&gt;my-dist-scheme&lt;/scheme-ref&gt;
        &lt;/distributed-scheme&gt;
    &lt;/back-scheme&gt;
 &lt;/near-scheme&gt;</markup>

<p>Each of the <code>caffeine-scheme</code> elements above can be further configured the same way <code>local-scheme</code> is configured,
  by specifying one or more of the following child elements:</p>


<div class="table__overflow elevation-1  ">
<table class="datatable table">
<colgroup>
<col style="width: 50%;">
<col style="width: 50%;">
</colgroup>
<thead>
<tr>
<th>Configuration Element</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td class=""><code>scheme-name</code></td>
<td class="">The name of this scheme, which can be referenced elsewhere in the configuration file.</td>
</tr>
<tr>
<td class=""><code>scheme-ref</code></td>
<td class="">The reference (by name) to a <code>caffeine-scheme</code> defined elsewhere in the configuration file.</td>
</tr>
<tr>
<td class=""><code>class-name</code></td>
<td class="">The name of the custom class that extends <code>com.oracle.coherence.caffeine.CaffeineCache</code>,
 allowing you to customize its behavior.</td>
</tr>
<tr>
<td class=""><code>scope-name</code></td>
<td class="">The name of the scope.</td>
</tr>
<tr>
<td class=""><code>service-name</code></td>
<td class="">The name of the service.</td>
</tr>
<tr>
<td class=""><code>init-params</code></td>
<td class="">The arguments to pass to the <code>class-name</code> constructor.</td>
</tr>
<tr>
<td class=""><code>high-units</code></td>
<td class="">The maximum amount of data the cache should be allowed to hold before the eviction occurs.</td>
</tr>
<tr>
<td class=""><code>unit-calculator</code></td>
<td class="">The unit calculator to use, typically either <code>BINARY</code>, which determines the number of "units"
 based on the number of bytes that the serialized form of cache keys and values consume, or <code>FIXED</code>
 which simply uses the number of entries as "units".</td>
</tr>
<tr>
<td class=""><code>unit-factor</code></td>
<td class="">Sometimes used in combination with a <code>BINARY</code> calculator to overcome 2 GB limit for "units". For example,
 specifying <code>1024</code> as a "unit factor" allows you to express <code>high-units</code> in kilobytes instead of in bytes.</td>
</tr>
<tr>
<td class=""><code>expiry-delay</code></td>
<td class="">The amount of time from last update the entries will be kept in cache before being discarded.</td>
</tr>
<tr>
<td class=""><code>listener</code></td>
<td class="">A <code>MapListener</code> to register with the cache.</td>
</tr>
</tbody>
</table>
</div>
<p>All of the configuration elements above are optional, but you will typically want to set either
  <code>high-units</code> or <code>expiry-delay</code> (or both) to limit cache based on either size or time-to-live (TTL).</p>

<p>If neither is specified, the cache size will be limited only by available memory, and the TTL can
  be specified explicitly using <code>NamedCache.put(key, value, ttl)</code> method, or by calling <code>BinaryEntry.expire</code>
  within an entry processor.</p>

<p>Of course, there is nothing wrong with not limiting the cache by either size or time, and you may still
  benefit from using Caffeine in those situations, especially under high concurrent load, due to its support
  for lock-free reads and fine-grained locking on writes.</p>

<p>Finally, when using Caffeine as a backing map for a partitioned cache, you will likely want to configure
  <code>unit-calculator</code> to <code>BINARY</code>, so you can set the limits and observe cache size (via JMX or Metrics) in
  bytes instead of the number of entries in the cache.</p>

</div>
</div>
</div>
</div>
</doc-view>
