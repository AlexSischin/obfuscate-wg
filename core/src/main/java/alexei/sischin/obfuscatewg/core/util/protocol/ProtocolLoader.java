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

package alexei.sischin.obfuscatewg.core.util.protocol;

import alexei.sischin.obfuscatewg.protocol.spi.ProtocolBuilder;
import alexei.sischin.obfuscatewg.protocol.api.Protocol;
import org.jspecify.annotations.Nullable;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;

public class ProtocolLoader implements AutoCloseable {

    private final URLClassLoader classLoader;

    public ProtocolLoader(URL url) {
        this.classLoader = new URLClassLoader(new URL[]{url});
    }

    public Protocol load(@Nullable String args) throws IllegalArgumentException {
        ServiceLoader<ProtocolBuilder> loader = ServiceLoader.load(ProtocolBuilder.class, classLoader);
        List<ProtocolBuilder> availableProtocolBuilders = loader.stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        if (availableProtocolBuilders.isEmpty()) {
            throw new IllegalArgumentException("No protocol builder providers found");
        } else if (availableProtocolBuilders.size() > 1) {
            throw new IllegalArgumentException("Several protocols builder providers found: %s"
                    .formatted(Arrays.toString(availableProtocolBuilders.toArray())));
        }
        ProtocolBuilder protocolBuilder = availableProtocolBuilders.getFirst();
        try {
            return protocolBuilder.build(args);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to build protocol", e);
        }
    }

    @Override
    public void close() throws Exception {
        this.classLoader.close();
    }
}
