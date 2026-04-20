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

import alexei.sischin.obfuscatewg.protocol.spi.ProtocolBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Implements datagram proxying and obfuscation, and exports convenient utilities for external use.
 */
@NullMarked
module obfuscate.wg.core {
    requires obfuscate.wg.protocol;

    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;
    requires org.jspecify;
    requires org.slf4j;
    requires java.compiler;

    requires static lombok;

    uses ProtocolBuilder;

    exports alexei.sischin.obfuscatewg.core.datagram;
    exports alexei.sischin.obfuscatewg.core.util.config;
    exports alexei.sischin.obfuscatewg.core.util.bridge;
    exports alexei.sischin.obfuscatewg.core.util.logging;
    exports alexei.sischin.obfuscatewg.core.util.protocol;
}
