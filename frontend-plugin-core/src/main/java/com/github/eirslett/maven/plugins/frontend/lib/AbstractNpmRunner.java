package com.github.eirslett.maven.plugins.frontend.lib;

import com.github.eirslett.maven.plugins.frontend.lib.ProxyConfig.Proxy;

import java.util.ArrayList;
import java.util.List;

public class AbstractNpmRunner extends NodeTaskExecutor {

    static final String TASK_NAME = "npm";

    public AbstractNpmRunner(NodeExecutorConfig config,  ProxyConfig proxyConfig, String npmRegistryURL) {
        super(config, TASK_NAME, config.getNpmPath().getAbsolutePath(), AbstractNpmRunner.buildArguments(proxyConfig, npmRegistryURL));
    }

    protected static List<String> buildArguments(ProxyConfig proxyConfig, String npmRegistryURL) {
        List<String> arguments = new ArrayList<String>();

        if(npmRegistryURL != null && !npmRegistryURL.isEmpty()){
            arguments.add ("--registry=" + npmRegistryURL);
        }

        if(!proxyConfig.isEmpty()){
            Proxy proxy = null;
            if(npmRegistryURL != null && !npmRegistryURL.isEmpty()){
                proxy = proxyConfig.getProxyForUrl(npmRegistryURL);
            }

            if(proxy == null){
                proxy = proxyConfig.getSecureProxy();
            }

            if(proxy == null){
                proxy = proxyConfig.getInsecureProxy();
            }

            arguments.add("--https-proxy=" + proxy.getUri().toString());
            arguments.add("--proxy=" + proxy.getUri().toString());
        }

        return arguments;
    }

}
