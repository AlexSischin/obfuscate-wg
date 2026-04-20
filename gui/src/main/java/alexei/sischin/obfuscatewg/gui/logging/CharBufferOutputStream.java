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

package alexei.sischin.obfuscatewg.gui.logging;

import org.jspecify.annotations.Nullable;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.Objects;
import java.util.function.Consumer;

public class CharBufferOutputStream extends OutputStream {

    private final Charset charset;
    private final Consumer<CharBuffer> charCallback;
    private final int byteBufferCapacity;

    private final ThreadLocal<@Nullable ByteBuffer> byteBuffer = new ThreadLocal<>();
    private final ThreadLocal<@Nullable CharsetDecoder> decoder = new ThreadLocal<>();
    private final ThreadLocal<@Nullable CharBuffer> charBuffer = new ThreadLocal<>();

    public CharBufferOutputStream(Charset charset, Consumer<CharBuffer> charCallback, Integer byteBufferCapacity) {
        Objects.requireNonNull(charset, "charset must not be null");
        Objects.requireNonNull(charCallback, "charCallback must not be null");
        Objects.requireNonNull(byteBufferCapacity, "byteBufferCapacity must not be null");

        this.charset = charset;
        this.charCallback = charCallback;
        this.byteBufferCapacity = byteBufferCapacity;
    }

    @Override
    public void write(int b) {
        ByteBuffer byteBuffer = getByteBuffer();
        byteBuffer.put((byte) b);
    }

    @Override
    public void flush() {
        ByteBuffer byteBuffer = getByteBuffer().flip();
        CharBuffer charBuffer = getCharBuffer().clear();
        CharsetDecoder decoder = getDecoder();

        CoderResult result = decoder.decode(byteBuffer, charBuffer, false);
        byteBuffer.compact();
        if (!result.isUnderflow()) {
            throw new IllegalStateException("Unexpected coder result: %s".formatted(result));
        }
        this.charCallback.accept(charBuffer.flip());
    }

    private CharsetDecoder getDecoder() {
        if (this.decoder.get() == null) {
            this.decoder.set(this.charset.newDecoder());
        }
        return this.decoder.get();
    }

    private ByteBuffer getByteBuffer() {
        if (this.byteBuffer.get() == null) {
            ByteBuffer newByteBuffer = ByteBuffer.allocate(this.byteBufferCapacity);
            this.byteBuffer.set(newByteBuffer);
        }
        return this.byteBuffer.get();
    }

    private CharBuffer getCharBuffer() {
        if (this.charBuffer.get() == null) {
            CharsetDecoder decoder = getDecoder();
            int charBufferCapacity = (int) Math.ceil(this.byteBufferCapacity * decoder.maxCharsPerByte());
            CharBuffer newCharBuffer = CharBuffer.allocate(charBufferCapacity);
            this.charBuffer.set(newCharBuffer);
        }
        return this.charBuffer.get();
    }
}
