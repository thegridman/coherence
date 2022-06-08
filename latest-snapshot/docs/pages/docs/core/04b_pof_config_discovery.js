<doc-view>

<h2 id="_pof_configuration_discovery">POF Configuration Discovery</h2>
<div class="section">

<h3 id="_making_pof_configuration_files_discoverable_at_runtime">Making POF Configuration Files Discoverable At Runtime</h3>
<div class="section">
<p>In Java applications where new modules may be added at deploy time or runtime, the full set of required POF configuration files may not be known ahead of time and hence it may not be possible to create a POF configuration file with all the correct <code>&lt;include&gt;</code> elements. To allow for this type of use case, it is possible to make POF configuration files discoverable at runtime by the ConfigurablePofContext class instead of needing to put them inside <code>&lt;include&gt;</code> elements.</p>

<p>To make a configuration file discoverable, create a class that implements <code>com.tangosol.io.pof.PofConfigProvider</code>.
The <code>PofConfigProvider</code> has a single method that must be implemented to return the name of the configuration file.
At runtime, the <code>ConfigurablePofContext</code> class uses the Java <code>ServiceLoader</code> to discover implementations of <code>PofConfigProvider</code>
and load their provided POF configuration files, exactly as if they had been added in <code>&lt;include&gt;</code> elements.</p>

<p>For example, the <code>RuntimeConfigProvider</code> class below provides <code>discovered-pof-config.xml</code> as the configuration file name.
At runtime, the <code>ConfigurablePofContext</code> class will load the <code>discovered-pof-config.xml</code> POF configuration file.</p>

<markup
lang="java"
title="RuntimeConfigProvider.java"
>package com.oracle.coherence.examples;

import com.tangosol.io.pof.PofConfigProvider;

public class RuntimeConfigProvider
        implements PofConfigProvider
    {
    @Override
    public String getConfigURI()
        {
        return "discovered-pof-config.xml";
        }
    }</markup>

<p>To make the <code>RuntimeConfigProvider</code> class discoverable, either:</p>

<p>Add it to a file <code>META-INF/services/com.tangosol.io.pof.PofConfigProvider</code></p>

<markup

title="META-INF/services/com.tangosol.io.pof.PofConfigProvider"
>com.oracle.coherence.examples.RuntimeConfigProvider</markup>

<p>Or if using Java Modules, add it to the <code>module-info.java</code> file:</p>

<markup
lang="java"
title="module-info.java"
>module com.oracle.coherence.examples {
    provides com.tangosol.io.pof.PofConfigProvider
        with com.oracle.coherence.examples.RuntimeConfigProvider;
}</markup>

<p>If required, a <code>PofConfigProvider</code> implementation may return multiple POF configuration files by overriding
the <code>PofConfigProvider.getConfigURIs()</code> method. In this case the singular <code>getConfigURI()</code> will not be called.</p>

<p>In the example below the <code>RuntimeConfigProvider</code> the <code>getConfigURIs()</code> method returns the POF configuration file names
<code>discovered-pof-config.xml</code>, <code>additional-pof-config.xml</code>, both of which will be loaded by the <code>ConfigurablePofContext</code>
at runtime.</p>

<markup
lang="java"
title="RuntimeConfigProvider.java"
>package com.oracle.coherence.examples;

import com.tangosol.io.pof.PofConfigProvider;

import java.util.Set;

public class RuntimeConfigProvider
        implements PofConfigProvider
    {
    @Override
    public Set&lt;String&gt; getConfigURIs()
        {
        return Set.of("discovered-pof-config.xml",
                "additional-pof-config.xml");
        }

    @Override
    public String getConfigURI()
        {
        return null;
        }
    }</markup>

</div>
</div>
</doc-view>
