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

import alexei.sischin.obfuscatewg.cli.util.JvmPropUtils;
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

class JvmPropConfigSourceTest extends ConfigSourceTest<JvmPropConfigSource> {

    @BeforeEach
    void beforeEach() {
        super.victim = new JvmPropConfigSource();
    }

    @SneakyThrows
    @MethodSource("validJvmPropsArgs")
    @ParameterizedTest
    void get_givenValues_returnsThem(Map<String, String> jvmProps, Map<ConfigProperty, String> expectedValues) {
        try (MockedStatic<JvmPropUtils> _ = mockProps(jvmProps)) {
            super.get_givenValues_returnsThem(expectedValues);
        }
    }

    @SneakyThrows
    @MethodSource("validJvmPropsArgs")
    @ParameterizedTest
    void get_givenValues_returnsEmptyForOthers(Map<String, String> jvmProps, Map<ConfigProperty, String> expectedValues) {
        try (MockedStatic<JvmPropUtils> _ = mockProps(jvmProps)) {
            super.get_givenValues_returnsEmptyForOthers(expectedValues);
        }
    }

    private static MockedStatic<JvmPropUtils> mockProps(Map<String, String> jvmProps) {
        MockedStatic<JvmPropUtils> envVarUtils = mockStatic(JvmPropUtils.class);
        try {
            jvmProps.forEach((name, value) ->
                    envVarUtils.when(() -> JvmPropUtils.getJvmProp(name)).thenReturn(Optional.of(value)));
            return envVarUtils;
        } catch (Exception e) {
            envVarUtils.close();
            throw e;
        }
    }

    public Stream<Arguments> validJvmPropsArgs() {
        return Stream.of(
                // Single vars
                Arguments.of(Map.of("log.level", "ERROR"), Map.of(ConfigProperty.LOG_LEVEL, "ERROR")),
                Arguments.of(Map.of("protocol.url", "file:///home/user/bin/protocol.jar"),
                        Map.of(ConfigProperty.PROTOCOL_URL, "file:///home/user/bin/protocol.jar")),
                Arguments.of(Map.of("protocol.args", "key=test"), Map.of(ConfigProperty.PROTOCOL_ARGS, "key=test")),
                Arguments.of(Map.of("mode", "OBFUSCATOR"), Map.of(ConfigProperty.MODE, "OBFUSCATOR")),
                Arguments.of(Map.of("wg.mtu", "32"), Map.of(ConfigProperty.WG_MTU, "32")),
                Arguments.of(Map.of("ip", "127.0.0.1"), Map.of(ConfigProperty.IP, "127.0.0.1")),
                Arguments.of(Map.of("port", "3232"), Map.of(ConfigProperty.PORT, "3232")),
                Arguments.of(Map.of("peer.ip", "10.0.7.1"), Map.of(ConfigProperty.PEER_IP, "10.0.7.1")),
                Arguments.of(Map.of("peer.port", "2323"), Map.of(ConfigProperty.PEER_PORT, "2323")),
                Arguments.of(Map.of("queue.size", "10000"), Map.of(ConfigProperty.QUEUE_SIZE, "10000")),
                Arguments.of(Map.of("queue.processors", "2"), Map.of(ConfigProperty.QUEUE_PROCESSORS, "2")),
                Arguments.of(Map.of("max.sessions", "64"), Map.of(ConfigProperty.MAX_SESSIONS, "64")),
                // Multiple vars
                Arguments.of(Map.of(
                        "ip", "127.0.0.1",
                        "port", "3232"
                ), Map.of(
                        ConfigProperty.IP, "127.0.0.1",
                        ConfigProperty.PORT, "3232"
                ))
        );
    }
}