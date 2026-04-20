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

package alexei.sischin.obfuscatewg.gui.config;

import alexei.sischin.obfuscatewg.core.util.bridge.BridgeConfig;
import alexei.sischin.obfuscatewg.core.util.logging.LogConfig;
import org.jspecify.annotations.Nullable;

public record SerializableConfig(
        @Nullable String logLevel,
        @Nullable String protocolUrl,
        @Nullable String protocolArgs,
        @Nullable String mode,
        @Nullable String wgMTU,
        @Nullable String ip,
        @Nullable String port,
        @Nullable String peerIp,
        @Nullable String peerPort,
        @Nullable String queueSize,
        @Nullable String queueProcessors,
        @Nullable String maxSessions
) {

    public BridgeConfig toConfig() {
        return BridgeConfig.fromRawStrings(
                this.protocolUrl,
                this.protocolArgs,
                this.mode,
                this.wgMTU,
                this.ip,
                this.port,
                this.peerIp,
                this.peerPort,
                this.queueSize,
                this.queueProcessors,
                this.maxSessions
        );
    }

    public LogConfig toLogConfig() {
        return LogConfig.fromRawStrings(
                this.logLevel
        );
    }
}
