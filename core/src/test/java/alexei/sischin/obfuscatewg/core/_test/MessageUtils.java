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

package alexei.sischin.obfuscatewg.core._test;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@UtilityClass
public class MessageUtils {

    public static String buildMessage(int i) {
        return "message-%s".formatted(i);
    }

    public static String buildMessage(int i, String label) {
        return "message-%s-%s".formatted(i, label);
    }

    public static Set<String> buildMessageSet(int messageNumber) {
        return IntStream.range(0, messageNumber)
                .mapToObj(MessageUtils::buildMessage)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Set<String> buildMessageSet(int messageNumber, String label) {
        return IntStream.range(0, messageNumber)
                .mapToObj(i -> buildMessage(i, label))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static ByteBuffer encodeMessage(String message, int bufSize) {
        ByteBuffer buffer = ByteBuffer.allocate(bufSize);
        buffer.put(message.getBytes(StandardCharsets.US_ASCII)).flip();
        return buffer;
    }

    public static String decodeMessage(ByteBuffer byteBuffer) {
        byte[] dataArray = new byte[byteBuffer.remaining()];
        byteBuffer.get(dataArray);
        return new String(dataArray, StandardCharsets.US_ASCII);
    }

    public static MessageCollector buildMessageCollector() {
        return buildMessageCollector(_ -> {});
    }

    public static MessageCollector buildMessageCollector(Consumer<ByteBuffer> modifier) {
        Map<InetSocketAddress, List<String>> receivedMessageMap = new ConcurrentHashMap<>();
        BiConsumer<ByteBuffer, InetSocketAddress> consumer = (data, remoteAddress) -> {
            modifier.accept(data);
            String message = decodeMessage(data);
            log.trace("Received message {}", message);
            List<String> messages = receivedMessageMap.compute(
                    remoteAddress,
                    (_, existingMessages) ->
                            existingMessages != null ? existingMessages : new CopyOnWriteArrayList<>()
            );
            messages.add(message);
        };
        return new MessageCollector(consumer, receivedMessageMap);
    }

    public record MessageCollector(
            BiConsumer<ByteBuffer, InetSocketAddress> consumer,
            Map<InetSocketAddress, List<String>> messages
    ) {}
}
