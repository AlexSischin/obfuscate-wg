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

import alexei.sischin.obfuscatewg.core.concurrent.ConcurrentResourceMap;
import alexei.sischin.obfuscatewg.core.concurrent.UncaughtErrorLogging;
import alexei.sischin.obfuscatewg.protocol.api.Protocol;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Slf4j
public class DatagramBridge implements Runnable {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final CountDownLatch initLatch = new CountDownLatch(1);
    private final AtomicInteger queueProcessWorkerCounter = new AtomicInteger();
    private final ReentrantLock queueProcessWorkerLock = new ReentrantLock();
    private final Condition queueProcessWorkerCountChangedCondition = queueProcessWorkerLock.newCondition();

    private final Consumer<ByteBuffer> clientDatagramModifier;
    private final Consumer<ByteBuffer> peerDatagramModifier;
    private final InetSocketAddress address;
    private final InetSocketAddress peerAddress;
    private final Integer bufSize;
    private final Integer queueProcessorNumber;
    private final Integer queueSize;

    private final CountDownLatch processorWorkerInitLatch;
    private final RunnableDatagramChannel clientChannel;
    private final ConcurrentResourceMap<InetSocketAddress, PeerSession> peerSessionMap;

    @Nullable
    private ExecutorService mainTaskExecutor;
    @Nullable
    private ExecutorService peerChannelExecutor;
    @Nullable
    private ExecutorService queueProcessorExecutor;
    @Nullable
    private BlockingQueue<ClientDatagram> datagramQueue;
    @Nullable
    private Queue<ByteBuffer> bufferPool;

