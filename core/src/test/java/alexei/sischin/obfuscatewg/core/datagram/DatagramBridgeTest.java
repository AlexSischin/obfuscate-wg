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
import alexei.sischin.obfuscatewg.core._test.TestDatagramChannel;
import alexei.sischin.obfuscatewg.core._test.protocol.InvertingProtocol;
import alexei.sischin.obfuscatewg.core._test.protocol.PaddingProtocol;
import alexei.sischin.obfuscatewg.core._test.protocol.ShiftingNoopProtocol;
import alexei.sischin.obfuscatewg.core._test.protocol.NoopProtocol;
import alexei.sischin.obfuscatewg.protocol.api.Protocol;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static alexei.sischin.obfuscatewg.core._test.ArgUtils.dropoutArgStream;
import static alexei.sischin.obfuscatewg.core._test.Constants.TEST_AWAIT_SECONDS;
import static alexei.sischin.obfuscatewg.core._test.Constants.TEST_MAX_CLIENT_SESSIONS;
import static alexei.sischin.obfuscatewg.core._test.Constants.TEST_QUEUE_PROCESSORS;
import static alexei.sischin.obfuscatewg.core._test.Constants.TEST_QUEUE_SIZE;
import static alexei.sischin.obfuscatewg.core._test.Constants.TEST_WG_MTU;
import static alexei.sischin.obfuscatewg.core._test.MessageUtils.buildMessageSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatagramBridgeTest {

    @MethodSource("newDropoutArgs")
    @ParameterizedTest
    public void new_givenNullArgument_throwsNullPointerException(
            Protocol protocol,
            BridgeMode mode,
            InetSocketAddress bindAddress,
            InetSocketAddress peerAddress,
            Integer wgMTU,
            Integer queueSize,
            Integer queueProcessors,
            Integer maxClientSessions
    ) {
        assertThatThrownBy(() -> new DatagramBridge(
                protocol,
                mode,
                bindAddress,
                peerAddress,
                wgMTU,
                queueSize,
                queueProcessors,
                maxClientSessions
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("must not be null");
    }

    @SneakyThrows
    @MethodSource("protocolsAndModes")
    @ParameterizedTest
    void run_givenSingleClientMessages_forwardsModifiedMessagesToPeer(Protocol protocol, BridgeMode mode) {
        int messageNumber = 100;
        Consumer<ByteBuffer> peerInDataModifier = switch (mode) {
            case NOOP, DEOBFUSCATOR -> _ -> {};
            case OBFUSCATOR -> protocol::deobfuscate;
        };
        Consumer<ByteBuffer> clientOutDataModifier = switch (mode) {
            case NOOP, OBFUSCATOR -> _ -> {};
            case DEOBFUSCATOR -> protocol::obfuscate;
        };
        int bufSize = protocol.maxDataSize(TEST_WG_MTU);

        try (ExecutorService executor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            TestDatagramChannel peer = new TestDatagramChannel(bufSize, _ -> {}, peerInDataModifier);
            executor.submit(peer);
            peer.awaitStarted(Duration.ofSeconds(TEST_AWAIT_SECONDS));
            InetSocketAddress peerAddress = peer.getAddress();

            DatagramBridge victim = new DatagramBridge(
                    protocol,
                    mode,
                    new InetSocketAddress(InetAddress.getLocalHost(), 0),
                    peerAddress,
                    TEST_WG_MTU,
                    TEST_QUEUE_SIZE,
                    TEST_QUEUE_PROCESSORS,
                    TEST_MAX_CLIENT_SESSIONS
            );
            executor.submit(victim);
            victim.awaitInitialization(Duration.ofSeconds(TEST_AWAIT_SECONDS));
            InetSocketAddress victimAddress = victim.getLocalAddress();

            TestDatagramChannel client = new TestDatagramChannel(bufSize, clientOutDataModifier, _ -> {});
            executor.submit(client);
            client.awaitStarted(Duration.ofSeconds(TEST_AWAIT_SECONDS));

            Set<String> messages = buildMessageSet(messageNumber);
            client.send(messages, victimAddress);
            peer.verifyReceivedExactly(messages, Duration.ofSeconds(TEST_AWAIT_SECONDS));
            assertThat(peer.getRemoteAddresses()).hasSize(1);
        }
    }

    @SneakyThrows
    @MethodSource("protocolsAndModes")
    @ParameterizedTest
    void run_givenConcurrentClientMessages_forwardsModifiedMessagesToPeerWithoutCollisions(
            Protocol protocol,
            BridgeMode mode
    ) {
        int messageNumber = 10;
        int clientNumber = 10;
        Consumer<ByteBuffer> peerInDataModifier = switch (mode) {
            case NOOP, DEOBFUSCATOR -> _ -> {};
            case OBFUSCATOR -> protocol::deobfuscate;
        };
        Consumer<ByteBuffer> clientOutDataModifier = switch (mode) {
            case NOOP, OBFUSCATOR -> _ -> {};
            case DEOBFUSCATOR -> protocol::obfuscate;
        };
        int bufSize = protocol.maxDataSize(TEST_WG_MTU);

        try (ExecutorService executor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            TestDatagramChannel peer = new TestDatagramChannel(bufSize, _ -> {}, peerInDataModifier);
            executor.submit(peer);
            peer.awaitStarted(Duration.ofSeconds(TEST_AWAIT_SECONDS));
            InetSocketAddress peerAddress = peer.getAddress();

            DatagramBridge victim = new DatagramBridge(
                    protocol,
                    mode,
                    new InetSocketAddress(InetAddress.getLocalHost(), 0),
                    peerAddress,
                    TEST_WG_MTU,
                    TEST_QUEUE_SIZE,
                    TEST_QUEUE_PROCESSORS,
                    TEST_MAX_CLIENT_SESSIONS
            );
            executor.submit(victim);
            victim.awaitInitialization(Duration.ofSeconds(TEST_AWAIT_SECONDS));
            InetSocketAddress victimAddress = victim.getLocalAddress();

            CyclicBarrier sendBarrier = new CyclicBarrier(clientNumber);
            Set<String> allMessages = new HashSet<>();
            for (int c = 0; c < clientNumber; c++) {
                String label = "client-%s".formatted(c);
                Set<String> messages = buildMessageSet(messageNumber, label);
                allMessages.addAll(messages);

                TestDatagramChannel client = new TestDatagramChannel(bufSize, clientOutDataModifier, _ -> {});
                executor.submit(client);
                client.awaitStarted(Duration.ofSeconds(TEST_AWAIT_SECONDS));

                executor.submit(() -> {
                    try {
                        sendBarrier.await(TEST_AWAIT_SECONDS, TimeUnit.SECONDS);
                        client.send(messages, victimAddress);
                    } catch (Exception e) {
                        log.error("Failed to send messages", e);
                    }
                });
            }
            peer.verifyReceivedExactly(allMessages, Duration.ofSeconds(TEST_AWAIT_SECONDS));
            assertThat(peer.getRemoteAddresses()).hasSize(clientNumber);
        }
    }

    @SneakyThrows
    @MethodSource("protocolsAndModes")
    @ParameterizedTest
    void run_givenPeerMessagesForSingleClient_forwardsModifiedMessagesToClient(Protocol protocol, BridgeMode mode) {
        int messageNumber = 100;
        Consumer<ByteBuffer> peerOutDataModifier = switch (mode) {
            case NOOP, DEOBFUSCATOR -> _ -> {};
            case OBFUSCATOR -> protocol::obfuscate;
        };
        Consumer<ByteBuffer> peerInDataModifier = switch (mode) {
            case NOOP, DEOBFUSCATOR -> _ -> {};
            case OBFUSCATOR -> protocol::deobfuscate;
        };
        Consumer<ByteBuffer> clientOutDataModifier = switch (mode) {
            case NOOP, OBFUSCATOR -> _ -> {};
            case DEOBFUSCATOR -> protocol::obfuscate;
        };
        Consumer<ByteBuffer> clientInDataModifier = switch (mode) {
            case NOOP, OBFUSCATOR -> _ -> {};
            case DEOBFUSCATOR -> protocol::deobfuscate;
        };
        int bufSize = protocol.maxDataSize(TEST_WG_MTU);

        try (ExecutorService executor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            TestDatagramChannel peer = new TestDatagramChannel(bufSize, peerOutDataModifier, peerInDataModifier);
            executor.submit(peer);
            peer.awaitStarted(Duration.ofSeconds(TEST_AWAIT_SECONDS));
            InetSocketAddress peerAddress = peer.getAddress();

            DatagramBridge victim = new DatagramBridge(
                    protocol,
                    mode,
                    new InetSocketAddress(InetAddress.getLocalHost(), 0),
                    peerAddress,
                    TEST_WG_MTU,
                    TEST_QUEUE_SIZE,
                    TEST_QUEUE_PROCESSORS,
                    TEST_MAX_CLIENT_SESSIONS
            );
            executor.submit(victim);
            victim.awaitInitialization(Duration.ofSeconds(TEST_AWAIT_SECONDS));
            InetSocketAddress victimAddress = victim.getLocalAddress();

            TestDatagramChannel client = new TestDatagramChannel(bufSize, clientOutDataModifier, clientInDataModifier);
            executor.submit(client);
            client.awaitStarted(Duration.ofSeconds(TEST_AWAIT_SECONDS));

            String initSessionMessage = "init";
            client.send(Set.of(initSessionMessage), victimAddress);
            peer.verifyReceivedExactly(Set.of(initSessionMessage), Duration.ofSeconds(TEST_AWAIT_SECONDS));
            Set<InetSocketAddress> peerRemoteAddresses = peer.getRemoteAddresses();
            assertThat(peerRemoteAddresses).hasSize(1);

            Set<String> messages = buildMessageSet(messageNumber);
            peer.send(messages, peerRemoteAddresses.iterator().next());
            client.verifyReceivedExactly(messages, Duration.ofSeconds(TEST_AWAIT_SECONDS));
        }
    }

    @SneakyThrows
    @MethodSource("protocolsAndModes")
    @ParameterizedTest
    void run_givenPeerMessagesForConcurrentClients_forwardsModifiedMessagesToClientsWithoutCollisions(
            Protocol protocol,
            BridgeMode mode
    ) {
        int messageNumber = 10;
        int clientNumber = 10;
        Consumer<ByteBuffer> peerOutDataModifier = switch (mode) {
            case NOOP, DEOBFUSCATOR -> _ -> {};
            case OBFUSCATOR -> protocol::obfuscate;
        };
        Consumer<ByteBuffer> peerInDataModifier = switch (mode) {
            case NOOP, DEOBFUSCATOR -> _ -> {};
            case OBFUSCATOR -> protocol::deobfuscate;
        };
        Consumer<ByteBuffer> clientOutDataModifier = switch (mode) {
            case NOOP, OBFUSCATOR -> _ -> {};
            case DEOBFUSCATOR -> protocol::obfuscate;
        };
        Consumer<ByteBuffer> clientInDataModifier = switch (mode) {
            case NOOP, OBFUSCATOR -> _ -> {};
            case DEOBFUSCATOR -> protocol::deobfuscate;
        };
        int bufSize = protocol.maxDataSize(TEST_WG_MTU);

        try (ExecutorService executor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            TestDatagramChannel peer = new TestDatagramChannel(bufSize, peerOutDataModifier, peerInDataModifier);
            executor.submit(peer);
            peer.awaitStarted(Duration.ofSeconds(TEST_AWAIT_SECONDS));
            InetSocketAddress peerAddress = peer.getAddress();

            DatagramBridge victim = new DatagramBridge(
                    protocol,
                    mode,
                    new InetSocketAddress(InetAddress.getLocalHost(), 0),
                    peerAddress,
                    TEST_WG_MTU,
                    TEST_QUEUE_SIZE,
                    TEST_QUEUE_PROCESSORS,
                    TEST_MAX_CLIENT_SESSIONS
            );
            executor.submit(victim);
            victim.awaitInitialization(Duration.ofSeconds(TEST_AWAIT_SECONDS));
            InetSocketAddress victimAddress = victim.getLocalAddress();

            Map<String, TestDatagramChannel> labelClientMap = new HashMap<>();
            for (int c = 0; c < clientNumber; c++) {
                String initSessionMessage = "init-%s".formatted(c);
                TestDatagramChannel client = new TestDatagramChannel(bufSize, clientOutDataModifier, clientInDataModifier);
                executor.submit(client);
                client.awaitStarted(Duration.ofSeconds(TEST_AWAIT_SECONDS));

                client.send(Set.of(initSessionMessage), victimAddress);
                labelClientMap.put(initSessionMessage, client);
            }
            peer.verifyReceivedExactly(labelClientMap.keySet(), Duration.ofSeconds(TEST_AWAIT_SECONDS));
            assertThat(peer.getRemoteAddresses()).hasSize(clientNumber);
            Map<InetSocketAddress, List<String>> sessionAddressMap = peer.getReceivedMessages();

            Map<String, Set<String>> labelMessageMap = new HashMap<>();
            CyclicBarrier sendBarrier = new CyclicBarrier(clientNumber);
            for (Map.Entry<InetSocketAddress, List<String>> sessionAddressEntry : sessionAddressMap.entrySet()) {
                InetSocketAddress sessionAddress = sessionAddressEntry.getKey();
                String label = sessionAddressEntry.getValue().getFirst();
                Set<String> messages = buildMessageSet(messageNumber, label);
                executor.submit(() -> {
                    try {
                        sendBarrier.await(TEST_AWAIT_SECONDS, TimeUnit.SECONDS);
                        peer.send(messages, sessionAddress);
                    } catch (Exception e) {
                        log.error("Failed to send messages", e);
                    }
                });
                labelMessageMap.put(label, messages);
            }
            for (Map.Entry<String, TestDatagramChannel> labelClientEntry : labelClientMap.entrySet()) {
                String label = labelClientEntry.getKey();
                TestDatagramChannel client = labelClientEntry.getValue();
                Set<String> messages = labelMessageMap.get(label);

                client.verifyReceivedExactly(messages, Duration.ofSeconds(TEST_AWAIT_SECONDS));
                assertThat(client.getRemoteAddresses()).hasSize(1);
            }
        }
    }

    @SneakyThrows
    @Test
    public void run_givenInterruption_finishesGracefully() {
        Protocol protocol = new NoopProtocol();
        DatagramBridge victim = new DatagramBridge(
                protocol,
                BridgeMode.NOOP,
                new InetSocketAddress(InetAddress.getLocalHost(), 0),
                new InetSocketAddress(InetAddress.getLocalHost(), 51820),
                TEST_WG_MTU,
                TEST_QUEUE_SIZE,
                TEST_QUEUE_PROCESSORS,
                TEST_MAX_CLIENT_SESSIONS
        );

        Thread victimThread = new Thread(victim, "victim");
        victimThread.start();
        victim.awaitInitialization(Duration.ofSeconds(TEST_AWAIT_SECONDS));

        log.info("Sending interruption signal");
        victimThread.interrupt();
        assertThat(victimThread.join(Duration.ofSeconds(TEST_AWAIT_SECONDS))).isTrue();
    }

    @SneakyThrows
    private static Stream<Arguments> newDropoutArgs() {
        Protocol protocol = new NoopProtocol();
        return dropoutArgStream(
                named("noop", protocol),
                named("noop", BridgeMode.NOOP),
                named("localhost:0", new InetSocketAddress(InetAddress.getLocalHost(), 0)),
                named("localhost:51820", new InetSocketAddress(InetAddress.getLocalHost(), 51820)),
                named("1500", 1500),
                named("1000", 1000),
                named("4", 4),
                named("100", 100)
        );
    }

    private static Stream<Arguments> protocolsAndModes() {
        Protocol[] protocols = new Protocol[]{
                new NoopProtocol(), new InvertingProtocol(), new PaddingProtocol(), new ShiftingNoopProtocol()
        };
        BridgeMode[] bridgeModes = BridgeMode.values();

        List<Arguments> argumentList = new LinkedList<>();
        Arrays.stream(bridgeModes).forEach(bridgeMode ->
                Arrays.stream(protocols).forEach(protocol ->
                        argumentList.add(Arguments.of(protocol, bridgeMode))));
        return argumentList.stream();
    }
}
