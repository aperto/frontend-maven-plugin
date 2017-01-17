package com.github.eirslett.maven.plugins.frontend.lib;

import java.util.List;
import java.util.Map;

final class NpmExecutor extends Executor {

    public NpmExecutor(NodeExecutorConfig config, List<String> arguments, Map<String, String> additionalEnvironment){
        super(config, arguments, additionalEnvironment);
    }

    protected String getExecutablePath(NodeExecutorConfig config) {
        return config.getNpmPath().getAbsolutePath();
    }

}
