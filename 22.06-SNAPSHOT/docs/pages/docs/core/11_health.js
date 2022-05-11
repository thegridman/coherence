<doc-view>

<h2 id="_health_check_api">Health Check API</h2>
<div class="section">
<p>Coherence 22.06 introduced a health check API to allow application code to determine the health of the local Coherence member, and corresponding http and management endpoints to allow external
applications to query the health of a cluster and its members.
The health API also allows applications to register their own health checks that will then be included in the member&#8217;s and cluster&#8217;s health status. The health check API can be used from application code, to determine whether Coherence is healthy, and also from a http endpoint making it useful for health checks in containerized environments such as Kubernetes and Docker.</p>

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

<p>The methods were specifically chosen to integrate with other systems where Coherence is run, for example Kubernetes, that use similar, "started", "live" and "ready" health checks. The "safe" check is specific to Coherence to be used for controlling use-cases such as rolling upgrades, where it is important to know a cluster is "safe" before rolling the next cluster member.</p>


<h3 id="_the_health_check_api">The Health Check API</h3>
<div class="section">
<p>The health check API is part of the Coherence management APIs and can be accessed from the <code>com.tangosol.net.management.Registry</code> class. The <code>Registry</code> is typically obtained from the current Coherence <code>Cluster</code> instance.</p>

<p>For example, when Coherence has been started by running <code>com.tangosol.net.Coherence.main()</code>, or by using the bootstrap API, the management <code>Registry</code> can be obtained as shown below.</p>

<markup
lang="java"

>Cluster  cluster  = Coherence.getInstance().getCluster();
Registry registry = cluster.getManagement();</markup>

<p>The health check API can only see registered health checks for the local Coherence member, it is not a cluster wide API. For cluster wide health checks, use the corresponding health MBeans via the Coherence management API, JMX, or management over REST.</p>


<h4 id="_obtain_all_healthchecks">Obtain All HealthChecks</h4>
<div class="section">
<p>To obtain a collection of all the registered health checks, the <code>getHealthChecks()</code> method can be called on the <code>Registry</code> instance. This method returns an immutable collection of registered <code>HealthCheck</code> instances.</p>

<p>For example, the code below obtains a <code>Set</code> of names of <code>HealthCheck</code> instances that are not ready:</p>

<markup
lang="java"

>Coherence coherence = Coherence.getInstance();
Registry registry = coherence.getManagement();
Collection&lt;HealthCheck&gt; healthChecks = registry.getHealthChecks();
Set&lt;String&gt; names = healthChecks.stream()
        .filter(hc -&gt; !hc.isReady())
        .map(HealthCheck::getName)
        .collect(Collectors.toSet());</markup>

</div>

<h4 id="_obtain_a_healthcheck_by_name">Obtain a HealthCheck by Name</h4>
<div class="section">
<p>To obtain a specific health check by name, the <code>getHealthCheck(String name)</code> method can be called on the <code>Registry</code> instance. This method returns an <code>Optional</code> containing the requested <code>HealthCheck</code>, if one has been
registered with the requested name, or returning an empty <code>Optional</code> if no <code>HealthCheck</code> has been registered with
the request name.</p>

<p>For example, the code below obtains gets the <code>HealthCheck</code> with the name "Foo":</p>

<markup
lang="java"

>Coherence coherence = Coherence.getInstance();
Registry registry = coherence.getManagement();
Optional&lt;HealthCheck&gt; healthChecks = registry.getHealthCheck("Foo");</markup>

</div>

<h4 id="_check_all_healthchecks_are_ready">Check all HealthChecks are Ready</h4>
<div class="section">
<p>The <code>allHealthChecksReady()</code> method on the <code>Registry</code> instance can be used to determine whether all locally registered health checks are ready. Only health checks that return <code>true</code> from their <code>isMemberHealthCheck()</code>
method are included in the ready check.</p>

<markup
lang="java"

>Coherence coherence = Coherence.getInstance();
Registry registry = coherence.getManagement();
boolean ready = registry.allHealthChecksReady();</markup>

</div>

<h4 id="_check_all_healthchecks_are_started">Check all HealthChecks are Started</h4>
<div class="section">
<p>The <code>allHealthChecksStarted()</code> method on the <code>Registry</code> instance can be used to determine whether all locally registered health checks are started. Only health checks that return <code>true</code> from their <code>isMemberHealthCheck()</code>
method are included in the started check</p>

<markup
lang="java"

