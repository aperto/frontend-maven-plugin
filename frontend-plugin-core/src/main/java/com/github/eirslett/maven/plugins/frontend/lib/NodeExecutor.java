package com.github.eirslett.maven.plugins.frontend.lib;

import java.util.List;
import java.util.Map;

final class NodeExecutor extends Executor {

    public NodeExecutor(NodeExecutorConfig config, List<String> arguments, Map<String, String> additionalEnvironment){
        super(config, arguments, additionalEnvironment);
    }

    protected String getExecutablePath(NodeExecutorConfig config) {
        return config.getNodePath().getAbsolutePath();
    }

}