    /**
     * @param protocol          implementation.
     * @param mode              bridge mode.
     * @param address           bind address for incoming datagrams.
     * @param peerAddress       the address to proxy the datagrams to. Typically, it is a deobfuscator or a WireGuard.
     * @param wgMTU             WireGuard MTU setting.
     * @param queueSize         buffer pool size for incoming datagrams.
     * @param queueProcessors   number of processors for incoming datagrams.
     * @param maxClientSessions maximum number of concurrent client sessions.
     */
    public DatagramBridge(
            Protocol protocol,
            BridgeMode mode,
            InetSocketAddress address,
            InetSocketAddress peerAddress,
            Integer wgMTU,
            Integer queueSize,
            Integer queueProcessors,
            Integer maxClientSessions
    ) {
        Objects.requireNonNull(protocol, "protocol must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(address, "address must not be null");
        Objects.requireNonNull(peerAddress, "peerAddress must not be null");
        Objects.requireNonNull(wgMTU, "wgMTU must not be null");
        Objects.requireNonNull(queueSize, "queueSize must not be null");
        Objects.requireNonNull(queueProcessors, "queueProcessors must not be null");
        Objects.requireNonNull(maxClientSessions, "maxClientSessions must not be null");

        this.address = address;
        this.peerAddress = peerAddress;
        this.bufSize = protocol.maxDataSize(wgMTU);
        this.queueSize = queueSize;
        this.queueProcessorNumber = queueProcessors;

        this.clientDatagramModifier = switch (mode) {
            case OBFUSCATOR -> protocol::obfuscate;
            case DEOBFUSCATOR -> protocol::deobfuscate;
            case NOOP -> _ -> {};
        };
        this.peerDatagramModifier = switch (mode) {
            case OBFUSCATOR -> protocol::deobfuscate;
            case DEOBFUSCATOR -> protocol::obfuscate;
            case NOOP -> _ -> {};
        };
        this.processorWorkerInitLatch = new CountDownLatch(queueProcessors);
        this.clientChannel = buildClientChannel();
        this.peerSessionMap = new ConcurrentResourceMap<>(maxClientSessions);
    }

    /**
     * Receives incoming datagrams from clients, modifies them and forwards to the peer,
     * and does the same in the opposite way.
     * <p>
     *     Uses a single {@link RunnableDatagramChannel} instance for incoming packets from clients
     *     and N {@link RunnableDatagramChannel} instances for incoming packets from the peer,
     *     where N is the number of clients.
     * </p>
     *
     * @see RunnableDatagramChannel
     */
    @Override
    public void run() {
        if (!this.started.compareAndSet(false, true)) {
            throw new IllegalStateException("Already started");
        }
        initialize();
        try {
            CompletionService<Boolean> completionService = new ExecutorCompletionService<>(this.mainTaskExecutor);
            completionService.submit(this::runQueueProcessorMaintenance, true);
            completionService.submit(this.clientChannel, true);
            completionService.take();
            log.error("Stopping due to unexpected error during task execution");
        } catch (InterruptedException e) {
            log.debug("Stopping due to interruption");
        } catch (Exception e) {
            log.error("Stopping due to unexpected error", e);
        } finally {
            try {
                cleanup();
            } catch (InterruptedException e) {
                log.error("Interrupted during cleanup");
            } catch (Exception e) {
                log.error("Unexpected exception occurred during cleanup");
            }
        }
    }

    /**
     * Awaits for all the threads to be ready to accept connections and process traffic.
     *
     * @param timeout max time to wait.
     * @throws InterruptedException if the calling thread was interrupted.
     * @throws TimeoutException if timeout exceeded.
     */
    public void awaitInitialization(Duration timeout) throws InterruptedException, TimeoutException {
        if (!initLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new TimeoutException("Initialization awaiting timeout");
        }
        if (!processorWorkerInitLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new TimeoutException("Initialization awaiting timeout");
        }
        this.clientChannel.awaitInitialization(timeout);
    }

    /**
     * @return listen address for incoming client connections.
     * @throws IOException if I/O error occurs.
     * @see RunnableDatagramChannel#getLocalAddress()
     */
    public InetSocketAddress getLocalAddress() throws IOException {
        return this.clientChannel.getLocalAddress();
    }

    private void runQueueProcessorMaintenance() {
        try {
            while (checkStatus()) {
                log.debug("Checking worker count");
                this.queueProcessWorkerLock.lock();
                try {
                    if (this.queueProcessWorkerCounter.get() < this.queueProcessorNumber) {
                        this.queueProcessorExecutor.submit(this::runQueueProcessorWorker);
                    }
                    this.queueProcessWorkerCountChangedCondition.await();
                } finally {
                    queueProcessWorkerLock.unlock();
                }
            }
        } catch (InterruptedException e) {
            log.debug("Stopping all queue workers because of interruption");
        }
    }

    private void runQueueProcessorWorker() {
        int workerIndex = this.queueProcessWorkerCounter.get() + 1;
        log.debug("Started worker {}/{}", workerIndex, this.queueProcessorNumber);
        this.queueProcessWorkerLock.lock();
        try {
            this.queueProcessWorkerCounter.incrementAndGet();
            this.queueProcessWorkerCountChangedCondition.signal();
            this.queueProcessWorkerLock.unlock();
            runQueueProcessorWorkerLoop();
        } catch (InterruptedException e) {
            log.debug("Stopping worker {} because of interruption", workerIndex);
        } catch (Exception e) {
            log.error("Unexpected error occurred in worker", e);
        } finally {
            if (!this.queueProcessWorkerLock.isHeldByCurrentThread()) {
                this.queueProcessWorkerLock.lock();
            }
            try {
                this.queueProcessWorkerCounter.decrementAndGet();
                this.queueProcessWorkerCountChangedCondition.signal();
            } finally {
                this.queueProcessWorkerLock.unlock();
            }
            log.debug("Stopped worker {}", workerIndex);
        }
    }

    private void runQueueProcessorWorkerLoop() throws InterruptedException {
        this.processorWorkerInitLatch.countDown();
        while (checkStatus()) {
            ClientDatagram clientDatagram = this.datagramQueue.take();
            ByteBuffer buffer = clientDatagram.datagram();
            InetSocketAddress clientAddress = clientDatagram.clientAddress();
            try {
                ConcurrentResourceMap.ComputeIfAbsentResult<PeerSession> peerSessionResult
                        = this.peerSessionMap.computeIfAbsent(clientAddress, _ -> {

                    log.debug("Creating peer session for client {}", clientAddress);
                    RunnableDatagramChannel newPeerChannel = buildPeerChannel(clientAddress);
                    Future<?> task = this.peerChannelExecutor.submit(newPeerChannel);
                    return new PeerSession(clientAddress, newPeerChannel, task);
                });
                PeerSession eldestSession = peerSessionResult.eldestValue();
                if (eldestSession != null) {
                    log.debug("Closing session with client {}", eldestSession.clientAddress());
                    if (!eldestSession.task().cancel(true)) {
                        log.warn("Failed to close session with client {}", eldestSession.clientAddress());
                    }
                }

                RunnableDatagramChannel peerChannel = peerSessionResult.existingOrNewValue().peerChannel();
                try {
                    this.clientDatagramModifier.accept(buffer);
                } catch (Exception e) {
                    log.error("Protocol error. Dropping datagram from client", e);
                    continue;
                }
                try {
                    peerChannel.awaitInitialization(Duration.ofSeconds(5));
                    peerChannel.send(buffer);
                } catch (InterruptedException e) {
                    throw e;
                } catch (TimeoutException e) {
                    log.error("Failed to await peer channel initialization");
                    ConcurrentResourceMap.RemoveResult<PeerSession> removeResult
                            = this.peerSessionMap.remove(clientAddress);
                    PeerSession removedSession = removeResult.removedValue();
                    if (removedSession != null) {
                        log.info("Closing broken peer channel");
                        removedSession.task().cancel(true);
                    }
                } catch (Exception e) {
                    log.error("Failed to send datagram to peer", e);
                }
            } finally {
                this.bufferPool.offer(buffer);
            }
        }
    }

    private boolean checkStatus() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return true;
    }

