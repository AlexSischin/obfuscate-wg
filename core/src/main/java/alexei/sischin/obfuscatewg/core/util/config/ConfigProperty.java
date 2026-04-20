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

package alexei.sischin.obfuscatewg.core.util.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public enum ConfigProperty {
    LOG_LEVEL(
            "Logback log level name.",
            "INFO",
            List.of("OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE")
    ),
    PROTOCOL_URL(
            "Protocol implementation URL."
    ),
    PROTOCOL_ARGS(
            "Protocol arguments. Typically, a symmetric key.",
            ""
    ),
    MODE(
            "Bridge mode.",
            "NOOP",
            List.of("OBFUSCATOR", "DEOBFUSCATOR", "NOOP")
    ),
    WG_MTU(
            "WireGuard MTU. Must be equal to the proxied WG MTU setting.",
            "1420"
    ),
    IP(
            "Listen IP address.",
            "0.0.0.0"
    ),
    PORT(
            "Listen port number.",
            "51820"
    ),
    PEER_IP(
            "Peer IP address."
    ),
    PEER_PORT(
            "Peer port number.",
            "51820"
    ),
    QUEUE_SIZE(
            "Client datagram queue size.",
            "73000" // ~100 MB total
    ),
    QUEUE_PROCESSORS(
            "Number of client datagram processors. 0 means equal to the processor number.",
            "0"
    ),
    MAX_SESSIONS(
            "Maximum number of concurrent sessions.",
            "256"
    );

    @Getter
    private final String description;

    @Nullable
    private final String defaultValue;

    @Nullable
    private final LinkedHashSet<String> possibleValues;

    ConfigProperty(String description) {
        this(description, null, null);
    }

    ConfigProperty(String description, @Nullable String defaultValue) {
        this(description, defaultValue, null);
    }

    ConfigProperty(String description, @Nullable String defaultValue, @Nullable Collection<String> possibleValues) {
        this.description = description;
        this.defaultValue = defaultValue;
        this.possibleValues = Optional.ofNullable(possibleValues).map(LinkedHashSet::new).orElse(null);
    }

    public Optional<String> getDefaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    public Optional<LinkedHashSet<String>> getPossibleValues() {
        return Optional.ofNullable(possibleValues);
    }
}
