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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Extract given npm packages and their dependencies from node_modules and copy them to given destination
 * directory in target.
 */
@Mojo(name = "extractDependencies", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class NpmDependenciesMojo extends AbstractNpmMojo {

    // can be debugged using "mvnDebug frontend:extractDependencies -Dfrontend.npmDependencies.npmPackages=grunt-assemble -Dfrontend.npmDependencies.targetDir=hbs/node_modules"
    // given a project that has "grunt-assemble" in its node_modules

    private final Logger logger = LoggerFactory.getLogger(getClass());;


    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;

    /**
     * npm packages to extract dependencies from.
     */
    @Parameter(property = "frontend.npmDependencies.npmPackages", required = true)
    protected String[] npmPackages;

    @Parameter(property = "frontend.npmDependencies.targetDir", required = true)
    protected String targetDir;

    protected int lineCounter = 0;
    Queue<String> packageNames = new ArrayBlockingQueue<>(1000);

    class NpmPackage {
        final String name;
        final String version;
        final List<NpmPackage> dependencies = new LinkedList<>();

        public NpmPackage(String name, String version) {
            this.name = name;
            this.version = version;
        }
        @Override
        public String toString() {
            return name + "@" + version;
        }
    }

    NpmPackage parseAsciiTree(String asciiTree) {
        NpmPackage root = null;
        List<String> lines = getLines(asciiTree);
        String lastLine = lines.get(lines.size()-1);
        if (lastLine.trim().equals("")) {
            // remove empty line at end
            lines.remove(lines.size() - 1);
        }
        Stack<NpmPackage> stack = new Stack<>();

        for (String line : lines) {
            int nameStart = findPackageNameStart(line);

            int versionSeparator = line.indexOf("@", nameStart);
            String packageName = line.substring(nameStart, versionSeparator);
            String version = line.substring(versionSeparator + 1);

            // levels are indented by 2 chars
            int level = nameStart / 2;

            NpmPackage npmPackage = new NpmPackage(packageName, version);

            if (stack.isEmpty()) {
                // this is the root package
                root = npmPackage;
            } else {
                // pop to parent of current level
                while (stack.size() >= level ) {
                    stack.pop();
                }

                NpmPackage parent = stack.peek();
                parent.dependencies.add(npmPackage);
            }

            stack.push(npmPackage);
        }
        return root;
    }

    private int findPackageNameStart(String line) {
        int nameStart;
        int index = line.indexOf("─ ");
        if (index == -1) {
            index = line.indexOf("┬ ");
        }
        if (index > -1) {
            nameStart = index + 2;
        } else {
            // first line in file starts with containing package
            nameStart = 0;
        }
        return nameStart;
    }

    /**
     * Reduce to top-level folders within topmost node_modules folder
     */
    protected Set<String> flattenToToplevelFolders(Collection<String> directories) {
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

    protected List<String> getLines(String string) {
        BufferedReader bufReader = new BufferedReader(new StringReader(string));
        List<String> lines = new ArrayList<>(10000);
        String line = null;
        try {
            while ((line = bufReader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return lines;
    }

    protected void copyDependencies(Set<String> directories) throws IOException {
        Build build = project.getModel().getBuild();

        for (String directory : directories) {
            File from = new File(directory);
            File to = Paths.get(build.getDirectory(), targetDir, from.getName()).toFile();
            logger.info("extractDependencies: copying " + from + " to " + to);
            org.apache.maven.shared.utils.io.FileUtils.copyDirectoryStructure(from, to);
        }
    }

    protected NpmPackage depthFirstSearchDependency(NpmPackage root, String findNpmPackage, boolean addLeavesToQueue) {
        String packageNameToSearch = null;
        String versionToSearch = null;
        int index;
        if (findNpmPackage!=null && (index = findNpmPackage.indexOf("@")) > -1) {
            packageNameToSearch = findNpmPackage.substring(0, index);
            versionToSearch = findNpmPackage.substring(index + 1);
        } else {
            packageNameToSearch = findNpmPackage;
        }

        lineCounter++;
        if (!root.dependencies.isEmpty()) {
            for (NpmPackage dependency : root.dependencies) {

                if (addLeavesToQueue && dependency.dependencies.isEmpty()) {
                    packageNames.add(dependency.toString());
                }

                if (dependency.name.equals(packageNameToSearch) && (versionToSearch==null || dependency.version.equals(versionToSearch))) {
                    return dependency;
                } else {
                    NpmPackage found = depthFirstSearchDependency(dependency, findNpmPackage, addLeavesToQueue);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void executeNpm(FrontendPluginFactory factory, ProxyConfig proxyConfig) throws TaskRunnerException {
        // obtain package tree as ASCII art
        String asciiTree = getPackageTreeAscii(factory, proxyConfig);
        // lines in directoryList correspond to lines in package tree ASCII art, and tell us what directory NPM resolves the logical packages to
        String directoryList = getPackageDirectoryList(factory, proxyConfig);

        logger.info("extractDependencies: copying npm packages " + Arrays.toString(npmPackages) + " and their dependencies to " + targetDir);

        List<String> allDirectories = getLines(directoryList);
        Set<String> resultDirectories = new TreeSet<>();

        final NpmPackage root = parseAsciiTree(asciiTree);

        for (String packageName : npmPackages) {
            packageNames.add(packageName);
        }
        while (!packageNames.isEmpty()) {
            String npmPackageName = packageNames.remove();
            lineCounter = 0;

            // depth-first-search package, and count nodes traversed
            NpmPackage npmPackage = depthFirstSearchDependency(root, npmPackageName, false);
            // remember line of found package
            int startLine = lineCounter;
            lineCounter = 0;
            // now count nodes of package's dependencies, and append all leave packages to our packageNames Queue
            // (a leaf package may turn out to have defined dependencies somewhere earlier in the tree)
            depthFirstSearchDependency(npmPackage, null, true);
            int numLines = lineCounter;

            List<String> subList = allDirectories.subList(startLine, startLine + numLines);
            resultDirectories.addAll(subList);
        }

        Set<String> topLevelFolders = flattenToToplevelFolders(resultDirectories);
        try {
            copyDependencies(topLevelFolders);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected String getPackageDirectoryList(FrontendPluginFactory factory, ProxyConfig proxyConfig) throws TaskRunnerException {
        String directoryList = factory.getNpmRunner(proxyConfig, getRegistryUrl()).executeWithResult("ls --parseable true --json false", environmentVariables);
        return directoryList;
    }

    protected String getPackageTreeAscii(FrontendPluginFactory factory, ProxyConfig proxyConfig) throws TaskRunnerException {
        String packageTreeJson = factory.getNpmRunner(proxyConfig, getRegistryUrl()).executeWithResult("ls --parseable false --json false", environmentVariables);
        return packageTreeJson;
    }

}
