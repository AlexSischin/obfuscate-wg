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

import alexei.sischin.obfuscatewg.core.util.bridge.BridgeConfig;
import alexei.sischin.obfuscatewg.core.util.config.ConfigProperty;
import alexei.sischin.obfuscatewg.core.util.logging.LogConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class ConfigLoader {

    private final List<ConfigSource> apiList;

    public ConfigLoader(ConfigSource... apis) {
        this.apiList = Arrays.asList(apis);
    }

    public BridgeConfig loadBridgeConfig() throws IllegalArgumentException {
        return BridgeConfig.fromRawStrings(
                getConfigValue(ConfigProperty.PROTOCOL_URL),
                getConfigValue(ConfigProperty.PROTOCOL_ARGS),
                getConfigValue(ConfigProperty.MODE),
                getConfigValue(ConfigProperty.WG_MTU),
                getConfigValue(ConfigProperty.IP),
                getConfigValue(ConfigProperty.PORT),
                getConfigValue(ConfigProperty.PEER_IP),
                getConfigValue(ConfigProperty.PEER_PORT),
                getConfigValue(ConfigProperty.QUEUE_SIZE),
                getConfigValue(ConfigProperty.QUEUE_PROCESSORS),
                getConfigValue(ConfigProperty.MAX_SESSIONS)
        );
    }

    public LogConfig loadLogConfig() throws IllegalArgumentException {
        return LogConfig.fromRawStrings(
                getConfigValue(ConfigProperty.LOG_LEVEL)
        );
    }

    private String getConfigValue(ConfigProperty propKey) {
        return apiList.stream()
                .map(api -> api.get(propKey))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .or(propKey::getDefaultValue)
                .orElseThrow(() -> new NullPointerException("%s is required but is not specified"
                        .formatted(propKey.getDescription())));
    }
}
