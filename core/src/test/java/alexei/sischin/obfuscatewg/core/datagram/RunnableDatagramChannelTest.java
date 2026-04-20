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

package alexei.sischin.obfuscatewg.core.datagram;

import alexei.sischin.obfuscatewg.core._test.InterruptingExecutors;
import alexei.sischin.obfuscatewg.core._test.MessageUtils;
import alexei.sischin.obfuscatewg.core._test.TestDatagramChannel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static alexei.sischin.obfuscatewg.core._test.ArgUtils.dropoutArgStream;
import static alexei.sischin.obfuscatewg.core._test.Constants.MESSAGE_DELAY;
import static alexei.sischin.obfuscatewg.core._test.Constants.TEST_AWAIT_SECONDS;
import static alexei.sischin.obfuscatewg.core._test.Constants.TEST_BUF_SIZE;
import static alexei.sischin.obfuscatewg.core._test.Constants.TEST_WG_MTU;
import static alexei.sischin.obfuscatewg.core._test.MessageUtils.buildMessage;
import static alexei.sischin.obfuscatewg.core._test.MessageUtils.buildMessageCollector;
import static alexei.sischin.obfuscatewg.core._test.MessageUtils.buildMessageSet;
import static alexei.sischin.obfuscatewg.core._test.MessageUtils.decodeMessage;
import static alexei.sischin.obfuscatewg.core._test.MessageUtils.encodeMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Named.named;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RunnableDatagramChannelTest {

    @MethodSource("newDropoutArgs")
    @ParameterizedTest
    public void new_givenNullArgument_throwsNullPointerException(
            InetSocketAddress bindAddress,
            BiConsumer<ByteBuffer, InetSocketAddress> receiveCallback,
            Integer bufSize
    ) {
        assertThatThrownBy(() -> new RunnableDatagramChannel(bindAddress, receiveCallback, bufSize))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("must not be null");
    }

    @SneakyThrows
    @Test
    public void setConnectionAddress_givenUninitialized_throwsIllegalStateException() {
        RunnableDatagramChannel victim = new RunnableDatagramChannel(
                new InetSocketAddress(InetAddress.getLocalHost(), 0),
                (_, _) -> {},
                TEST_BUF_SIZE
        );
        try (ExecutorService victimExecutor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            victimExecutor.submit(victim);

            victim.awaitInitialization(Duration.ofSeconds(1));

            assertThatThrownBy(() -> victim.setConnectionAddress(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Channel is initialized");
        }
    }

    @SneakyThrows
    @Test
    public void getLocalAddress_givenNotInitialized_throwsIllegalStateException() {
        RunnableDatagramChannel victim = new RunnableDatagramChannel(
                new InetSocketAddress(InetAddress.getLocalHost(), 0),
                (_, _) -> {},
                TEST_BUF_SIZE
        );
        assertThatThrownBy(victim::getLocalAddress)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Channel is not initialized");
    }

    @SneakyThrows
    @Test
    public void getLocalAddress_givenBound_returnsNormally() {
        RunnableDatagramChannel victim = new RunnableDatagramChannel(
                new InetSocketAddress(InetAddress.getLocalHost(), 0),
                (_, _) -> {},
                TEST_BUF_SIZE
        );
        try (ExecutorService victimExecutor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            victimExecutor.submit(victim);
            victim.awaitInitialization(Duration.ofSeconds(1));

            assertThat(victim.getLocalAddress())
                    .isNotNull()
                    .satisfies(localAddress ->
                            assertThat(localAddress.getAddress()).isEqualTo(InetAddress.getLocalHost()));

        }
    }

    @SneakyThrows
    @Test
    public void send_givenUninitialized_throwsIllegalStateException() {
        RunnableDatagramChannel victim = new RunnableDatagramChannel(
                new InetSocketAddress(InetAddress.getLocalHost(), 0),
                (_, _) -> {},
                TEST_BUF_SIZE
        );
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), 51820);
        victim.setConnectionAddress(address);
        ByteBuffer data = ByteBuffer.allocate(1);
        assertThatThrownBy(() -> victim.send(data))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Channel is not initialized");
    }

    @SneakyThrows
    @Test
    public void send_givenDisconnected_throwsIllegalStateException() {
        RunnableDatagramChannel victim = new RunnableDatagramChannel(
                new InetSocketAddress(InetAddress.getLocalHost(), 0),
                (_, _) -> {},
                TEST_BUF_SIZE
        );
        try (ExecutorService victimExecutor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            victimExecutor.submit(victim);
            victim.awaitInitialization(Duration.ofSeconds(1));

            ByteBuffer data = ByteBuffer.allocate(1);
            assertThatThrownBy(() -> victim.send(data))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Channel is not connected");
        }
    }

    @SneakyThrows
    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    public void send_givenInitialized_sendsSuccessfully(boolean withConnection) {
        int messageNumber = 100;
        Set<String> messages = buildMessageSet(messageNumber);

        try (ExecutorService executor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            TestDatagramChannel peer = new TestDatagramChannel(TEST_BUF_SIZE);
            executor.submit(peer);
            peer.awaitStarted(Duration.ofSeconds(TEST_AWAIT_SECONDS));
            InetSocketAddress peerAddress = peer.getAddress();

            RunnableDatagramChannel victim = new RunnableDatagramChannel(
                    new InetSocketAddress(InetAddress.getLocalHost(), 0),
                    (_, _) -> {},
                    TEST_BUF_SIZE
            );
            if (withConnection) {
                victim.setConnectionAddress(peerAddress);
            }
            executor.submit(victim);
            victim.awaitInitialization(Duration.ofSeconds(TEST_AWAIT_SECONDS));

            for (String message : messages) {
                ByteBuffer data = encodeMessage(message, TEST_BUF_SIZE);
                if (withConnection) {
                    victim.send(data);
                } else {
                    victim.send(data, peerAddress);
                }
            }
            peer.verifyReceivedExactly(messages, Duration.ofSeconds(TEST_AWAIT_SECONDS));
            assertThat(peer.getRemoteAddresses()).hasSize(1);
        }
    }

    @SneakyThrows
    @MethodSource("clientAndMessageNumbers")
    @ParameterizedTest
    public void run_givenConcurrentClients_invokesReceiveCallback(
            Integer clientNumber,
            Integer messageNumber
    ) {
        try (ExecutorService executor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            MessageUtils.MessageCollector messageCollector = buildMessageCollector();
            RunnableDatagramChannel victim = new RunnableDatagramChannel(
                    new InetSocketAddress(InetAddress.getLocalHost(), 0),
                    messageCollector.consumer(),
                    TEST_BUF_SIZE
            );
            executor.submit(victim);
            victim.awaitInitialization(Duration.ofSeconds(TEST_AWAIT_SECONDS));
            InetSocketAddress victimAddress = victim.getLocalAddress();

            Map<InetSocketAddress, Set<String>> sentMessageMap = new HashMap<>();
            CyclicBarrier sendBarrier = new CyclicBarrier(clientNumber);
            for (int c = 0; c < clientNumber; c++) {
                String label = "client-%s".formatted(c);
                Set<String> messages = buildMessageSet(messageNumber, label);

                TestDatagramChannel client = new TestDatagramChannel(TEST_BUF_SIZE);
                executor.submit(client);
                client.awaitStarted(Duration.ofSeconds(TEST_AWAIT_SECONDS));
                InetSocketAddress address = client.getAddress();

                executor.submit(() -> {
                    try {
                        sendBarrier.await();
                        client.send(messages, victimAddress);
                    } catch (Exception e) {
                        log.error("Failed to send messages", e);
                    }
                });
                sentMessageMap.put(address, messages);
            }

            await().timeout(Duration.ofSeconds(TEST_AWAIT_SECONDS))
                    .pollInterval(Duration.ofMillis(1))
                    .untilAsserted(() -> {
                        assertThat(messageCollector.messages().keySet())
                                .containsExactlyInAnyOrderElementsOf(sentMessageMap.keySet());
                        messageCollector.messages().forEach((address, messages) -> {
                            Set<String> sentMessages = sentMessageMap.get(address);
                            assertThat(messages).containsExactlyElementsOf(sentMessages);
                        });
                    });
        }
    }

    @SneakyThrows
    @Test
    public void run_givenConnected_invokesReceiveCallbackOnlyForConnectedAddress() {
        int messageNumber = 50;
        Set<String> authorizedMessages = buildMessageSet(messageNumber, "authorized");
        Set<String> unauthorizedMessages = buildMessageSet(messageNumber, "unauthorized");

        try (ExecutorService executor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            TestDatagramChannel authorizedClient = new TestDatagramChannel(TEST_BUF_SIZE);
            executor.submit(authorizedClient);
            authorizedClient.awaitStarted(Duration.ofSeconds(TEST_AWAIT_SECONDS));
            InetSocketAddress authorizedClientAddress = authorizedClient.getAddress();

            TestDatagramChannel unauthorizedClient = new TestDatagramChannel(TEST_BUF_SIZE);
            executor.submit(unauthorizedClient);
            unauthorizedClient.awaitStarted(Duration.ofSeconds(TEST_AWAIT_SECONDS));

            MessageUtils.MessageCollector messageCollector = buildMessageCollector();
            RunnableDatagramChannel victim = new RunnableDatagramChannel(
                    new InetSocketAddress(InetAddress.getLocalHost(), 0),
                    messageCollector.consumer(),
                    TEST_BUF_SIZE
            );
            victim.setConnectionAddress(authorizedClientAddress);
            executor.submit(victim);
            victim.awaitInitialization(Duration.ofSeconds(TEST_AWAIT_SECONDS));
            InetSocketAddress victimAddress = victim.getLocalAddress();

            CyclicBarrier sendBarrier = new CyclicBarrier(2);
            executor.submit(() -> {
                try {
                    sendBarrier.await();
                    authorizedClient.send(authorizedMessages, victimAddress);
                } catch (Exception e) {
                    log.error("Failed to send messages", e);
                }
            });
            executor.submit(() -> {
                try {
                    sendBarrier.await();
                    unauthorizedClient.send(unauthorizedMessages, victimAddress);
                } catch (Exception e) {
                    log.error("Failed to send messages", e);
                }
            });

            await().timeout(Duration.ofSeconds(TEST_AWAIT_SECONDS))
                    .pollInterval(Duration.ofMillis(1))
                    .untilAsserted(() -> {
                        assertThat(messageCollector.messages().keySet()).containsExactly(authorizedClientAddress);
                        List<String> receivedMessages = messageCollector.messages().get(authorizedClientAddress);
                        assertThat(receivedMessages).containsExactlyInAnyOrderElementsOf(authorizedMessages);
                    });
        }
    }

    @SneakyThrows
    @Test
    public void run_givenInsufficientBuffer_invokesReceiveCallbackWithCroppedData() {
        int messageNumber = 100;
        int bufSize = 9;

        try (ExecutorService executor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            MessageUtils.MessageCollector messageCollector = buildMessageCollector();
            RunnableDatagramChannel victim = new RunnableDatagramChannel(
                    new InetSocketAddress(InetAddress.getLocalHost(), 0),
                    messageCollector.consumer(),
                    bufSize
            );
            executor.submit(victim);
            victim.awaitInitialization(Duration.ofSeconds(TEST_AWAIT_SECONDS));
            InetSocketAddress victimAddress = victim.getLocalAddress();

            TestDatagramChannel client = new TestDatagramChannel(TEST_BUF_SIZE);
            executor.submit(client);
            client.awaitStarted(Duration.ofSeconds(TEST_AWAIT_SECONDS));
            InetSocketAddress clientAddress = client.getAddress();

            Set<String> messages = buildMessageSet(messageNumber);
            client.send(messages, victimAddress);

            List<String> expectedMessages = messages.stream()
                    .map(m -> m.substring(0, bufSize))
                    .toList();
            await().timeout(Duration.ofSeconds(TEST_AWAIT_SECONDS))
                    .pollInterval(Duration.ofMillis(1))
                    .untilAsserted(() -> {
                        assertThat(messageCollector.messages().keySet()).containsExactly(clientAddress);
                        List<String> receivedMessages = messageCollector.messages().get(clientAddress);
                        assertThat(receivedMessages).containsExactlyInAnyOrderElementsOf(expectedMessages);
                    });
        }
    }

    @SneakyThrows
    @Test
    public void run_givenInterruption_finishesGracefully() {
        InetSocketAddress bindAddress = new InetSocketAddress(InetAddress.getLocalHost(), 0);
        BiConsumer<ByteBuffer, InetSocketAddress> receiveCallback = (_, _) -> {};
        RunnableDatagramChannel victim = new RunnableDatagramChannel(bindAddress, receiveCallback, TEST_BUF_SIZE);

        Thread victimThread = new Thread(victim, "victim");
        victimThread.start();
        victim.awaitInitialization(Duration.ofSeconds(5));

        log.info("Sending interruption signal");
        victimThread.interrupt();
        assertThat(victimThread.join(Duration.ofSeconds(5))).isTrue();
    }

    @SneakyThrows
    private static Stream<Arguments> newDropoutArgs() {
        return dropoutArgStream(
                named("localhost:0", new InetSocketAddress(InetAddress.getLocalHost(), 0)),
                named("noop", (BiConsumer<DatagramPacket, InetSocketAddress>) (_, _) -> {}),
                named(String.valueOf(TEST_BUF_SIZE), TEST_BUF_SIZE)
        );
    }

    public Stream<Arguments> clientAndMessageNumbers() {
        int totalMessageNumberPerTest = 100;
        List<Arguments> argumentsList = new LinkedList<>();
        IntStream.of(1, 2, 5, 10).forEach(clientNumber ->
                argumentsList.add(Arguments.of(
                        clientNumber,
                        totalMessageNumberPerTest / clientNumber
                ))
        );
        return argumentsList.stream();
    }

    @SneakyThrows
    private static Set<String> sendTestMessagesFromVictim(
            InetSocketAddress testAddress,
            int numberOfMessages,
            RunnableDatagramChannel victim
    ) {
        Set<String> messages = new HashSet<>();
        for (int i = 0; i < numberOfMessages; i++) {
            String message = buildMessage(i);
            messages.add(message);
            ByteBuffer datagram = encodeMessage(message, TEST_WG_MTU);
            Thread.sleep(MESSAGE_DELAY);
            victim.send(datagram, testAddress);
        }
        return messages;
    }

    private BiConsumer<ByteBuffer, InetSocketAddress> buildReceiveCallback(
            Collection<String> actualMessageCaptor,
            Collection<InetSocketAddress> actualAddressCaptor,
            CountDownLatch latch
    ) {
        return (data, address) -> {
            actualAddressCaptor.add(address);
            String message = decodeMessage(data);
            actualMessageCaptor.add(message);
            latch.countDown();
            log.debug("Successfully invoked callback for message \"{}\"", message);
        };
    }
}
