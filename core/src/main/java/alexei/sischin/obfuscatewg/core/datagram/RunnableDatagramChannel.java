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

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Wraps {@link DatagramChannel} into a {@link Runnable}, allowing to receive datagrams asynchronously and to send
 * datagrams synchronously.
 */
@Slf4j
public final class RunnableDatagramChannel implements Runnable {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final CountDownLatch initLatch = new CountDownLatch(1);

    private final InetSocketAddress bindAddress;
    private final BiConsumer<ByteBuffer, InetSocketAddress> receiveCallback;
    private final int bufSize;

    @Nullable
    private DatagramChannel channel;

    @Nullable
    private InetSocketAddress remoteAddress;

    /**
     * @param bindAddress the address to bind the channel to.
     * @param receiveCallback callback for incoming datagrams.
     * @param bufSize buffer for incoming datagrams.
     */
    public RunnableDatagramChannel(
            InetSocketAddress bindAddress,
            BiConsumer<ByteBuffer, InetSocketAddress> receiveCallback,
            Integer bufSize
    ) {
        Objects.requireNonNull(bindAddress, "bindAddress must not be null");
        Objects.requireNonNull(receiveCallback, "receiveCallback must not be null");
        Objects.requireNonNull(bufSize, "bufSize must not be null");

        this.bindAddress = bindAddress;
        this.receiveCallback = receiveCallback;
        this.bufSize = bufSize;
    }

    /**
     * Sets the connection address to the underlying channel.
     *
     * @param remoteAddress the remote address to which the channel is to be connected.
     * @see DatagramChannel#connect(SocketAddress)
     */
    public void setConnectionAddress(@Nullable InetSocketAddress remoteAddress) {
        if (isInitialized()) {
            throw new IllegalStateException("Channel is initialized");
        }
        this.remoteAddress = remoteAddress;
    }

    /**
     * Waits for the channel thread to be ready to receive data.
     *
     * @param timeout max time to wait.
     * @throws InterruptedException if the calling thread was interrupted.
     * @throws TimeoutException     if timeout exceeded.
     */
    public void awaitInitialization(Duration timeout) throws InterruptedException, TimeoutException {
        if (!initLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new TimeoutException("Initialization awaiting timeout");
        }
    }

    /**
     * @return underlying channel listen address.
     * @throws IOException if an I/O error occurred.
     * @throws IllegalStateException if channel is not initialized or bound.
     * @see DatagramChannel#getLocalAddress()
     */
    public InetSocketAddress getLocalAddress() throws IOException {
        if (this.channel == null) {
            throw new IllegalStateException("Channel is not initialized");
        }
        SocketAddress address = this.channel.getLocalAddress();
        if (address == null) {
            throw new IllegalStateException("Channel is not bound");
        }
        return (InetSocketAddress) address;
    }

    /**
     * Sends the data to the connected address.
     *
     * @param data data to be sent.
     * @throws IOException if an I/O error occurred.
     * @throws IllegalStateException if channel is not connected or initialized.
     * @see RunnableDatagramChannel#send(ByteBuffer, InetSocketAddress)
     * @see RunnableDatagramChannel#setConnectionAddress(InetSocketAddress)
     */
    public void send(ByteBuffer data) throws IOException {
        if (this.remoteAddress == null) {
            throw new IllegalStateException(
                    "Channel is not connected. Consider specifying address or connecting first");
        }
        send(data, this.remoteAddress);
    }

    /**
     * Sends the data to the address.
     *
     * @param data data to be sent.
     * @param address target address.
     * @throws IOException if an I/O error occurred.
     * @throws IllegalStateException if channel is not initialized.
     * @see DatagramChannel#send(ByteBuffer, SocketAddress)
     */
    public void send(ByteBuffer data, InetSocketAddress address) throws IOException {
        if (!isInitialized()) {
            throw new IllegalStateException("Channel is not initialized");
        }
        if (log.isTraceEnabled()) {
            log.trace("Sending datagram ({} bytes) to {}", data.remaining(), address);
        }
        this.channel.send(data, address);
    }

    /**
     * Listens to incoming datagrams and invokes the callback.
     */
    @Override
    public void run() {
        if (!this.started.compareAndSet(false, true)) {
            throw new IllegalStateException("Already started");
        }
        try {
            try {
                initialize();
                log.debug("Initialization complete");
            } catch (Exception e) {
                log.error("Failed to initialize");
                return;
            }
            try {
                runMainLoop();
            } catch (ClosedByInterruptException | InterruptedException e) {
                log.debug("Stopping because of interruption");
            } catch (ClosedChannelException e) {
                log.info("Stopping because channel is closed");
            } catch (Exception e) {
                log.error("Stopping because an unexpected exception occurred", e);
            }
        } finally {
            try {
                cleanup();
                log.debug("Cleanup complete");
            } catch (IOException e) {
                log.error("Failed to clean up", e);
            }
        }
    }

    private void runMainLoop() throws ClosedChannelException, InterruptedException {
        ByteBuffer buffer = ByteBuffer.allocate(this.bufSize);
        while (verifyStatus()) {
            try {
                InetSocketAddress senderAddress = (InetSocketAddress) this.channel.receive(buffer.clear());
                if (log.isTraceEnabled()) {
                    log.trace("Received datagram ({} bytes) from {}", buffer.position(), senderAddress);
                }
                try {
                    this.receiveCallback.accept(buffer.flip(), senderAddress);
                } catch (Exception e) {
                    log.error("Unhandled error was thrown by receive callback. Continuing to listen", e);
                }
            } catch (ClosedChannelException e) {
                throw e;
            } catch (IOException e) {
                log.warn("Failed to receive datagram. Continuing to listen.", e);
            }
        }
    }

    private boolean verifyStatus() throws InterruptedException, ClosedChannelException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (!this.channel.isOpen()) {
            throw new ClosedChannelException();
        }
        return true;
    }

    private void initialize() throws IOException {
        this.channel = DatagramChannel.open();
        this.channel.bind(this.bindAddress);
        log.info("Listening at {}", this.channel.getLocalAddress());
        if (this.remoteAddress != null) {
            log.debug("Connecting to address {}", this.remoteAddress);
            this.channel.connect(this.remoteAddress);
        }
        this.initLatch.countDown();
    }

    private void cleanup() throws IOException {
        if (this.channel.isOpen()) {
            log.debug("Closing channel");
            this.channel.close();
        } else {
            log.trace("Skipping closing channel because it is not open");
        }
    }

    private boolean isInitialized() {
        return this.initLatch.getCount() == 0;
    }
}
