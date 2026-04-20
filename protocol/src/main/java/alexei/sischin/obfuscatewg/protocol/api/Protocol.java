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

package alexei.sischin.obfuscatewg.protocol.api;

import java.nio.ByteBuffer;

/**
 * WireGuard protocol modifier.
 */
public interface Protocol {

    int WG_PROTOCOL_OVERHEAD = 32;

    /**
     * Turns a raw packet into an obfuscated one.
     * <p>
     * Must be reversible by {@link #deobfuscate(ByteBuffer)}.
     * </p>
     *
     * @param data raw WireGuard packet.
     */
    void obfuscate(ByteBuffer data);

    /**
     * Turns an obfuscated packet into a raw one.
     * <p>
     * Undoes what {@link #obfuscate(ByteBuffer)} does.
     * </p>
     *
     * @param data raw WireGuard packet.
     */
    void deobfuscate(ByteBuffer data);

    /**
     * Defines the capacity of the {@code data} parameter passed to
     * {@link #obfuscate(ByteBuffer)} and {@link #deobfuscate(ByteBuffer)}.
     * <p>
     * The default implementation assumes that the protocol has no overhead over the raw WireGuard protocol.
     * If the protocol creates an overhead you might want to override this method, e.g:
     * </p>
     * <p>
     * {@code super.maxDataSize(wgMTU) + OVERHEAD}
     * </p>
     * @param wgMTU WireGuard MTU value.
     * @return `data` parameter capacity.
     */
    default int maxDataSize(int wgMTU) {
        return wgMTU + WG_PROTOCOL_OVERHEAD;
    }
}
