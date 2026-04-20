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
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * Configuration support for environment variables.
 */
public final class EnvVarConfigSource implements ConfigSource {

    @Override
    public Optional<String> get(ConfigProperty propKey) {
        EnvVar envVar = EnvVar.forKey(propKey);
        return EnvVarUtils.getEnvVar(envVar.name);
    }

    @Getter
    @RequiredArgsConstructor
    private enum EnvVar {
        LOG_LEVEL(ConfigProperty.LOG_LEVEL, "OBFUSCATE_WG_LOG_LEVEL"),
        PROTOCOL_URL(ConfigProperty.PROTOCOL_URL, "OBFUSCATE_WG_PROTOCOL_URL"),
        PROTOCOL_ARGS(ConfigProperty.PROTOCOL_ARGS, "OBFUSCATE_WG_PROTOCOL_ARGS"),
        MODE(ConfigProperty.MODE, "OBFUSCATE_WG_MODE"),
        WG_MTU(ConfigProperty.WG_MTU, "OBFUSCATE_WG_WG_MTU"),
        IP(ConfigProperty.IP, "OBFUSCATE_WG_IP"),
        PORT(ConfigProperty.PORT, "OBFUSCATE_WG_PORT"),
        PEER_IP(ConfigProperty.PEER_IP, "OBFUSCATE_WG_PEER_IP"),
        PEER_PORT(ConfigProperty.PEER_PORT, "OBFUSCATE_WG_PEER_PORT"),
        QUEUE_SIZE(ConfigProperty.QUEUE_SIZE, "OBFUSCATE_WG_QUEUE_SIZE"),
        QUEUE_PROCESSORS(ConfigProperty.QUEUE_PROCESSORS, "OBFUSCATE_WG_QUEUE_PROCESSORS"),
        MAX_SESSIONS(ConfigProperty.MAX_SESSIONS, "OBFUSCATE_WG_MAX_SESSIONS");

        private final ConfigProperty propKey;
        private final String name;

        private static EnvVar forKey(ConfigProperty propKey) {
            return Arrays.stream(values())
                    .filter(v -> v.propKey.equals(propKey))
                    .findAny().orElseThrow(() -> new UnsupportedOperationException(
                            "No env var exists for key %s".formatted(propKey)
                    ));
        }
    }
}
