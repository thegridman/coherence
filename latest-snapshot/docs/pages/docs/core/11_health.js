<doc-view>

<h2 id="_health_check_api">Health Check API</h2>
<div class="section">
<p>Coherence 22.06 introduced a health check API and corresponding http and management endpoints to allow external
applications to query the health of a cluster and its members.
The health API also allows applications to register their own health checks that will then be included in the cluster&#8217;s health status.</p>

<p>All health checks in Coherence implement a simple interface.</p>

<markup
lang="java"
title="HealthCheck.java"
>public interface HealthCheck
    {
    /**
     * Returns the unique name of this health check.
     *
     * @return the unique name of this health check
     */
    String getName();

    /**
     * Return {@code true} if this {@link HealthCheck} should
     * be included when working out this Coherence member's
     * health status.
     *
     * @return {@code true} if this {@link HealthCheck} should
     *         be included in the member's health status
     */
    default boolean isMemberHealthCheck()
        {
        return true;
        }

    /**
     * Returns {@link true} if the resource represented by
     * this {@link HealthCheck} is ready, otherwise returns
     * {@code false}.
     * &lt;p&gt;
     * The concept of what "ready" means may vary for different
     * types of resources.
     *
     * @return {@link true} if the resource represented by this
     *         {@link HealthCheck} is ready, otherwise {@code false}
     */
    boolean isReady();

    /**
     * Returns {@link true} if the resource represented by
     * this {@link HealthCheck} is alive, otherwise returns
     * {@code false}.
     * &lt;p&gt;
     * The concept of what "alive" means may vary for different
     * types of resources.
     *
     * @return {@link true} if the resource represented by this
     *         {@link HealthCheck} is alive, otherwise returns
     *         {@code false}
     */
    boolean isLive();

    /**
     * Returns {@link true} if the resource represented by
     * this {@link HealthCheck} is started, otherwise returns
     * {@code false}.
     * &lt;p&gt;
     * The concept of what "started" means may vary for different
     * types of resources.
     *
     * @return {@link true} if the resource represented by this
     *         {@link HealthCheck} is started, otherwise returns
     *         {@code false}
     */
    boolean isStarted();

    /**
     * Returns {@link true} if the resource represented by this
     * {@link HealthCheck} is in a safe state to allow a rolling
     * upgrade to proceed, otherwise returns {@code false}.
     * &lt;p&gt;
     * The concept of what "safe" means may vary for different
     * types of resources.
     *
     * @return {@link true} if the resource represented by this
     *         {@link HealthCheck} is in a safe state to allow
     *         a rolling upgrade to proceed, otherwise returns
     *         {@code false}
     */
    boolean isSafe();
    }</markup>

<p>The methods were specifically chosen to integrate with other systems where Coherence is run, for example Kubernetes, that plug in to similar, "started", "live" and "ready" health checks. The "safe" check is specific to Coherence to be used for controlling use-cases such as rolling upgrades, where it is important to know a cluster is "safe" before rolling the next cluster member.</p>


<h3 id="_built_in_health_checks">Built-in Health Checks</h3>
<div class="section">
<p>Coherence has a number of health checks that are enabled out of the box.
Each Coherence service has a corresponding health check.
Instances of <code>com.tangosol.net.Coherence</code> provide a corresponding health check.</p>


<h4 id="_service_health_checks">Service Health Checks</h4>
<div class="section">
<p>For Coherence services heath checks have the following functionality.</p>

<ul class="ulist">
<li>
<p>Started: The <code>isStarted()</code> method for a service health check will return <code>true</code> if the corresponding service is running.</p>

</li>
<li>
<p>Live: The <code>isLive()</code> method for a service health check will return <code>true</code> if the corresponding service is running.</p>

</li>
<li>
<p>Ready: For a service, the <code>isReady()</code> method will return <code>false</code> until a service becomes "safe", after which the "ready" state will remain <code>true</code> until the service is stopped.</p>

</li>
<li>
<p>Safe: For all services except a partitioned cache service, the <code>isSafe()</code> method will always return <code>true</code>.</p>

</li>
</ul>
</div>

<h4 id="_the_partitionedcache_service_issafe_check">The PartitionedCache Service isSafe Check</h4>
<div class="section">
<p>The <code>isSafe()</code> check for a <code>PartitionedCache</code> service performs a number of checks to ensure the service is stable and safe. The main use-cases for the "safe" check are when performing a rolling upgrade, or safely scaling down a cluster.</p>

<ul class="ulist">
<li>
<p>The <code>isSafe()</code> health check for a <code>PartitionedCache</code> service on a non-storage enabled member will return <code>true</code> as long as the service is runnning.</p>