    private RunnableDatagramChannel buildClientChannel() {
        return new RunnableDatagramChannel(this.address, this::handleClientDatagram, this.bufSize);
    }

    private void handleClientDatagram(ByteBuffer datagram, InetSocketAddress clientAddress) {
        ByteBuffer buffer = this.bufferPool.poll();
        if (buffer == null) {
            log.warn("Packed dropped because cannot acquire buffer");
            return;
        }
        buffer.clear().put(datagram).flip();
        ClientDatagram clientDatagram = new ClientDatagram(buffer, clientAddress);
        if (!this.datagramQueue.offer(clientDatagram)) {
            this.bufferPool.offer(buffer);
            log.warn("Packed dropped because queue is full");
        }
    }

    private RunnableDatagramChannel buildPeerChannel(
            InetSocketAddress clientAddress
    ) {
        RunnableDatagramChannel peerChannel = new RunnableDatagramChannel(
                new InetSocketAddress((InetAddress) null, 0),
                (datagram, _) -> handlePeerDatagram(datagram, clientAddress),
                this.bufSize
        );
        peerChannel.setConnectionAddress(this.peerAddress);
        return peerChannel;
    }

    private void handlePeerDatagram(ByteBuffer datagram, InetSocketAddress clientAddress) {
        try {
            this.peerDatagramModifier.accept(datagram);
        } catch (Exception e) {
            log.error("Protocol error. Dropping datagram from peer", e);
            return;
        }
        try {
            this.clientChannel.send(datagram, clientAddress);
        } catch (IOException e) {
            log.error("Failed to send datagram to client", e);
        }
    }

    private void initialize() {
        this.mainTaskExecutor = UncaughtErrorLogging.wrap(Executors.newFixedThreadPool(2));
        this.peerChannelExecutor = UncaughtErrorLogging.wrap(Executors.newThreadPerTaskExecutor(Executors.defaultThreadFactory()));
        this.queueProcessorExecutor = UncaughtErrorLogging.wrap(Executors.newFixedThreadPool(this.queueProcessorNumber));
        this.datagramQueue = new ArrayBlockingQueue<>(this.queueSize);
        this.bufferPool = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < this.queueSize; i++) {
            ByteBuffer buffer = ByteBuffer.allocate(this.bufSize);
            this.bufferPool.add(buffer);
        }
        this.initLatch.countDown();
    }

    private void cleanup() throws InterruptedException {
        shutdownNowExecutorService(this.mainTaskExecutor, "main task");
        shutdownNowExecutorService(this.peerChannelExecutor, "peer channel");
        shutdownNowExecutorService(this.queueProcessorExecutor, "queue processor");
        awaitTerminationOfExecutorService(this.mainTaskExecutor, "main task");
        awaitTerminationOfExecutorService(this.peerChannelExecutor, "peer channel");
        awaitTerminationOfExecutorService(this.queueProcessorExecutor, "queue processor");
    }

    private void shutdownNowExecutorService(
            @Nullable ExecutorService target,
            String label
    ) {
        if ((target != null) && (!target.isTerminated())) {
            log.debug("Shutting down {} executor service", label);
            target.shutdownNow();
        }
    }

    private void awaitTerminationOfExecutorService(
            @Nullable ExecutorService target,
            String label
    ) throws InterruptedException {
        if ((target != null)
                && (!target.awaitTermination(5, TimeUnit.SECONDS))) {
            log.warn("Timeout during awaiting {} executor service termination", label);
        }
    }

    private record PeerSession(
            InetSocketAddress clientAddress,
            RunnableDatagramChannel peerChannel,
            Future<?> task
    ) {}

    private record ClientDatagram(
            ByteBuffer datagram,
            InetSocketAddress clientAddress
    ) {}
}
