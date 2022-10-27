<doc-view>

<h2 id="_partition_backup_enhancements">Partition Backup Enhancements</h2>
<div class="section">
<p>Coherence partitioned caches maintain primary and backup partitions. The backup partitions
allow the application to simultaneously lose <code>backup-count</code> number of members. These backups
are kept as 'strong' as possible by ensuring they are on different machines, racks or sites.
In addition customers can choose to forgo backup synchronicity for write performance by enabling
asynchronous backups.</p>

<p>Described below are two features introduced in <code>21.12</code> that either capitalize on these backups to
improve read throughput and/or latency, or optimize the asynchronous processing to increase write
throughput. These feature are called 'Read Locator' and 'Scheduled Backups' respectively.</p>

</div>

<h2 id="_read_locator">Read Locator</h2>
<div class="section">
<p>Prior to this change all Coherence <a id="" title="" target="_blank" href="https://coherence.community/21.12.6/api/java//com/tangosol/net/NamedMap.html">NamedMap</a> requests are serviced by the primary owner of
the associated partition(s) (ignoring client side caches, i.e. <a id="" title="" target="_blank" href="https://coherence.community/21.12.6/api/java//com/tangosol/net/cache/NearCache.html">NearCache</a> / <a id="" title="" target="_blank" href="https://coherence.community/21.12.6/api/java//com/tangosol/net/cache/ContinuousQueryCache.html">CQC</a>). The <code>read-locator</code>
feature allows for certain requests (currently only <a id="" title="" target="_blank" href="https://coherence.community/21.12.6/api/java//com/tangosol/util/ConcurrentMap.html#get(java.lang.Object)">NamedMap.get</a> or <a id="" title="" target="_blank" href="https://coherence.community/21.12.6/api/java//com/tangosol/net/NamedMap.html#getAll(java.util.Collection)">NamedMap.getAll</a>) to be targetted to non-primary
partition owners (backups) to balance request load or reduce latency. If the application chooses to
target a non-primary partition owner there is an implied tolerance for stale reads. This may be
possible as the primary (or other backups) process future/in-flight changes while the targeted member
that performed the read has not.</p>

<p>Coherence now provides an ability for applications to choose the appropriate <code>read-locator</code> for a cache
or service via the cache configuration, as highlighted below:</p>

<markup
lang="xml"

>    ...
    &lt;distributed-scheme&gt;
      &lt;scheme-name&gt;example-distributed&lt;/scheme-name&gt;
      &lt;service-name&gt;DistributedCache&lt;/service-name&gt;
      &lt;backing-map-scheme&gt;
          &lt;read-locator&gt;closest&lt;/read-locator&gt;
      &lt;/backing-map-scheme&gt;
      &lt;autostart&gt;true&lt;/autostart&gt;
    &lt;/distributed-scheme&gt;
    ...</markup>

<p>The following <code>read-locator</code> values are supported:</p>

<ul class="ulist">
<li>
<p><code>primary</code> - (default) target the request to the primary only.</p>

</li>
<li>
<p><code>closest</code> - find the 'closest' owner based on the machine, rack or site information for each member
in the partition&#8217;s ownership chain (primary &amp; backups).</p>

</li>
<li>
<p><code>random</code> - pick a random owner in the partition&#8217;s ownership chain.</p>

</li>
<li>
<p><code>random-backup</code> - pick a random backup owner in the partition&#8217;s ownership chain.</p>

</li>
<li>
<p><code>class-scheme</code> - provide your own implementation that receives the ownership chain and returns the member to target</p>

</li>
</ul>
</div>

<h2 id="_scheduled_backups">Scheduled Backups</h2>
<div class="section">
<p>As mentioned previously, Coherence provides an ability for applications to favor write throughput
over coherent backup copies (<code>async-backup</code>). This can result in acknowledged write requests being lost if
they were not successfully backed up; acknowledgement comes in the form of control being returned
when using the synchronous API against a mutating method (<a id="" title="" target="_blank" href="https://coherence.community/21.12.6/api/java//com/tangosol/util/ConcurrentMap.html#put(K,V)">put</a> / <a id="" title="" target="_blank" href="https://coherence.community/21.12.6/api/java//com/tangosol/util/InvocableMap.html#invoke(K,com.tangosol.util.InvocableMap.EntryProcessor)">invoke</a>), or receiving a notification
of the completion of a write request via the asynchronous API.</p>

<p>Internally this still results in <code>n</code> backup messages being created for <code>n</code> write requests, which
has a direct impact on write throughput. To improve write throughput Coherence now supports
"Scheduled" (or periodic) backups, thus allowing the number of backup messages to be <code>&lt; n</code>.</p>

<p>The existing <code>async-backup</code> XML element has been augmented to accept more than a simple <code>true|false</code>
value and now supports a time-based value. This allows applications to suggest a soft target of how long
they are willing to tolerate stale backups. Coherence at runtime may decide to accelerate
backup synchronicity, or increase the staleness based on primary write throughput.</p>

<div class="admonition note">
<p class="admonition-textlabel">Note</p>
<p ><p>Care must be taken when choosing the backup interval, since there is a potential for losing
updates in the event of losing a primary partition owner. All the updates waiting to
be sent by that primary will not be reflected when the corresponding backup owner is restored
and becomes primary.</p>
</p>
</div>

<h3 id="_example_configuration">Example Configuration</h3>
<div class="section">
<p>The following distributed scheme contains an example of setting the scheduled backup interval
of ten seconds:</p>

<markup
lang="xml"

>    ...
    &lt;distributed-scheme&gt;
      &lt;scheme-name&gt;example-distributed&lt;/scheme-name&gt;
      &lt;service-name&gt;DistributedCache&lt;/service-name&gt;
      &lt;autostart&gt;true&lt;/autostart&gt;
      &lt;async-backup&gt;10s&lt;/async-backup&gt;
    &lt;/distributed-scheme&gt;
    ...</markup>

<p>A default system property can also be used, and will take effect on all distributed schemes used,
e.g.:</p>

<markup
lang="text"

>-Dcoherence.distributed.asyncbackup=10s</markup>

</div>
</div>
</doc-view>