</li>
<li>
<p>The <code>isSafe()</code> health check for a <code>PartitionedCache</code> service will return <code>false</code> if this member is the only storage enabled member for the service, but does not own all the partitions. This can happen just after all the other members of the cluster have been stopped but the partition recovery and reallocation logic is still in progress, so this member does not yet know it owns all the partitions.</p>

</li>
<li>
<p>The <code>isSafe()</code> health check for a <code>PartitionedCache</code> service will return <code>false</code> if the backup count is configured to be greater than zero and the StatusHA state for the service is <code>endangered</code>. This behaviour can be changed for individual services in the cache configuration file to allow them to be endangered. A service with a backup count of zero is allowed to be endangered for the safe check.</p>

</li>
<li>
<p>The <code>isSafe()</code> health check for a <code>PartitionedCache</code> service will return <code>false</code> if partition redistribution is in progress.</p>

</li>
<li>
<p>The <code>isSafe()</code> health check for a <code>PartitionedCache</code> service will return <code>false</code> if recovery from persistent storage is in progress.</p>

</li>
</ul>
</div>
</div>

<h3 id="_the_health_check_api">The Health Check API</h3>
<div class="section">
<p>The health check API is part of the Coherence management APIs and can be accessed from the <code>com.tangosol.net.management.Registry</code> class. The <code>Resistry</code> is typically obtained from the current Coherence <code>Cluster</code> instance.</p>

<p>For example, when Coherence has been started by running <code>com.tangosol.net.Coherence.main()</code>, or by using the bootstrap API, the management <code>Registry</code> can be obtained as shown below.</p>

<markup
lang="java"

>Cluster  cluster  = Coherence.getInstance().getCluster();
Registry registry = cluster.getManagement();</markup>


<h4 id="_obtain_all_healthchecks">Obtain All HealthChecks</h4>
<div class="section">
<p>To obtain a collection of all the registered health checks, the <code>getHealthChecks()</code> can be called on the <code>Registry</code> instance. This method returns an immutable collection of registered <code>HealthCheck</code> instances.</p>

<p>For example, the code below obtains a <code>Set</code> of names of <code>HealthCheck</code> instances that are not ready:</p>

<markup
lang="java"

>Cluster  cluster  = Coherence.getInstance().getCluster();
Registry registry = cluster.getManagement();
Collection&lt;HealthCheck&gt; healthChecks = registry.getHealthChecks();
Set&lt;String&gt; names = healthChecks.stream()
        .filter(hc -&gt; !hc.isReady())
        .map(HealthCheck::getName)
        .collect(Collectors.toSet());</markup>

</div>

<h4 id="_check_all_healthchecks_are_ready">Check all HealthChecks are Ready</h4>
<div class="section">
<p>The <code>allHealthChecksReady()</code> method on the <code>Registry</code> instance can be used to determine whether all registered health checks are ready.</p>

<markup
lang="java"

>Cluster  cluster  = Coherence.getInstance().getCluster();
Registry registry = cluster.getManagement();
boolean ready = registry.allHealthChecksReady();</markup>

</div>

<h4 id="_check_all_healthchecks_are_started">Check all HealthChecks are Started</h4>
<div class="section">
<p>The <code>allHealthChecksStarted()</code> method on the <code>Registry</code> instance can be used to determine whether all registered health checks are started.</p>

<markup
lang="java"

>Cluster  cluster  = Coherence.getInstance().getCluster();
Registry registry = cluster.getManagement();
boolean started = registry.allHealthChecksStarted();</markup>

</div>

<h4 id="_check_all_healthchecks_are_live">Check all HealthChecks are Live</h4>
<div class="section">
<p>The <code>allHealthChecksLive()</code> method on the <code>Registry</code> instance can be used to determine whether all registered health checks are live.</p>

<markup
lang="java"

>Cluster  cluster  = Coherence.getInstance().getCluster();
Registry registry = cluster.getManagement();
boolean live = registry.allHealthChecksLive();</markup>

</div>

<h4 id="_check_all_healthchecks_are_safe">Check all HealthChecks are Safe</h4>
<div class="section">
<p>The <code>allHealthChecksSafe()</code> method on the <code>Registry</code> instance can be used to determine whether all registered health checks are safe.</p>

<markup
lang="java"

>Cluster  cluster  = Coherence.getInstance().getCluster();
Registry registry = cluster.getManagement();
boolean safe = registry.allHealthChecksSafe();</markup>

</div>
</div>

