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

import alexei.sischin.obfuscatewg.core.datagram.BridgeMode;
import alexei.sischin.obfuscatewg.core.util.bridge.BridgeConfig;
import alexei.sischin.obfuscatewg.core.util.config.ConfigProperty;
import alexei.sischin.obfuscatewg.core.util.logging.LogConfig;
import ch.qos.logback.classic.Level;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoaderTest {

    static final Level DEFAULT_LOG_LEVEL = Level.INFO;
    static final BridgeMode DEFAULT_MODE = BridgeMode.NOOP;
    static final Integer DEFAULT_WG_MTU = 1420;
    static final InetAddress DEFAULT_IP = InetAddress.ofLiteral("0.0.0.0");
    static final Integer DEFAULT_PORT = 51820;
    static final Integer DEFAULT_PEER_PORT = 51820;
    static final Integer DEFAULT_QUEUE_SIZE = 73000;
    static final Integer DEFAULT_QUEUE_PROCESSORS = 0;
    static final Integer DEFAULT_MAX_CLIENT_SESSIONS = 256;

    @SneakyThrows
    @Test
    void loadBridgeConfig_givenSingleApiAndAllProps_returnsSpecifiedProps() {
        Map<ConfigProperty, String> apiValues = new HashMap<>();
        apiValues.put(ConfigProperty.PROTOCOL_URL, "file:///home/user/bin/protocol.jar");
        apiValues.put(ConfigProperty.PROTOCOL_ARGS, "key=test");
        apiValues.put(ConfigProperty.WG_MTU, "32");
        apiValues.put(ConfigProperty.IP, "0.0.0.0");
        apiValues.put(ConfigProperty.PORT, "3232");
        apiValues.put(ConfigProperty.PEER_IP, "10.7.0.1");
        apiValues.put(ConfigProperty.PEER_PORT, "2323");
        apiValues.put(ConfigProperty.QUEUE_SIZE, "20000");
        apiValues.put(ConfigProperty.QUEUE_PROCESSORS, "8");
        apiValues.put(ConfigProperty.MAX_SESSIONS, "128");
        TestSource api = new TestSource(apiValues);
        ConfigLoader victim = new ConfigLoader(api);
        BridgeConfig config = victim.loadBridgeConfig();

        assertThat(config.protocolUrl()).isEqualTo(URI.create("file:///home/user/bin/protocol.jar").toURL());
        assertThat(config.protocolArgs()).contains("key=test");
        assertThat(config.wgMTU()).isEqualTo(32);
        assertThat(config.address()).isEqualTo(new InetSocketAddress(
                InetAddress.ofLiteral("0.0.0.0"),
                3232
        ));
        assertThat(config.peerAddress()).isEqualTo(new InetSocketAddress(
                InetAddress.ofLiteral("10.7.0.1"),
                2323
        ));
        assertThat(config.queueSize()).isEqualTo(20000);
        assertThat(config.queueProcessors()).isEqualTo(8);
        assertThat(config.maxSessions()).isEqualTo(128);
    }

    @SneakyThrows
    @Test
    void loadLogConfig_givenSingleApiAndAllProps_returnsSpecifiedProps() {
        Map<ConfigProperty, String> apiValues = new HashMap<>();
        apiValues.put(ConfigProperty.LOG_LEVEL, "ERROR");
        TestSource api = new TestSource(apiValues);
        ConfigLoader victim = new ConfigLoader(api);
        LogConfig config = victim.loadLogConfig();

        assertThat(config.logLevel()).isEqualTo(Level.ERROR);
    }

    @SneakyThrows
    @Test
    void loadBridgeConfig_givenSingleApiAndOnlyRequiredProps_returnsSpecifiedPropsAndDefaults() {
        TestSource api = new TestSource(Map.of(
                ConfigProperty.PEER_IP, "10.7.0.1",
                ConfigProperty.PROTOCOL_URL, "file:///home/user/bin/protocol.jar"
        ));
        ConfigLoader victim = new ConfigLoader(api);
        BridgeConfig config = victim.loadBridgeConfig();

        assertThat(config.protocolUrl()).isEqualTo(URI.create("file:///home/user/bin/protocol.jar").toURL());
        assertThat(config.protocolArgs()).isEmpty();
        assertThat(config.mode()).isEqualTo(DEFAULT_MODE);
        assertThat(config.wgMTU()).isEqualTo(DEFAULT_WG_MTU);
        assertThat(config.address()).isEqualTo(new InetSocketAddress(
                DEFAULT_IP,
                DEFAULT_PORT
        ));
        assertThat(config.peerAddress()).isEqualTo(new InetSocketAddress(
                InetAddress.ofLiteral("10.7.0.1"),
                DEFAULT_PEER_PORT
        ));
        assertThat(config.queueSize()).isEqualTo(DEFAULT_QUEUE_SIZE);
        assertThat(config.queueProcessors()).isEqualTo(DEFAULT_QUEUE_PROCESSORS);
        assertThat(config.maxSessions()).isEqualTo(DEFAULT_MAX_CLIENT_SESSIONS);
    }

    @SneakyThrows
    @Test
    void loadLogConfig_givenSingleApiAndOnlyRequiredProps_returnsSpecifiedPropsAndDefaults() {
        TestSource api = new TestSource(Map.of());
        ConfigLoader victim = new ConfigLoader(api);
        LogConfig config = victim.loadLogConfig();

        assertThat(config.logLevel()).isEqualTo(DEFAULT_LOG_LEVEL);
    }

    @SneakyThrows
    @Test
    void loadBridgeConfig_givenMultipleApis_returnsPropsAccordingToApiOrder() {
        TestSource api1 = new TestSource(Map.of());
        TestSource api2 = new TestSource(Map.of(ConfigProperty.PEER_IP, "10.7.0.2"));
        TestSource api3 = new TestSource(Map.of(
                ConfigProperty.PEER_IP, "10.7.0.3",
                ConfigProperty.PROTOCOL_URL, "file:///home/user/bin/protocol.jar"
        ));
        TestSource api4 = new TestSource(Map.of());
        TestSource api5 = new TestSource(Map.of(ConfigProperty.PEER_IP, "10.7.0.5"));
        ConfigLoader victim = new ConfigLoader(api1, api2, api3, api4, api5);
        BridgeConfig config = victim.loadBridgeConfig();

        assertThat(config.protocolUrl())
                .isEqualTo(URI.create("file:///home/user/bin/protocol.jar").toURL());
        assertThat(config.peerAddress().getAddress())
                .isEqualTo(InetAddress.ofLiteral("10.7.0.2"));
    }

    record TestSource(
            Map<ConfigProperty, String> propMap
    ) implements ConfigSource {

        @Override
        public Optional<String> get(ConfigProperty propKey) {
            return Optional.ofNullable(propMap.get(propKey));
        }
    }
}
