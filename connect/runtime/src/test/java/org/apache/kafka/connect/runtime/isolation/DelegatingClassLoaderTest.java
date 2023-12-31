/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kafka.connect.runtime.isolation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class DelegatingClassLoaderTest {

    @Rule
    public TemporaryFolder pluginDir = new TemporaryFolder();

    @Test
    public void testLoadingUnloadedPluginClass() {
        DelegatingClassLoader classLoader = new DelegatingClassLoader(
                Collections.emptyList(),
                DelegatingClassLoader.class.getClassLoader()
        );
        classLoader.initLoaders();
        for (String pluginClassName : TestPlugins.pluginClasses()) {
            assertThrows(ClassNotFoundException.class, () -> classLoader.loadClass(pluginClassName));
        }
    }

    @Test
    public void testLoadingPluginClass() throws ClassNotFoundException {
        DelegatingClassLoader classLoader = new DelegatingClassLoader(
                TestPlugins.pluginPath(),
                DelegatingClassLoader.class.getClassLoader()
        );
        classLoader.initLoaders();
        for (String pluginClassName : TestPlugins.pluginClasses()) {
            assertNotNull(classLoader.loadClass(pluginClassName));
            assertNotNull(classLoader.pluginClassLoader(pluginClassName));
        }
    }

    @Test
    public void testLoadingInvalidUberJar() throws Exception {
        pluginDir.newFile("invalid.jar");

        DelegatingClassLoader classLoader = new DelegatingClassLoader(
                Collections.singletonList(pluginDir.getRoot().toPath().toAbsolutePath()),
                DelegatingClassLoader.class.getClassLoader()
        );
        classLoader.initLoaders();
    }

    @Test
    public void testLoadingPluginDirContainsInvalidJarsOnly() throws Exception {
        pluginDir.newFolder("my-plugin");
        pluginDir.newFile("my-plugin/invalid.jar");

        DelegatingClassLoader classLoader = new DelegatingClassLoader(
                Collections.singletonList(pluginDir.getRoot().toPath().toAbsolutePath()),
                DelegatingClassLoader.class.getClassLoader()
        );
        classLoader.initLoaders();
    }

    @Test
    public void testLoadingNoPlugins() {
        DelegatingClassLoader classLoader = new DelegatingClassLoader(
                Collections.singletonList(pluginDir.getRoot().toPath().toAbsolutePath()),
                DelegatingClassLoader.class.getClassLoader()
        );
        classLoader.initLoaders();
    }

    @Test
    public void testLoadingPluginDirEmpty() throws Exception {
        pluginDir.newFolder("my-plugin");

        DelegatingClassLoader classLoader = new DelegatingClassLoader(
                Collections.singletonList(pluginDir.getRoot().toPath().toAbsolutePath()),
                DelegatingClassLoader.class.getClassLoader()
        );
        classLoader.initLoaders();
    }

    @Test
    public void testLoadingMixOfValidAndInvalidPlugins() throws Exception {
        pluginDir.newFile("invalid.jar");
        pluginDir.newFolder("my-plugin");
        pluginDir.newFile("my-plugin/invalid.jar");
        Path pluginPath = this.pluginDir.getRoot().toPath();

        for (Path source : TestPlugins.pluginPath()) {
            Files.copy(source, pluginPath.resolve(source.getFileName()));
        }

        DelegatingClassLoader classLoader = new DelegatingClassLoader(
                Collections.singletonList(pluginDir.getRoot().toPath().toAbsolutePath()),
                DelegatingClassLoader.class.getClassLoader()
        );
        classLoader.initLoaders();
        for (String pluginClassName : TestPlugins.pluginClasses()) {
            assertNotNull(classLoader.loadClass(pluginClassName));
            assertNotNull(classLoader.pluginClassLoader(pluginClassName));
        }
    }
}