<h3 id="_application_health_checks">Application Health Checks</h3>
<div class="section">
<p>The health check API allows application developers to add custom health checks. This can be useful where an application provides a service that should be used to determine the overall health of a Coherence member. For example, an application could include a web server and should not be considered "ready" until the web server is started.</p>

<p>To register a custom health check, just write an implementation of <code>com.tangosol.util.HealthCheck</code>.</p>

<p>The <code>getName()</code> method for the custom health check should return a unique name that represents this health check. As health checks are exposed as MBeans the name must be a name that is valid in a JMX MBean object name.</p>

<p>The health check implementation should then use relevant application logic to determine the result to return for each of the methods. Some methods may not apply, in which case they should just return <code>true</code>.</p>

<p>It is important to understand how the results of the different health check methods will be used outside the application code. For example, when the application is deployed and managed by an external system that monitors application health. For example, an application deployed into Kubernetes could be killed if it reports not being "live" too many times. An application that does not report being "ready" may be excluded from request routing, etc. An application that is not "safe" will block rolling upgrades or safe scaling of a Coherence cluster.</p>


<h4 id="_excluding_custom_healthchecks_from_member_health">Excluding Custom HealthChecks from Member Health</h4>
<div class="section">
<p>An application developer may want to add custom health checks for application services, but not have these checks impact the overall Coherence member health. The <code>HealthCheck</code> interface has a <code>isMemberHealthCheck()</code> method for this purpose. The default implementation of <code>isMemberHealthCheck()</code> always returns <code>true</code>, so by default all health checks are included in the member&#8217;s health. To exclude a health check from the member&#8217;s health, override the <code>isMemberHealthCheck()</code> method and return <code>false</code>.</p>

</div>
</div>

<h3 id="_enabling_http_health_checks">Enabling HTTP Health Checks</h3>
<div class="section">
<p>The health check http endpoints are enabled when Coherence is run using the bootstrap API, or starting Coherence using <code>com.tangosol.net.Coherence.main()</code>. If Coherence is started by any other method, the health check API is still available, but the http endpoints will not be running.
By default, the http server will bind to an ephemeral port, but this can be changed by setting the <code>coherence.health.http.port</code> system property.</p>

<p>For example, running the following command will start Coherence with the health endpoints on <a id="" title="" target="_blank" href="http://localhost:6676">http://localhost:6676</a></p>

<markup
lang="bash"

>java -cp coherence.jar -Dcoherence.health.http.port=6676 com.tangosol.net.Coherence</markup>

<p>or with Java modules</p>

<markup
lang="bash"

>java -p coherence/target/coherence-14.1.2-0-0-SNAPSHOT.jar -Dcoherence.health.http.port=6676 --module com.oracle.coherence</markup>

<p>The <code>curl</code> utility can then be used to poll one of the endpoints, for example <code>/ready</code>:</p>

<markup
lang="bash"

>curl -i -X GET http://localhost:6676/ready</markup>

<p>Which returns output like the following</p>

<markup
lang="bash"

>HTTP/1.1 200 OK
Date: Tue, 19 Apr 2022 17:59:05 GMT
Content-type: application/json
Vary: Accept-Encoding
Content-length: 0
X-content-type-options: nosniff</markup>

<p>If Coherence the health check had failed, the response code would have been 503, for "service unavailable".</p>


<h4 id="_health_http_endpoints">Health HTTP Endpoints</h4>
<div class="section">
<p>The health check http server has a number of endpoints. None of the endpoints return a response body, the status code is all that is provided to check health.</p>


<div class="table__overflow elevation-1  ">
<table class="datatable table">
<colgroup>
<col style="width: 50%;">
<col style="width: 50%;">
</colgroup>
<thead>
<tr>
<th>Endpoint</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td class=""><code>/started</code></td>
<td class="">This endpoint will return a 200 response if all the health checks for the member the request is sent to are "started".
If one or more health check is not started, a 503 response will be returned.</td>
</tr>
<tr>
<td class=""><code>/live</code></td>
<td class="">This endpoint will return a 200 response if all the health checks for the member the request is sent to are "live".
If one or more health check is not live, a 503 response will be returned.</td>
</tr>
<tr>
<td class=""><code>/ready</code></td>
<td class="">This endpoint will return a 200 response if all the health checks for the member the request is sent to are "ready".
If one or more health check is not ready, a 503 response will be returned.</td>
</tr>
<tr>
<td class=""><code>/safe</code></td>
<td class="">This endpoint will return a 200 response if all the health checks for the member the request is sent to are "safe".
If one or more health check is not safe, a 503 response will be returned.</td>
</tr>
</tbody>
</table>
</div>
</div>
</div>
</div>
</doc-view>
