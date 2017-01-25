package com.github.eirslett.maven.plugins.frontend.mojo;

import com.github.eirslett.maven.plugins.frontend.lib.FrontendPluginFactory;
import com.github.eirslett.maven.plugins.frontend.lib.NpmRunner;
import com.github.eirslett.maven.plugins.frontend.lib.ProxyConfig;
import com.github.eirslett.maven.plugins.frontend.lib.TaskRunnerException;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestNpmDependenciesMojo {

    private String readFile(String name) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(name);
        try {
            return IOUtils.toString(resourceAsStream, "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Set<String> resultDirectories;
    final FrontendPluginFactory mockFpf = mockFrontendPluginFactory();

    private FrontendPluginFactory mockFrontendPluginFactory() {
        NpmRunner mockNpmRunner = Mockito.mock(NpmRunner.class);
        FrontendPluginFactory mockFpf = Mockito.mock(FrontendPluginFactory.class);
        Mockito.when(mockFpf.getNpmRunner(null, null)).thenReturn(mockNpmRunner);
        return mockFpf;
    }

    @Before
    public void beforeTest() {
        resultDirectories = null;
    }

    private NpmDependenciesMojo createMojoUnderTest(final String directoryListFileName, final String packageTreeFilename) {
        return new NpmDependenciesMojo() {
            @Override
            protected String getPackageDirectoryList(FrontendPluginFactory factory, ProxyConfig proxyConfig) throws TaskRunnerException {
                return readFile(directoryListFileName);
            }
            protected String getPackageTreeAscii(FrontendPluginFactory factory, ProxyConfig proxyConfig) throws TaskRunnerException {
                return readFile(packageTreeFilename);
            };
            @Override
            protected void copyDependencies(Set<String> directories) {
                resultDirectories = directories;
            }
        };
    }

    @Test
    public void testLineCountOnSearch() throws TaskRunnerException {
        NpmDependenciesMojo testMojo = createMojoUnderTest("gft/directoryList-gft.txt", "gft/packageTree-gft.txt");
        NpmDependenciesMojo.NpmPackage root  = testMojo.parseAsciiTree(testMojo.getPackageTreeAscii(mockFpf, null));
        NpmDependenciesMojo.NpmPackage pkg = testMojo.depthFirstSearchDependency(root, "commoner@0.10.8", false);
        assertNotNull(pkg);
        assertEquals(36, testMojo.lineCounter);
    }

    @Test
    public void test() throws TaskRunnerException {
        NpmDependenciesMojo testMojo = createMojoUnderTest("testnpm/directoryList.txt", "testnpm/packageTree.txt");
        testMojo.npmPackages = new String[]{"uglify-js"};
        testMojo.executeNpm(mockFpf, null);

        assertEquals(4, resultDirectories.size());
        assertTrue(resultDirectories.contains("/Users/joerg.frantzius/git/frontend-maven-plugin/frontend-maven-plugin/src/test/resources/testnpm/node_modules/uglify-js"));
        assertTrue(resultDirectories.contains("/Users/joerg.frantzius/git/frontend-maven-plugin/frontend-maven-plugin/src/test/resources/testnpm/node_modules/async"));
        assertTrue(resultDirectories.contains("/Users/joerg.frantzius/git/frontend-maven-plugin/frontend-maven-plugin/src/test/resources/testnpm/node_modules/source-map"));
        assertTrue(resultDirectories.contains("/Users/joerg.frantzius/git/frontend-maven-plugin/frontend-maven-plugin/src/test/resources/testnpm/node_modules/amdefine"));
    }


    @Test
    public void testResolveDependencies() throws TaskRunnerException {
        NpmDependenciesMojo testMojo = createMojoUnderTest("gft/directoryList-gft.txt", "gft/packageTree-gft.txt");

        testMojo.npmPackages = new String[]{"grunt-assemble"};

        testMojo.executeNpm(mockFpf, null);

        // the tree of grunt-assemble/assemble/handlebars/handlebars-helpers (line 304) shows a minimatch@2.0.14 without any dependencies,
        // seemingly because these were defined earlier on in the tree (line 269)
        // verify that we resolve these minimatch dependencies properly

        assertTrue(resultDirectories.contains("/Users/joerg.frantzius/git/gft-prototype/src/main/assemble/src/node_modules/lru-cache"));
        assertTrue(resultDirectories.contains("/Users/joerg.frantzius/git/gft-prototype/src/main/assemble/src/node_modules/sigmund"));

    }


}
