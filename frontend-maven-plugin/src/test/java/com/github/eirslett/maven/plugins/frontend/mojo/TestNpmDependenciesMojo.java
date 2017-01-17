package com.github.eirslett.maven.plugins.frontend.mojo;

import com.github.eirslett.maven.plugins.frontend.lib.FrontendPluginFactory;
import com.github.eirslett.maven.plugins.frontend.lib.NpmRunner;
import com.github.eirslett.maven.plugins.frontend.lib.ProxyConfig;
import com.github.eirslett.maven.plugins.frontend.lib.TaskRunnerException;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.mockito.Mockito;

import javax.annotation.meta.When;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

public class TestNpmDependenciesMojo {

    private String readFile(String name) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(name);
        try {
            return IOUtils.toString(resourceAsStream, "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test() throws TaskRunnerException {
        final int[] testResult = new int[2];

        NpmDependenciesMojo testMojo = new NpmDependenciesMojo() {
            @Override
            protected String getPackageDirectoryList(FrontendPluginFactory factory, ProxyConfig proxyConfig) throws TaskRunnerException {
                return readFile("testNpm/directoryList.txt");
            }
            protected String getPackageTreeJson(FrontendPluginFactory factory, ProxyConfig proxyConfig) throws TaskRunnerException {
                return readFile("testNpm/packageTree.json");
            };
            @Override
            protected void copyDependencies(String fullDirectoryList, int startline, int numLines) {
                testResult[0] = startline;
                testResult[1] = numLines;
            }
        };

        testMojo.npmPackages = new String[]{"uglify-js"};
        NpmRunner mockNpmRunner = Mockito.mock(NpmRunner.class);
        FrontendPluginFactory mockFpf = Mockito.mock(FrontendPluginFactory.class);
        Mockito.when(mockFpf.getNpmRunner(null, null)).thenReturn(mockNpmRunner);
        testMojo.executeNpm(mockFpf, null);

        assertEquals(5, testResult[0]);
        assertEquals(3, testResult[1]);
    }

}
