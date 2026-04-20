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

package alexei.sischin.obfuscatewg.cli.config;

import alexei.sischin.obfuscatewg.cli.util.EnvVarUtils;
import alexei.sischin.obfuscatewg.core.util.config.ConfigProperty;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.Mockito.mockStatic;

class EnvVarConfigSourceTest extends ConfigSourceTest<EnvVarConfigSource> {

    @BeforeEach
    void beforeEach() {
        super.victim = new EnvVarConfigSource();
    }

    @SneakyThrows
    @MethodSource("validEnvVarArgs")
    @ParameterizedTest
    void get_givenValidValues_returnsThem(Map<String, String> envVars, Map<ConfigProperty, String> expectedValues) {
        try (MockedStatic<EnvVarUtils> _ = mockVars(envVars)) {
            super.get_givenValues_returnsThem(expectedValues);
        }
    }

    @SneakyThrows
    @MethodSource("validEnvVarArgs")
    @ParameterizedTest
    void get_givenValidValues_returnsEmptyForOthers(Map<String, String> envVars, Map<ConfigProperty, String> expectedValues) {
        try (MockedStatic<EnvVarUtils> _ = mockVars(envVars)) {
            super.get_givenValues_returnsEmptyForOthers(expectedValues);
        }
    }

    private static MockedStatic<EnvVarUtils> mockVars(Map<String, String> envVars) {
        MockedStatic<EnvVarUtils> envVarUtils = mockStatic(EnvVarUtils.class);
        try {
            envVars.forEach((name, value) ->
                    envVarUtils.when(() -> EnvVarUtils.getEnvVar(name)).thenReturn(Optional.of(value)));
            return envVarUtils;
        } catch (Exception e) {
            envVarUtils.close();
            throw e;
        }
    }

    public Stream<Arguments> validEnvVarArgs() {
        return Stream.of(
                // Single vars
                Arguments.of(Map.of("OBFUSCATE_WG_LOG_LEVEL", "ERROR"), Map.of(ConfigProperty.LOG_LEVEL, "ERROR")),
                Arguments.of(Map.of("OBFUSCATE_WG_PROTOCOL_URL", "file:///home/user/bin/protocol.jar"),
                        Map.of(ConfigProperty.PROTOCOL_URL, "file:///home/user/bin/protocol.jar")),
                Arguments.of(Map.of("OBFUSCATE_WG_PROTOCOL_ARGS", "key=test"), Map.of(ConfigProperty.PROTOCOL_ARGS, "key=test")),
                Arguments.of(Map.of("OBFUSCATE_WG_MODE", "OBFUSCATOR"), Map.of(ConfigProperty.MODE, "OBFUSCATOR")),
                Arguments.of(Map.of("OBFUSCATE_WG_WG_MTU", "32"), Map.of(ConfigProperty.WG_MTU, "32")),
                Arguments.of(Map.of("OBFUSCATE_WG_IP", "127.0.0.1"), Map.of(ConfigProperty.IP, "127.0.0.1")),
                Arguments.of(Map.of("OBFUSCATE_WG_PORT", "3232"), Map.of(ConfigProperty.PORT, "3232")),
                Arguments.of(Map.of("OBFUSCATE_WG_PEER_IP", "10.0.7.1"), Map.of(ConfigProperty.PEER_IP, "10.0.7.1")),
                Arguments.of(Map.of("OBFUSCATE_WG_PEER_PORT", "2323"), Map.of(ConfigProperty.PEER_PORT, "2323")),
                Arguments.of(Map.of("OBFUSCATE_WG_QUEUE_SIZE", "10000"), Map.of(ConfigProperty.QUEUE_SIZE, "10000")),
                Arguments.of(Map.of("OBFUSCATE_WG_QUEUE_PROCESSORS", "2"), Map.of(ConfigProperty.QUEUE_PROCESSORS, "2")),
                Arguments.of(Map.of("OBFUSCATE_WG_MAX_SESSIONS", "64"), Map.of(ConfigProperty.MAX_SESSIONS, "64")),
                // Multiple vars
                Arguments.of(Map.of(
                        "OBFUSCATE_WG_IP", "127.0.0.1",
                        "OBFUSCATE_WG_PORT", "3232"
                ), Map.of(
                        ConfigProperty.IP, "127.0.0.1",
                        ConfigProperty.PORT, "3232"
                ))
        );
    }
}