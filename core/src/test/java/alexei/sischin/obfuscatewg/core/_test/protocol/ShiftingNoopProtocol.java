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

package alexei.sischin.obfuscatewg.core._test.protocol;

import alexei.sischin.obfuscatewg.protocol.api.Protocol;

import java.nio.ByteBuffer;

public class ShiftingNoopProtocol implements Protocol {

    private static final int SHIFT = 4;

    @Override
    public void obfuscate(ByteBuffer data) {
        shiftRight(data);
    }

    @Override
    public void deobfuscate(ByteBuffer data) {
        shiftRight(data);
    }

    private static void shiftRight(ByteBuffer data) {
        System.arraycopy(
                data.array(),
                data.arrayOffset() + data.position(),
                data.array(),
                data.arrayOffset() + data.position() + SHIFT,
                data.remaining()
        );
        data.limit(data.limit() + SHIFT).position(data.position() + SHIFT);
    }

    @Override
    public int maxDataSize(int wgMTU) {
        return wgMTU + SHIFT;
    }
}
