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

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alexei.sischin.obfuscatewg.core._test.Constants.MESSAGE_DELAY;
import static alexei.sischin.obfuscatewg.core._test.MessageUtils.buildMessageCollector;
import static alexei.sischin.obfuscatewg.core._test.MessageUtils.encodeMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Slf4j
public class TestDatagramChannel implements Runnable {

    private final int bufSize;
    private final Consumer<ByteBuffer> outDataModifier;
    private final MessageUtils.MessageCollector messageCollector;

    private final AtomicBoolean started = new AtomicBoolean();

    private DatagramChannel channel;

    public TestDatagramChannel(int bufSize) {
        this(bufSize, _ -> {}, _ -> {});
    }

    public TestDatagramChannel(
            int bufSize,
            Consumer<ByteBuffer> outDataModifier,
            Consumer<ByteBuffer> inDataModifier
    ) {
        this.bufSize = bufSize;
        this.outDataModifier = outDataModifier;
        this.messageCollector = buildMessageCollector(inDataModifier);
    }

    @Override
    public void run() {
        ByteBuffer buf = ByteBuffer.allocate(bufSize);
        try (DatagramChannel channel = DatagramChannel.open()) {
            this.channel = channel;

            channel.bind(new InetSocketAddress(InetAddress.getLocalHost(), 0));
            started.set(true);
            while (!Thread.interrupted() && channel.isOpen()) {
                InetSocketAddress remoteAddress = (InetSocketAddress) channel.receive(buf.clear());
                messageCollector.consumer().accept(buf.flip(), remoteAddress);
            }
        } catch (ClosedByInterruptException e) {
            // exit silently
        } catch (Exception e) {
            log.error("Error occurred in test datagram channel thread", e);
        }
    }

    public void awaitStarted(Duration timeout) {
        await().timeout(timeout).pollInterval(Duration.ofMillis(1)).untilAsserted(() ->
                assertThat(this.started.get()).isTrue());
    }

    public InetSocketAddress getAddress() throws IOException {
        return (InetSocketAddress) channel.getLocalAddress();
    }

    public Set<InetSocketAddress> getRemoteAddresses() {
        return new HashSet<>(this.messageCollector.messages().keySet());
    }

    public void send(Collection<String> messages, InetSocketAddress target) throws IOException, InterruptedException {
        for (String message : messages) {
            ByteBuffer buf = encodeMessage(message, this.bufSize);
            this.outDataModifier.accept(buf);
            this.channel.send(buf, target);
            log.trace("Sent message {}", message);
            try {
                Thread.sleep(MESSAGE_DELAY);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    public Map<InetSocketAddress, List<String>> getReceivedMessages() {
        return this.messageCollector.messages().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new ArrayList<>(e.getValue())
                ));
    }

    public void verifyReceivedExactly(Collection<String> messages, Duration timeout) {
        await().timeout(timeout).pollInterval(Duration.ofMillis(1)).untilAsserted(() -> {
            Stream<String> allMessages = this.messageCollector.messages().values().stream().flatMap(Collection::stream);
            assertThat(allMessages).containsExactlyInAnyOrderElementsOf(messages);
        });
    }
}
