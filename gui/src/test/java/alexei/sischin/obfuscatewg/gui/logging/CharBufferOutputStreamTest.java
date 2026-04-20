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

import alexei.sischin.obfuscatewg.gui._test.InterruptingExecutors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CharBufferOutputStreamTest {

    private static final int BYTE_BUFFER_CAPACITY = 128;

    @SneakyThrows
    @MethodSource("stringsAndCharsets")
    @ParameterizedTest
    void write_givenBytes_buffersCorrectChars(String inputString, Charset charset) {
        StringBuilder sb = new StringBuilder();
        Consumer<CharBuffer> consumer = cb -> {
            while (cb.remaining() > 0) {
                sb.append(cb.get());
            }
        };
        CharBufferOutputStream victim = new CharBufferOutputStream(charset, consumer, BYTE_BUFFER_CAPACITY);

        byte[] bytes = inputString.getBytes(charset);
        victim.write(bytes);
        String outputStringBeforeFlush = sb.toString();
        assertThat(outputStringBeforeFlush).isEmpty();

        victim.flush();
        String outputStringAfterFlush = sb.toString();
        assertThat(outputStringAfterFlush).isEqualTo(inputString);
    }

    @SneakyThrows
    @MethodSource("stringsAndCharsets")
    @ParameterizedTest
    void write_givenBytesFromConcurrentThreads_buffersCharsIndependently(String inputString, Charset charset) {
        StringBuilder sb = new StringBuilder();
        Consumer<CharBuffer> consumer = cb -> {
            synchronized (sb) {
                while (cb.remaining() > 0) {
                    sb.append(cb.get());
                }
            }
        };
        CharBufferOutputStream victim = new CharBufferOutputStream(charset, consumer, BYTE_BUFFER_CAPACITY);

        int nThreads = 100;
        CyclicBarrier startBarrier = new CyclicBarrier(nThreads);
        CountDownLatch finishLatch = new CountDownLatch(nThreads);
        try (ExecutorService executor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            for (int t = 0; t < nThreads; t++) {
                executor.submit(() -> {
                    try {
                        byte[] bytes = inputString.getBytes(charset);
                        startBarrier.await(5, TimeUnit.SECONDS);
                        victim.write(bytes);
                        victim.flush();
                        finishLatch.countDown();
                    } catch (Exception e) {
                        log.error("Failed to write or flush bytes", e);
                    }
                });
            }
            assertThat(finishLatch.await(5, TimeUnit.SECONDS)).isTrue();
        }

        String actualString = sb.toString();
        String expectedString = inputString.repeat(nThreads);
        assertThat(actualString).isEqualTo(expectedString);
    }

    @SneakyThrows
    @Test
    void write_givenConsecutiveInvocations_worksWithBufferCorrectly() {
        StringBuilder sb = new StringBuilder();
        Consumer<CharBuffer> consumer = cb -> {
            while (cb.remaining() > 0) {
                sb.append(cb.get());
            }
        };
        Charset charset = StandardCharsets.UTF_8;
        CharBufferOutputStream victim = new CharBufferOutputStream(charset, consumer, BYTE_BUFFER_CAPACITY);

        for (int i = 0; i < 100; i++) {
            String inputString = "message-%s".formatted(i);
            byte[] bytes = inputString.getBytes(charset);
            victim.write(bytes);

            String outputStringBeforeFlush = sb.toString();
            assertThat(outputStringBeforeFlush).isEmpty();

            victim.flush();
            String outputStringAfterFlush = sb.toString();
            assertThat(outputStringAfterFlush).isEqualTo(inputString);

            sb.delete(0, sb.length());
        }
    }

    static Stream<Arguments> stringsAndCharsets() {
        List<Arguments> arguments = new LinkedList<>();
        String[] strings = new String[]{
                "text",
                "текст",
                "文本",
                "0123456789",
                "`~!@#$%^&*()_+-={}[]:\"|;'\\,./<>?",
                "\uD83D\uDE00"
        };
        Charset[] charsets = new Charset[]{
                StandardCharsets.UTF_8,
                StandardCharsets.UTF_16
        };
        Arrays.stream(strings).forEach(string ->
                Arrays.stream(charsets).forEach(charset ->
                        arguments.add(Arguments.of(string, charset))));
        return arguments.stream();
    }
}
