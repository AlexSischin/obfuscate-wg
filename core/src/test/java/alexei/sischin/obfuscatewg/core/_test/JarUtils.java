/*
 * Copyright 2026 Alexei Sischin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package alexei.sischin.obfuscatewg.core._test;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static alexei.sischin.obfuscatewg.core._test.CompilationUtils.compileDir;

@Slf4j
@UtilityClass
public class JarUtils {

    public static Path compileAndCreateJar(String resourceName) throws IOException {
        Path dirPath = resolveResource(resourceName);
        Path dirTmpCopy = Files.createTempDirectory("test-");
        FileUtils.copyDirectory(dirPath, dirTmpCopy);
        compileDir(dirTmpCopy);
        deleteJavaFiles(dirTmpCopy);
        return createJarAsIs(dirTmpCopy);
    }

    private static void deleteJavaFiles(Path sourceDirPath) throws IOException {
        try (Stream<Path> walk = Files.walk(sourceDirPath)) {
            walk.map(Path::toFile)
                    .filter(File::isFile)
                    .filter(f -> f.getName().endsWith(".java"))
                    .forEach(j -> {
                        if (!j.delete()) {
                            throw new RuntimeException("Failed to delete java file: %s".formatted(j));
                        }
                    });
        }
    }

    private static Path createJarAsIs(Path dirPath) throws IOException {
        Path jarFilePath = dirPath.getParent().resolve(dirPath.getFileName() + ".jar");
        File jarFile = jarFilePath.toFile();
        try (
                Stream<Path> walk = Files.walk(dirPath);
                FileOutputStream out = new FileOutputStream(jarFile);
                JarOutputStream jos = new JarOutputStream(out)
        ) {
            for (Path filePath : walk.toList()) {
                if (filePath.toFile().isFile()) {
                    Path relFilePath = dirPath.relativize(filePath);
                    String entryName = relFilePath.toString().replace(File.separatorChar, '/');

                    JarEntry jarEntry = new JarEntry(entryName);
                    jos.putNextEntry(jarEntry);
                    Files.copy(filePath, jos);
                    jos.closeEntry();
                }
            }
        }
        return jarFilePath;
    }

    @SneakyThrows
    private Path resolveResource(String resourceName) {
        ClassLoader classLoader = CompilationUtils.class.getClassLoader();
        URL javaFileUrl = classLoader.getResource(resourceName);
        if (javaFileUrl == null) {
            throw new IllegalArgumentException("Cannot find resource \"%s\"".formatted(resourceName));
        }
        return Path.of(javaFileUrl.toURI());
    }
}
