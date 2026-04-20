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

package alexei.sischin.obfuscatewg.cli;

import alexei.sischin.obfuscatewg.cli.config.CliApi;
import alexei.sischin.obfuscatewg.cli.config.EnvVarConfigSource;
import alexei.sischin.obfuscatewg.cli.config.JvmPropConfigSource;
import alexei.sischin.obfuscatewg.core.datagram.DatagramBridge;
import alexei.sischin.obfuscatewg.core.util.protocol.ProtocolLoader;
import alexei.sischin.obfuscatewg.core.util.bridge.BridgeConfig;
import alexei.sischin.obfuscatewg.core.util.bridge.DatagramBridgeBuilder;
import alexei.sischin.obfuscatewg.cli.config.ConfigLoader;
import alexei.sischin.obfuscatewg.core.util.logging.LogConfig;
import alexei.sischin.obfuscatewg.core.util.logging.LogbackConfig;
import alexei.sischin.obfuscatewg.protocol.api.Protocol;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
class App {

    static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new ShutdownHook());
        LogbackConfig.init();
        loadConfig(args).ifPresent(configTuple -> {
            LogbackConfig.configure(configTuple.logConfig());
            BridgeConfig bridgeConfig = configTuple.bridgeConfig();
            log.info("{}", bridgeConfig);

            runWithProtocolLoader(bridgeConfig, protocolLoader ->
                    loadProtocol(protocolLoader, bridgeConfig)
                            .flatMap(protocol -> buildDatagramBridge(bridgeConfig, protocol))
                            .ifPresent(App::run));
        });
    }

    private static Optional<ConfigTuple> loadConfig(String[] args) {
        CliApi cliApi = new CliApi();
        try {
            cliApi.initialize(args);
            if (cliApi.isHelp()) {
                cliApi.printHelp();
                return Optional.empty();
            }
            ConfigLoader configLoader = new ConfigLoader(
                    cliApi,
                    new EnvVarConfigSource(),
                    new JvmPropConfigSource()
            );
            BridgeConfig config = configLoader.loadBridgeConfig();
            LogConfig logConfig = configLoader.loadLogConfig();
            return Optional.of(new ConfigTuple(config, logConfig));
        } catch (Exception configException) {
            log.error("Configuration error", configException);
            try {
                cliApi.printHelp();
            } catch (Exception printException) {
                log.error("Failed to print help", printException);
            }
            return Optional.empty();
        }
    }

    private static void runWithProtocolLoader(BridgeConfig bridgeConfig, Consumer<ProtocolLoader> consumer) {
        ProtocolLoader protocolLoader;
        try {
            protocolLoader = new ProtocolLoader(bridgeConfig.protocolUrl());
        } catch (Exception e) {
            log.error("Failed to build protocol loader", e);
            return;
        }
        try {
            consumer.accept(protocolLoader);
        } finally {
            try {
                protocolLoader.close();
            } catch (Exception e) {
                log.error("Failed to close protocol loader", e);
            }
        }
    }

    private static Optional<Protocol> loadProtocol(ProtocolLoader protocolLoader, BridgeConfig bridgeConfig) {
        try {
            String args = bridgeConfig.protocolArgs().orElse(null);
            Protocol protocol = protocolLoader.load(args);
            log.info("Loaded protocol: {}", protocol.getClass());
            return Optional.of(protocol);
        } catch (Exception e) {
            log.error("Failed to load protocol", e);
            return Optional.empty();
        }
    }

    private static Optional<DatagramBridge> buildDatagramBridge(BridgeConfig config, Protocol protocol) {
        try {
            return Optional.of(DatagramBridgeBuilder.build(config, protocol));
        } catch (Exception e) {
            log.error("Failed to build datagram bridge", e);
            return Optional.empty();
        }
    }

    private static void run(DatagramBridge orchestrator) {
        try {
            orchestrator.run();
        } catch (Exception e) {
            log.error("Unexpected error occurred in datagram bridge", e);
        }
    }

    private record ConfigTuple(
            BridgeConfig bridgeConfig,
            LogConfig logConfig
    ) {}
}