>Coherence coherence = Coherence.getInstance();
Registry registry = coherence.getManagement();
boolean started = registry.allHealthChecksStarted();</markup>

</div>

<h4 id="_check_all_healthchecks_are_live">Check all HealthChecks are Live</h4>
<div class="section">
<p>The <code>allHealthChecksLive()</code> method on the <code>Registry</code> instance can be used to determine whether all locally registered health checks are live. Only health checks that return <code>true</code> from their <code>isMemberHealthCheck()</code>
method are included in the live check</p>

<markup
lang="java"

>Coherence coherence = Coherence.getInstance();
Registry registry = coherence.getManagement();
boolean live = registry.allHealthChecksLive();</markup>

</div>

<h4 id="_check_all_healthchecks_are_safe">Check all HealthChecks are Safe</h4>
<div class="section">
<p>The <code>allHealthChecksSafe()</code> method on the <code>Registry</code> instance can be used to determine whether all locally registered health checks are safe. Only health checks that return <code>true</code> from their <code>isMemberHealthCheck()</code>
method are included in the safe check</p>

<markup
lang="java"

>Coherence coherence = Coherence.getInstance();
Registry registry = coherence.getManagement();
boolean safe = registry.allHealthChecksSafe();</markup>

</div>
</div>

<h3 id="_built_in_health_checks">Built-in Health Checks</h3>
<div class="section">
<p>Coherence has a number of health checks that are enabled out of the box.</p>

<ul class="ulist">
<li>
<p>Each Coherence service has a corresponding health check.</p>

</li>
<li>
<p>Instances of <code>com.tangosol.net.Coherence</code> provide a corresponding health check.</p>

</li>
<li>
<p>When using Coherence gRPC integrations, the gRPC proxy server has a health check.</p>

</li>
</ul>

<h4 id="_service_health_checks">Service Health Checks</h4>
<div class="section">
<p>For Coherence services, heath checks have the following functionality.</p>

<ul class="ulist">
<li>
<p>Started: The <code>isStarted()</code> method for a service health check will return <code>true</code> if the corresponding service is running.</p>

</li>
<li>
<p>Live: The <code>isLive()</code> method for a service health check will return <code>true</code> if the corresponding service is running.</p>

</li>
<li>
<p>Ready: For a service, the <code>isReady()</code> method will return <code>false</code> until a service becomes "safe", after which the "ready" state will remain <code>true</code>. This is specifically for use cases such as Kubernetes, where Pods will be removed from a <code>Service</code> if not <code>Ready</code> but this behaviour is typically not required for Coherence.</p>

</li>
<li>
<p>Safe: For all services except a partitioned cache service, the <code>isSafe()</code> method will always return <code>true</code>.</p>

</li>
</ul>
</div>

<h4 id="_the_partitionedcache_service_issafe_check">The PartitionedCache Service isSafe Check</h4>
<div class="section">
<p>A Coherence PartitionedCache service is more complex that most services in Coherence, and as such, its health checks also do more.
The <code>isSafe()</code> check for a <code>PartitionedCache</code> service performs a number of checks to ensure the service is stable and safe. The main use-cases for the "safe" check are when performing a rolling upgrade, or safely scaling down a cluster.</p>

<ul class="ulist">
<li>
<p>The <code>isSafe()</code> health check for a <code>PartitionedCache</code> service on a non-storage enabled member will return <code>true</code> as long as the service is running.</p>

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

<h4 id="_exclude_services_from_member_health">Exclude Services from Member Health</h4>
<div class="section">
<p>Sometimes it may be desirable to exclude a Coherence service from the member&#8217;s overall health check.
This can be done by setting the <code>&lt;member-health-check&gt;</code> element in service&#8217;s <code>&lt;health&gt;</code> element in the cache configuration file.</p>

<p>For example, the <code>proxy-scheme</code> below has the <code>&lt;member-health-check&gt;</code> element value set to <code>false</code>.
The health checks for the <code>Proxy</code> service will still be accessible via the health API, but checks of the overall member health, such as the <code>Registry</code> class&#8217;s <code>allHealthChecksReady()</code> method will not include the <code>Proxy</code> service.</p>

<markup
lang="xml"
title="cache-configuration.xml"
>&lt;proxy-scheme&gt;
  &lt;service-name&gt;Proxy&lt;/service-name&gt;
  &lt;autostart&gt;true&lt;/autostart&gt;
  &lt;health&gt;
    &lt;member-health-check&gt;false&lt;/member-health-check&gt;
  &lt;/health&gt;
