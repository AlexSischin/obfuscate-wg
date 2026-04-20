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

package alexei.sischin.obfuscatewg.core.util.protocol;

import alexei.sischin.obfuscatewg.protocol.api.Protocol;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;

import static alexei.sischin.obfuscatewg.core._test.Constants.TEST_WG_MTU;
import static alexei.sischin.obfuscatewg.core._test.JarUtils.compileAndCreateJar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
class ProtocolLoaderTest {

    private static final String VALID_JAR_RESOURCE_NAME = "jar/valid";
    private static final String DUPLICATE_JAR_RESOURCE_NAME = "jar/duplicate";

    @SneakyThrows
    @Test
    void load_givenInvalidFileURL_throwsIllegalArgumentException() {
        URL url = File.createTempFile("invalid-protocol", ".jar").toURI().toURL();
        try (ProtocolLoader victim = new ProtocolLoader(url)) {
            assertThatThrownBy(() -> victim.load("test-args"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No protocol builder providers found");
        }
    }

    @SneakyThrows
    @Test
    void load_givenInvalidInetURL_throwsIllegalArgumentException() {
        URL url = URI.create("http://localhost:0").toURL();
        try (ProtocolLoader victim = new ProtocolLoader(url)) {
            assertThatThrownBy(() -> victim.load("test-args"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No protocol builder providers found");
        }
    }

    @SneakyThrows
    @Test
    void load_givenMultipleProviders_throwsIllegalArgumentException() {
        Path jarPath = compileAndCreateJar(DUPLICATE_JAR_RESOURCE_NAME);
        URL url = jarPath.toUri().toURL();
        try (ProtocolLoader victim = new ProtocolLoader(url)) {
            assertThatThrownBy(() -> victim.load("test-args"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Several protocols builder providers found");
        }
    }

    @SneakyThrows
    @Test
    void load_givenValidJar_returnsWorkingImplementation() {
        Path jarPath = compileAndCreateJar(VALID_JAR_RESOURCE_NAME);
        URL url = jarPath.toUri().toURL();

        try (ProtocolLoader victim = new ProtocolLoader(url)) {
            Protocol protocol = victim.load("tEsT-aRgS");

            assertThat(getProtocolTestMessage(protocol::obfuscate))
                    .isEqualTo("test-args");
            assertThat(getProtocolTestMessage(protocol::deobfuscate))
                    .isEqualTo("TEST-ARGS");
            assertThat(protocol.maxDataSize(42))
                    .isEqualTo(42);
        }
    }

    private static String getProtocolTestMessage(Consumer<ByteBuffer> protocolMethod) {
        ByteBuffer buffer = ByteBuffer.allocate(TEST_WG_MTU);
        protocolMethod.accept(buffer);
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }
}
