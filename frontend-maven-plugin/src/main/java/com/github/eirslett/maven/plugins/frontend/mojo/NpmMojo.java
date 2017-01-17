package com.github.eirslett.maven.plugins.frontend.mojo;

import com.github.eirslett.maven.plugins.frontend.lib.FrontendPluginFactory;
import com.github.eirslett.maven.plugins.frontend.lib.ProxyConfig;
import com.github.eirslett.maven.plugins.frontend.lib.TaskRunnerException;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name="npm",  defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public final class NpmMojo extends AbstractNpmMojo {

    /**
     * npm arguments. Default is "install".
     */
    @Parameter(defaultValue = "install", property = "frontend.npm.arguments", required = false)
    protected String arguments;

    protected void executeNpm(FrontendPluginFactory factory, ProxyConfig proxyConfig) throws TaskRunnerException {
        factory.getNpmRunner(proxyConfig, getRegistryUrl()).execute(arguments, environmentVariables);
    }

}
