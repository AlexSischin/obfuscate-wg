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

package test;

import alexei.sischin.obfuscatewg.protocol.api.Protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class TestProtocol implements Protocol {

    private final String word;

    public TestProtocol(String args) {
        this.word = args;
    }

    @Override
    public void obfuscate(ByteBuffer data) {
        data.put(word.toLowerCase().getBytes(StandardCharsets.US_ASCII)).flip();
    }

    @Override
    public void deobfuscate(ByteBuffer data) {
        data.put(word.toUpperCase().getBytes(StandardCharsets.US_ASCII)).flip();
    }

    @Override
    public int maxDataSize(int wgMTU) {
        return wgMTU;
    }
}
