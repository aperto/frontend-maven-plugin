package com.github.eirslett.maven.plugins.frontend.mojo;

import com.github.eirslett.maven.plugins.frontend.lib.FrontendPluginFactory;
import com.github.eirslett.maven.plugins.frontend.lib.ProxyConfig;
import com.github.eirslett.maven.plugins.frontend.lib.TaskRunnerException;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Build;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.OrderedJSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Extract given npm packages and their dependencies from node_modules and copy them to given destination
 * directory in target.
 */
@Mojo(name = "extractDependencies", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class NpmDependenciesMojo extends AbstractNpmMojo {

    private final Logger logger = LoggerFactory.getLogger(getClass());;

    private int lineCounter = 0;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;

    /**
     * npm packages to extract dependencies from.
     */
    @Parameter(property = "frontend.npmDependencies.npmPackages", required = true)
    protected String[] npmPackages;

    @Parameter(property = "frontend.npmDependencies.targetDir", required = true)
    protected String targetDir;

    /**
     * Reduce to top-level folders within topmost node_modules folder
     */
    protected Set<String> flattenToToplevelFolders(List<String> directories) {
        Set<String> result = new TreeSet<>();

        String[] arr = new String[directories.size()];
        directories.toArray(arr);

        String commonPrefix = StringUtils.getCommonPrefix(arr);

        for (String folder : directories) {
            int separatorAfterName = folder.indexOf(File.separator, commonPrefix.length());
            String topLevelFolder;
            if (separatorAfterName==-1) {
                topLevelFolder = folder.substring(commonPrefix.length());
            } else {
                topLevelFolder = folder.substring(commonPrefix.length(), separatorAfterName);
            }
            result.add(commonPrefix + topLevelFolder);
        }
        return result;
    }

    protected void copyDependencies(String fullDirectoryList, int startline, int numLines) {
        Build build = project.getModel().getBuild();

        BufferedReader bufReader = new BufferedReader(new StringReader(fullDirectoryList));
        List<String> lines = new ArrayList<>(10000);
        String line = null;
        try {
            while ((line = bufReader.readLine()) != null) {
                lines.add(line);
            }
            List<String> directories = lines.subList(startline, startline + numLines + 1);

            Set<String> topLevelFolders = flattenToToplevelFolders(directories);
            for (String directory : topLevelFolders) {
                File from = new File(directory);
                File to = Paths.get(build.getDirectory(), targetDir, from.getName()).toFile();
                logger.info("extractDependencies: copying " + from + " to " + to);
                org.apache.maven.shared.utils.io.FileUtils.copyDirectoryStructure(from, to);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        // obtain package tree as json
        String packageTreeJson = getPackageTreeJson(factory, proxyConfig);

        String directoryList = getPackageDirectoryList(factory, proxyConfig);

        logger.info("extractDependencies: copying npm packages " + Arrays.toString(npmPackages) + " to " + targetDir);
        for (String npmPackageName : npmPackages) {
            lineCounter = 0;
            OrderedJSONObject root;
            try {
                // must use OrderedJSONObject so order in packageTreeJson DFS
                // traversal will be same as in directoryList
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
    }

    protected String getPackageDirectoryList(FrontendPluginFactory factory, ProxyConfig proxyConfig) throws TaskRunnerException {
        String directoryList = factory.getNpmRunner(proxyConfig, getRegistryUrl()).executeWithResult("ls --parseable true --json false", environmentVariables);
        return directoryList;
    }

    protected String getPackageTreeJson(FrontendPluginFactory factory, ProxyConfig proxyConfig) throws TaskRunnerException {
        String packageTreeJson = factory.getNpmRunner(proxyConfig, getRegistryUrl()).executeWithResult("ls --parseable false --json true", environmentVariables);
        return packageTreeJson;
    }

}