&lt;/proxy-scheme&gt;</markup>

</div>

<h4 id="_allowing_endangered_services">Allowing Endangered Services</h4>
<div class="section">
<p>Sometimes an application may configure a distributed cache service that can intentionally become endangered, but this state should not be reflected in the member&#8217;s overall health.
This can be done by setting the <code>&lt;allow-endangered&gt;</code> element in distributed scheme&#8217;s <code>&lt;health&gt;</code> element in the cache configuration file.</p>

<p>For example, the <code>distributed-scheme</code> below has the <code>&lt;allow-endangered&gt;</code> element value set to <code>true</code>.
The health checks for the <code>PartitionedCache</code> will report that the service is "ready" or "safe" even if the Status HA value for the service is ENDANGERED.</p>

<markup
lang="xml"
title="cache-configuration.xml"
>&lt;distributed-scheme&gt;
  &lt;scheme-name&gt;distributed-scheme&lt;/scheme-name&gt;
  &lt;service-name&gt;PartitionedCacheOne&lt;/service-name&gt;
  &lt;backing-map-scheme&gt;
    &lt;local-scheme/&gt;
  &lt;/backing-map-scheme&gt;
  &lt;autostart&gt;true&lt;/autostart&gt;
  &lt;health&gt;
    &lt;allow-endangered&gt;true&lt;/allow-endangered&gt;
  &lt;/health&gt;
&lt;/distributed-scheme&gt;</markup>

</div>
</div>

<h3 id="http">Enabling HTTP Health Checks</h3>
<div class="section">
<p>The health check http endpoints are enabled when Coherence is run using the bootstrap API, or starting Coherence using <code>com.tangosol.net.Coherence</code> as the main class. If Coherence is started by any other method, the health check API is still available, but the http endpoints will not be running.
By default, the http server will bind to an ephemeral port, but this can be changed by setting the <code>coherence.health.http.port</code> system property, or <code>COHERENCE_HEALTH_HTTP_PORT</code> environment variable.</p>

<p>For example, running the following command will start Coherence with the health endpoints on <a id="" title="" target="_blank" href="http://localhost:6676">http://localhost:6676</a></p>

<markup
lang="bash"

>java -cp coherence.jar -Dcoherence.health.http.port=6676 \
    com.tangosol.net.Coherence</markup>

<p>or with Java modules</p>

<markup
lang="bash"

>java -p coherence/target/coherence-14.1.2-0-0-SNAPSHOT.jar \
    -Dcoherence.health.http.port=6676 \
    --module com.oracle.coherence</markup>

<p>The <code>curl</code> utility can then be used to poll one of the endpoints, for example <code>/ready</code>:</p>

<markup
lang="bash"

>curl -i -X GET http://localhost:6676/ready</markup>

<p>Which returns output like the following</p>

<markup


>HTTP/1.1 200 OK
Date: Tue, 19 Apr 2022 17:59:05 GMT
Content-type: application/json
Vary: Accept-Encoding
Content-length: 0
X-content-type-options: nosniff</markup>

<p>If Coherence the health check had failed, the response code would have been 503, for "service unavailable".</p>


<h4 id="_health_http_endpoints">Health HTTP Endpoints</h4>
<div class="section">
<p>The health check http server has a number of endpoints.</p>

<div class="admonition note">
<p class="admonition-textlabel">Note</p>
<p ><p>None of the endpoints accepts a payload or returns a response body.
The only response is either a 200, or 503 status code.
This means that although the health endpoints can be configured to use SSL/TLS, there is little need for encryption, making their use by external tooling such as Kubernetes and other container environments a lot simpler.</p>
</p>
</div>

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

<h3 id="_containerized_health_checks">Containerized Health Checks</h3>
<div class="section">
<p>When running Coherence applications in containers, for example, in Docker or Kubernetes, it is useful to be able to make use of health and readiness checks. By running Coherence with the <router-link to="#http" @click.native="this.scrollFix('#http')">health http endpoints enabled</router-link> configuring container health is simple.</p>

<p>When using the health check endpoints in a container, the http port needs to be fixed so that the image&#8217;s health checks can be configured. The default behaviour of binding to an ephemeral port would mean the system would not know which port the health check API was bound to. The http port can be set using the <code>coherence.health.http.port</code> system property, or <code>COHERENCE_HEALTH_HTTP_PORT</code> environment variable. When creating images, it is typically simpler to use environment variables, which is what the examples below show.</p>


