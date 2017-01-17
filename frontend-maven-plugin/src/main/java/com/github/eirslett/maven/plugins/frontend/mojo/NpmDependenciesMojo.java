package com.github.eirslett.maven.plugins.frontend.mojo;

import com.github.eirslett.maven.plugins.frontend.lib.FrontendPluginFactory;
import com.github.eirslett.maven.plugins.frontend.lib.ProxyConfig;
import com.github.eirslett.maven.plugins.frontend.lib.TaskRunnerException;

import org.apache.maven.plugins.annotations.Parameter;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.OrderedJSONObject;

import java.util.Iterator;

/**
 * Extract given npm packages and their dependencies from node_modules and copy them to given destination
 * directory in target.
 */
public class NpmDependenciesMojo extends AbstractNpmMojo {

    private int lineCounter = 0;

    /**
     * npm packages to extract dependencies from.
     */
    @Parameter(property = "frontend.npmDependencies.npmPackages", required = true)
    protected String[] npmPackages;

    protected void copyDependencies(String fullDirectoryList, int startline, int numLines) {
        // TODO copy directories
    }

    private OrderedJSONObject depthFirstSearchDependency(OrderedJSONObject root, String npmPackage) {
        try {
            if (root.containsKey("dependencies")) {
                OrderedJSONObject dependencies = (OrderedJSONObject) root.getJSONObject("dependencies");
                Iterator<String> dependencyNames = dependencies.getOrder();
                while (dependencyNames.hasNext()) {
                    String dependencyName = dependencyNames.next();
                    lineCounter++;
                    OrderedJSONObject dependency = (OrderedJSONObject) dependencies.getJSONObject(dependencyName);
                    if (dependencyName.equals(npmPackage)) {
                        return dependency;
                    } else {
                        OrderedJSONObject found = depthFirstSearchDependency(dependency, npmPackage);
                        if (found != null) {
                            return found;
                        }
                    }
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    protected void executeNpm(FrontendPluginFactory factory, ProxyConfig proxyConfig) throws TaskRunnerException {
        String arguments = "get config json";
        String restoreConfigJson = factory.getNpmRunner(proxyConfig, getRegistryUrl()).executeWithResult(arguments, environmentVariables);

        // obtain package tree as json
        String packageTreeJson = getPackageTreeJson(factory, proxyConfig);

        String directoryList = getPackageDirectoryList(factory, proxyConfig);

        for (String npmPackageName : npmPackages) {
            lineCounter = 0;
            OrderedJSONObject root;
            try {
                // must use OrderedJSONObject so order in packageTreeJson DFS traversal will be same as in directoryList
                root = new OrderedJSONObject(packageTreeJson);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            // depth-first-search package, and count nodes traversed
            OrderedJSONObject npmPackage = depthFirstSearchDependency(root, npmPackageName);
            // remember line of found package
            int startLine = lineCounter;
            lineCounter = 0;
            // now count nodes of package's dependencies
            depthFirstSearchDependency(npmPackage, null);
            int numLines = lineCounter;
            copyDependencies(directoryList, startLine, numLines);
        }

        // restoreConfigJson
        factory.getNpmRunner(proxyConfig, getRegistryUrl()).execute("set config json " + restoreConfigJson, environmentVariables);
    }

    protected String getPackageDirectoryList(FrontendPluginFactory factory, ProxyConfig proxyConfig) throws TaskRunnerException {
        factory.getNpmRunner(proxyConfig, getRegistryUrl()).execute("set config json false", environmentVariables);
        String directoryList = factory.getNpmRunner(proxyConfig, getRegistryUrl()).executeWithResult("ls", environmentVariables);
        return directoryList;
    }

    protected String getPackageTreeJson(FrontendPluginFactory factory, ProxyConfig proxyConfig) throws TaskRunnerException {
        factory.getNpmRunner(proxyConfig, getRegistryUrl()).execute("set config json true", environmentVariables);
        String packageTreeJson = factory.getNpmRunner(proxyConfig, getRegistryUrl()).executeWithResult("ls", environmentVariables);
        return packageTreeJson;
    }

}
