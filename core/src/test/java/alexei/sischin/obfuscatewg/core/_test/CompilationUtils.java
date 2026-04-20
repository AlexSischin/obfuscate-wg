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

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@UtilityClass
public class CompilationUtils {

    @SneakyThrows
    public static void compileDir(Path sourceDirPath) {
        List<File> javaFiles = getJavaFiles(sourceDirPath);
        if (javaFiles.isEmpty()) {
            throw new IllegalArgumentException("No java files found at path \"%s\"".formatted(sourceDirPath));
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No Java compiler available");
        }

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(javaFiles);

        try (
                ByteArrayOutputStream compilerBos = new ByteArrayOutputStream();
                PrintWriter compilerWriter = new PrintWriter(compilerBos)
        ) {
            List<String> options = Stream.of(getModulePath(), getClassPath()).flatMap(Collection::stream).toList();
            boolean success = compiler.getTask(
                    compilerWriter,
                    fileManager,
                    null,
                    options,
                    null,
                    compilationUnits
            ).call();
            if (!success) {
                String compilerOutput = compilerBos.toString().trim();
                throw new RuntimeException("Failed to compile \"%s\":\n%s".formatted(sourceDirPath, compilerOutput));
            }
        }
    }

    @SneakyThrows
    private List<File> getJavaFiles(Path sourceDirPath) {
        try (Stream<Path> walk = Files.walk(sourceDirPath)) {
            return walk.map(Path::toFile)
                    .filter(File::isFile)
                    .filter(f -> f.getName().endsWith(".java"))
                    .toList();
        }
    }

    private List<String> getModulePath() {
        String modulePath = System.getProperty("jdk.module.path");
        if (modulePath != null) {
            return List.of("-p", modulePath);
        }
        return List.of();
    }

    private List<String> getClassPath() {
        String modulePath = System.getProperty("java.class.path");
        if (modulePath != null) {
            return List.of("-cp", modulePath);
        }
        return List.of();
    }
}