<h4 id="_docker_health_checks">Docker Health Checks</h4>
<div class="section">
<p>It is possible to build a Coherence Docker image configured with a health check using the <code>HEALTHCHECK</code> configuration in the Dockerfile.</p>

<p>The example <code>Dockerfile</code> below, sets the health check port to <code>6676</code> using the <code>ENV COHERENCE_HEALTH_HTTP_PORT=6676</code> setting.
The <code>Dockerfile</code> is then configured with a <code>HEALTHCHECK</code> where the command will run <code>curl</code> against the http endpoint on <code><a id="" title="" target="_blank" href="http://127.0.0.1:6676/ready">http://127.0.0.1:6676/ready</a></code>. This will fail if the response is not <code>200</code>.</p>

<markup

title="Dockerfile"
>FROM openjdk:11-jre

ADD coherence.jar /coherence/lib/coherence.jar

ENTRYPOINT [ "java" ]
CMD [ "-cp", "/coherence/lib/*", "com.tangosol.net.Coherence" ]

ENV COHERENCE_HEALTH_HTTP_PORT=6676

HEALTHCHECK CMD curl --fail http://127.0.0.1:6676/ready || exit 1</markup>

<p>The check above assumes that the base image has <code>curl</code> installed.
This is not always the case, for example some very slim linux base images or distroless images will not have any additional tools such as <code>curl</code>. In this case all the image has is Java, so the health check can be configured to use a Java health check client class <code>com.tangosol.util.HealthCheckClient</code> that is built into the Coherence jar. This class can be run with a single parameter, which is the URL of the http endpoint to check.</p>

<p>The example <code>Dockerfile</code> below uses a distroless base image that only has a linux kernel and Java 11 installed. The health check port is set to <code>6676</code> using the <code>ENV COHERENCE_HEALTH_HTTP_PORT=6676</code> setting.
The <code>Dockerfile</code> is then configured with a <code>HEALTHCHECK</code> where the command will run <code>java -cp /coherence/lib/coherence.jar com.tangosol.util.HealthCheckClient <a id="" title="" target="_blank" href="http://127.0.0.1:6676/ready">http://127.0.0.1:6676/ready</a></code> This will fail if the response is not <code>200</code>.</p>

<markup

title="Dockerfile"
>FROM gcr.io/distroless/java11

ADD coherence.jar /coherence/lib/coherence.jar

ENTRYPOINT [ "java" ]
CMD [ "-cp", "/coherence/lib/*", "com.tangosol.net.Coherence" ]

ENV COHERENCE_HEALTH_HTTP_PORT=6676

HEALTHCHECK CMD java -cp /coherence/lib/coherence.jar com.tangosol.util.HealthCheckClient http://127.0.0.1:6676/ready</markup>

</div>

<h4 id="_kubernetes_readiness_and_liveness">Kubernetes Readiness and Liveness</h4>
<div class="section">
<p>In Kubernetes, there are various readiness and liveness probes that can be configured.
The image itself does not need a health check (such as that shown above) as Kubernetes readiness and liveness is independent of the image. For full details of how to configure Kubernetes readiness and liveness, see the Kubernetes documentation.</p>

<p>The example below is just a simple <code>Pod</code> using a Coherence image and health checks.
The <code>COHERENCE_HEALTH_HTTP_PORT</code> environment variable is used to fix the health check http port to <code>6676</code>.
The <code>readinessProbe</code> is then configured to use a http GET request on port <code>6676</code> using the request path <code>/ready</code>. The host for the request defaults to the Pod IP address, so will effectively be the same as <code><a id="" title="" target="_blank" href="http://&lt;pod-ip&gt;:6676/ready">http://&lt;pod-ip&gt;:6676/ready</a></code>.</p>

<markup
lang="yaml"
title="pod.yaml"
>apiVersion: v1
kind: Pod
metadata:
  name: coherence
spec:
  containers:
  - name: coherence
    image: ghcr.io/oracle/coherence-ce:22.06
    env:
      - name: COHERENCE_HEALTH_HTTP_PORT
        value: "6676"
      - name: COHERENCE_WKA
        value: coherence_wka.svc.cluster.local
    readinessProbe:
      httpGet:
        path: "/ready"
        port: 6676
      initialDelaySeconds: 30
      periodSeconds: 30</markup>

</div>
</div>
</div>
</doc-view>
