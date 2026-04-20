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
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * Configuration support for JVM parameters.
 */
public final class JvmPropConfigSource implements ConfigSource {

    @Override
    public Optional<String> get(ConfigProperty propKey) {
        Property property = Property.forKey(propKey);
        return JvmPropUtils.getJvmProp(property.name);
    }

    @Getter
    @RequiredArgsConstructor
    private enum Property {
        LOG_LEVEL(ConfigProperty.LOG_LEVEL, "log.level"),
        PROTOCOL_URL(ConfigProperty.PROTOCOL_URL, "protocol.url"),
        PROTOCOL_ARGS(ConfigProperty.PROTOCOL_ARGS, "protocol.args"),
        MODE(ConfigProperty.MODE, "mode"),
        WG_MTU(ConfigProperty.WG_MTU, "wg.mtu"),
        IP(ConfigProperty.IP, "ip"),
        PORT(ConfigProperty.PORT, "port"),
        PEER_IP(ConfigProperty.PEER_IP, "peer.ip"),
        PEER_PORT(ConfigProperty.PEER_PORT, "peer.port"),
        QUEUE_SIZE(ConfigProperty.QUEUE_SIZE, "queue.size"),
        QUEUE_PROCESSORS(ConfigProperty.QUEUE_PROCESSORS, "queue.processors"),
        MAX_CLIENT_SESSIONS(ConfigProperty.MAX_SESSIONS, "max.sessions");

        private final ConfigProperty propKey;
        private final String name;

        private static Property forKey(ConfigProperty propKey) {
            return Arrays.stream(values())
                    .filter(v -> v.propKey.equals(propKey))
                    .findAny().orElseThrow(() -> new UnsupportedOperationException(
                            "No arg exists for key %s".formatted(propKey)
                    ));
        }
    }
}
