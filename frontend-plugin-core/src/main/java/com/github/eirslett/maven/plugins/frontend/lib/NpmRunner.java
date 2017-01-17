package com.github.eirslett.maven.plugins.frontend.lib;

public interface NpmRunner extends NodeTaskRunner {}

final class DefaultNpmRunner extends AbstractNpmRunner implements NpmRunner {
    public DefaultNpmRunner(NodeExecutorConfig config, ProxyConfig proxyConfig, String npmRegistryURL) {
        super(config, proxyConfig, npmRegistryURL);
    }

}
