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

package alexei.sischin.obfuscatewg.core.util.bridge;

import alexei.sischin.obfuscatewg.core.datagram.BridgeMode;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static java.util.function.Predicate.not;

public record BridgeConfig(
        URL protocolUrl,
        Optional<String> protocolArgs,
        BridgeMode mode,
        Integer wgMTU,
        InetSocketAddress address,
        InetSocketAddress peerAddress,
        Integer queueSize,
        Integer queueProcessors,
        Integer maxSessions
) {
    public BridgeConfig {
        Objects.requireNonNull(protocolUrl, "Protocol URL must be not null");

        Objects.requireNonNull(protocolArgs, "Protocol args must be not null");

        Objects.requireNonNull(mode, "Mode must be not null");

        Objects.requireNonNull(wgMTU, "WireGuard MTU must be not null");
        if (wgMTU < 1 || wgMTU > 65535) {
            throw new IllegalArgumentException("WireGuard MTU must be within [1, 65535]");
        }

        Objects.requireNonNull(address, "Address must be not null");
        if (address.getPort() < 1 || address.getPort() > 65535) {
            throw new IllegalArgumentException("Address port must be within [1, 65535]");
        }

        Objects.requireNonNull(peerAddress, "Peer address must be not null");
        if (peerAddress.getPort() < 1 || peerAddress.getPort() > 65535) {
            throw new IllegalArgumentException("Peer port must be within [1, 65535]");
        }

        Objects.requireNonNull(queueSize, "Queue size must be not null");
        if (queueSize < 0) {
            throw new IllegalArgumentException("Queue size must be within [1, MAX_VALUE]");
        }

        Objects.requireNonNull(queueProcessors, "Queue processor number must be not null");
        if (queueProcessors < 0) {
            throw new IllegalArgumentException("Queue processor number must be within [0, MAX_VALUE]");
        }

        Objects.requireNonNull(maxSessions, "Max sessions must be not null");
        if (maxSessions < 0) {
            throw new IllegalArgumentException("Max sessions must be within [1, MAX_VALUE]");
        }
    }

    public static BridgeConfig fromRawStrings(
            String protocolUrl,
            String protocolArgs,
            String mode,
            String wgMTU,
            String ip,
            String port,
            String peerIp,
            String peerPort,
            String queueSize,
            String queueProcessors,
            String maxSessions
    ) {
        Objects.requireNonNull(protocolUrl, "Protocol URL must be not null");
        Objects.requireNonNull(mode, "Mode must be not null");
        Objects.requireNonNull(wgMTU, "WireGuard MTU must be not null");
        Objects.requireNonNull(ip, "IP must be not null");
        Objects.requireNonNull(port, "Port must be not null");
        Objects.requireNonNull(peerIp, "Peer IP must be not null");
        Objects.requireNonNull(peerPort, "Peer port must be not null");
        Objects.requireNonNull(queueSize, "Queue size must be not null");
        Objects.requireNonNull(queueProcessors, "Queue processor number must be not null");
        Objects.requireNonNull(maxSessions, "Max sessions must be not null");

        return new BridgeConfig(
                parseUrl(protocolUrl),
                parseOptional(protocolArgs, a -> a),
                parseMode(mode),
                parsePositiveInt(wgMTU),
                parseInetSocketAddress(ip, port),
                parseInetSocketAddress(peerIp, peerPort),
                parsePositiveInt(queueSize),
                parseNonNegativeInt(queueProcessors),
                parsePositiveInt(maxSessions)
        );
    }

    @Override
    public String toString() {
        return "BridgeConfig{" +
                "protocolUrl=" + protocolUrl +
                ", protocolArgs=" + protocolArgs.map(String::length).map("*"::repeat) +
                ", mode=" + mode +
                ", wgMTU=" + wgMTU +
                ", address=" + address +
                ", peerAddress=" + peerAddress +
                ", queueSize=" + queueSize +
                ", queueProcessors=" + queueProcessors +
                ", maxSessions=" + maxSessions +
                '}';
    }

    private static <T> Optional<T> parseOptional(String s, Function<String, T> mapper) {
        return Optional.ofNullable(s)
                .filter(not(String::isBlank))
                .map(mapper);
    }

    private static URL parseUrl(String s) {
        if (s == null) {
            return null;
        }
        try {
            return URI.create(s).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL: \"%s\"".formatted(s), e);
        }
    }

    private static BridgeMode parseMode(String s) {
        if (s == null) {
            return null;
        }
        try {
            return BridgeMode.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid bridge mode: \"%s\"".formatted(s), e);
        }
    }

    private static InetSocketAddress parseInetSocketAddress(String ipS, String portS) {
        InetAddress ip = parseIp(ipS);
        Integer port = parsePort(portS);
        if (ip == null && port == null) {
            return null;
        }
        return new InetSocketAddress(ip, port);
    }

    private static InetAddress parseIp(String s) {
        if (s == null) {
            return null;
        }
        try {
            return InetAddress.ofLiteral(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid ip address: \"%s\"".formatted(s), e);
        }
    }

    private static Integer parsePort(String s) {
        if (s == null) {
            return null;
        }
        try {
            int port = Integer.parseInt(s);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port out of range: %s".formatted(port));
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port: \"%s\"".formatted(s), e);
        }
    }

    private static Integer parsePositiveInt(String s) {
        if (s == null) {
            return null;
        }
        try {
            int i = Integer.parseInt(s);
            if (i < 1) {
                throw new IllegalArgumentException("Positive integer out of range: %s".formatted(i));
            }
            return i;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer: \"%s\"".formatted(s), e);
        }
    }

    private static Integer parseNonNegativeInt(String s) {
        if (s == null) {
            return null;
        }
        try {
            int i = Integer.parseInt(s);
            if (i < 0) {
                throw new IllegalArgumentException("Non-negative integer out of range: %s".formatted(i));
            }
            return i;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer: \"%s\"".formatted(s), e);
        }
    }
}
