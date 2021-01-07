/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.classloader;

import org.gradle.api.GradleException;
import org.gradle.internal.classpath.ClassPath;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.CodeSource;
import java.security.cert.Certificate;

// TODO (donat) just extend TransformingClassLoader
public class AntAndGradleClassloader extends VisitableURLClassLoader {

    private ClassLoader runtimeClassLoaderClassLoader;

    public AntAndGradleClassloader(String name, ClassLoader parent, ClassPath classPath, ClassLoader runtimeClassLoaderClassLoader) {
        super(name, parent, classPath);
        this.runtimeClassLoaderClassLoader = runtimeClassLoaderClassLoader;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!shouldTransform(name)) {
            return super.findClass(name);
        }

        String resourceName = name.replace('.', '/') + ".class";
        URL resource = findResource(resourceName);

        byte[] bytes = null;
        CodeSource codeSource = null;
        try {
            if (resource != null) {
                bytes = loadBytecode(resource);
                bytes = transform(name, bytes);
                URL codeBase = ClasspathUtil.getClasspathForResource(resource, resourceName).toURI().toURL();
                codeSource = new CodeSource(codeBase, (Certificate[]) null);
            }
        } catch (Exception e) {
            throw new GradleException(String.format("Could not load class '%s' from %s.", name, resource), e);
        }

        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }


        String packageName = substringBeforeLast(name, ".");
        @SuppressWarnings("deprecation") Package p = getPackage(packageName);
        if (p == null) {
            definePackage(packageName, null, null, null, null, null, null, null);
        }
        return defineClass(name, bytes, 0, bytes.length, codeSource);
    }

    public static String substringBeforeLast(String str, String separator) {
        if (str.isEmpty() || separator.isEmpty()) {
            return str;
        }
        int pos = str.lastIndexOf(separator);
        if (pos == -1) {
            return str;
        }
        return str.substring(0, pos);
    }

    private byte[] transform(String name, byte[] bytes) {
        try {
            Class<?> clazz = runtimeClassLoaderClassLoader.loadClass("org.gradle.internal.classloader.BackwardCompatibilityClassTransformer");
            Method method = clazz.getMethod("transform", String.class, byte[].class);
            return (byte[]) method.invoke(null, name, bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] loadBytecode(URL resource) throws IOException {
        InputStream inputStream = resource.openStream();
        try {
            return toByteArray(inputStream);
        } finally {
            inputStream.close();
        }
    }

    private byte[] toByteArray(InputStream inputStream) {
        try {
            Class<?> clazz = runtimeClassLoaderClassLoader.loadClass("com.google.common.io.ByteStreams");
            Method method = clazz.getMethod("toByteArray", InputStream.class);
            return (byte[]) method.invoke(null, inputStream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected boolean shouldTransform(String className) {
        return className.contains("org.gradle.api.tasks.bundling.Tar");
    }

    @Override
    public String toString() {
        return AntAndGradleClassloader.class.getSimpleName() + "(" + getName() + ")";
    }
}
