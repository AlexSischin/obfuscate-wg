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

package alexei.sischin.obfuscatewg.cli.config;

import alexei.sischin.obfuscatewg.core.util.config.ConfigProperty;
import lombok.SneakyThrows;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.UnrecognizedOptionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliApiTest extends ConfigSourceTest<CliApi> {

    @BeforeEach
    void beforeEach() {
        super.victim = new CliApi();
    }

    @MethodSource("invalidArgsArgs")
    @ParameterizedTest
    void initialize_givenInvalidArgs_throwsException(String argsStr, Class<? extends Exception> exceptionClass) {
        String[] args = argsStr.split(" ");
        assertThatThrownBy(() -> super.victim.initialize(args))
                .isInstanceOf(exceptionClass);
    }

    @SneakyThrows
    @Test
    void isHelp_givenNoArgs_returnsFalse() {
        super.victim.initialize(new String[0]);
        assertThat(victim.isHelp()).isFalse();
    }

    @SneakyThrows
    @ValueSource(strings = {"--help", "-i 127.0.0.1 --help"})
    @ParameterizedTest
    void isHelp_givenHelp_returnsTrue(String argsStr) {
        String[] args = argsStr.split(" ");
        super.victim.initialize(args);
        assertThat(victim.isHelp()).isTrue();
    }

    @SneakyThrows
    @Test
    void isHelp_givenOtherArgs_returnsFalse() {
        String[] args = "-i 127.0.0.1".split(" ");
        super.victim.initialize(args);
        assertThat(victim.isHelp()).isFalse();
    }

    @SneakyThrows
    @MethodSource("validArgsArgs")
    @ParameterizedTest
    void get_givenValues_returnsThem(String argsStr, Map<ConfigProperty, String> expectedValues) {
        String[] args = argsStr.split(" ");
        super.victim.initialize(args);
        super.get_givenValues_returnsThem(expectedValues);
    }

    @SneakyThrows
    @MethodSource("validArgsArgs")
    @ParameterizedTest
    void get_givenValues_returnsEmptyForOthers(String argsStr, Map<ConfigProperty, String> expectedValues) {
        String[] args = argsStr.split(" ");
        super.victim.initialize(args);
        super.get_givenValues_returnsEmptyForOthers(expectedValues);
    }

    @SneakyThrows
    @EnumSource(ConfigProperty.class)
    @ParameterizedTest
    @Override
    protected void get_givenNoValues_returnsEmpty(ConfigProperty propKey) {
        super.victim.initialize(new String[0]);
        super.get_givenNoValues_returnsEmpty(propKey);
    }

    @SneakyThrows
    @Test
    void printHelp_givenAnyCase_printsReadableHelp() {
        super.victim.initialize(new String[0]);

        PrintStream stdout = System.out;
        ByteArrayOutputStream capturingBuffer = new ByteArrayOutputStream(10240); // 10 KB
        PrintStream capturingStream = new PrintStream(capturingBuffer);
        System.setOut(capturingStream);
        try {
            super.victim.printHelp();
        } finally {
            System.setOut(stdout);
        }
        String expectedHelpMessage = """
                usage:  java -jar <JAR_FILE> [-a <PROTOCOL_ARGS>] [-h] [-i <IP>] [-I <PEER_IP>] [-l <LOG_LEVEL>] [-m <MODE>] [-M <WG_MTU>] [-p
                    <PORT>] [-P <PEER_PORT>] [-q <QUEUE_SIZE>] [-Q <QUEUE_PROCESSORS>] [-s <MAX_SESSIONS>] [-u <PROTOCOL_URL>]
                
                 Run a WireGuard proxy that obfuscates protocol fingerprint. It can be run in either obfuscation or deobfuscation mode.
                
                                  Options                      Since                                   Description                              \s
                 -l, --log-level <LOG_LEVEL>                    --       Logback log level name. Possible values: OFF, ERROR, WARN, INFO, DEBUG,\s
                                                                          TRACE                                                                 \s
                 -u, --protocol-url <PROTOCOL_URL>              --       Protocol implementation URL.                                           \s
                 -a, --protocol-args <PROTOCOL_ARGS>            --       Protocol arguments. Typically, a symmetric key.                        \s
                 -m, --mode <MODE>                              --       Bridge mode. Possible values: OBFUSCATOR, DEOBFUSCATOR, NOOP           \s
                 -M, --wg-mtu <WG_MTU>                          --       WireGuard MTU. Must be equal to the proxied WG MTU setting.            \s
                 -i, --ip <IP>                                  --       Listen IP address.                                                     \s
                 -p, --port <PORT>                              --       Listen port number.                                                    \s
                 -I, --peer-ip <PEER_IP>                        --       Peer IP address.                                                       \s
                 -P, --peer-port <PEER_PORT>                    --       Peer port number.                                                      \s
                 -q, --queue-size <QUEUE_SIZE>                  --       Client datagram queue size.                                            \s
                 -Q, --queue-processors <QUEUE_PROCESSORS>      --       Number of client datagram processors. 0 means equal to the processor   \s
                                                                          number.                                                               \s
                 -s, --max-sessions <MAX_SESSIONS>              --       Maximum number of concurrent sessions.                                 \s
                 -h, --help                                     --       Display help information.                                              \s
                
                 Parameters could be also specified via JVM params or environment variables.
                    Defaults:
                    - LOG_LEVEL = INFO
                    - PROTOCOL_ARGS =
                    - MODE = NOOP
                    - WG_MTU = 1420
                    - IP = 0.0.0.0
                    - PORT = 51820
                    - PEER_PORT = 51820
                    - QUEUE_SIZE = 73000
                    - QUEUE_PROCESSORS = 0
                    - MAX_SESSIONS = 256
                    Required params (could be specified either way):
                    - PROTOCOL_URL
                    - PEER_IP
                """;
        String actualHelpMessage = capturingBuffer.toString(StandardCharsets.UTF_8);
        assertThat(actualHelpMessage).isEqualToIgnoringWhitespace(expectedHelpMessage);
    }

    Stream<Arguments> invalidArgsArgs() {
        return Stream.of(
                Arguments.of("-l", MissingArgumentException.class),
                Arguments.of("--log-level", MissingArgumentException.class),
                Arguments.of("-u", MissingArgumentException.class),
                Arguments.of("--protocol-url", MissingArgumentException.class),
                Arguments.of("-a", MissingArgumentException.class),
                Arguments.of("--protocol-args", MissingArgumentException.class),
                Arguments.of("-m", MissingArgumentException.class),
                Arguments.of("--mode", MissingArgumentException.class),
                Arguments.of("-M", MissingArgumentException.class),
                Arguments.of("--wg-mtu", MissingArgumentException.class),
                Arguments.of("-i", MissingArgumentException.class),
                Arguments.of("--ip", MissingArgumentException.class),
                Arguments.of("-p", MissingArgumentException.class),
                Arguments.of("--port", MissingArgumentException.class),
                Arguments.of("-I", MissingArgumentException.class),
                Arguments.of("--peer-ip", MissingArgumentException.class),
                Arguments.of("-P", MissingArgumentException.class),
                Arguments.of("--peer-port", MissingArgumentException.class),
                Arguments.of("-q", MissingArgumentException.class),
                Arguments.of("--queue-size", MissingArgumentException.class),
                Arguments.of("-Q", MissingArgumentException.class),
                Arguments.of("--queue-processors", MissingArgumentException.class),
                Arguments.of("-s", MissingArgumentException.class),
                Arguments.of("--max-sessions", MissingArgumentException.class),
                Arguments.of("--unrecognized-arg", UnrecognizedOptionException.class),
                Arguments.of("--unrecognized-arg 0", UnrecognizedOptionException.class)
        );
    }

    Stream<Arguments> validArgsArgs() {
        return Stream.of(
                // Single args
                Arguments.of("-l ERROR", Map.of(ConfigProperty.LOG_LEVEL, "ERROR")),
                Arguments.of("--log-level ERROR", Map.of(ConfigProperty.LOG_LEVEL, "ERROR")),
                Arguments.of("-u file:///home/user/bin/protocol.jar",
                        Map.of(ConfigProperty.PROTOCOL_URL, "file:///home/user/bin/protocol.jar")),
                Arguments.of("--protocol-url file:///home/user/bin/protocol.jar",
                        Map.of(ConfigProperty.PROTOCOL_URL, "file:///home/user/bin/protocol.jar")),
                Arguments.of("-a key=test", Map.of(ConfigProperty.PROTOCOL_ARGS, "key=test")),
                Arguments.of("--protocol-args key=test", Map.of(ConfigProperty.PROTOCOL_ARGS, "key=test")),
                Arguments.of("-m OBFUSCATOR", Map.of(ConfigProperty.MODE, "OBFUSCATOR")),
                Arguments.of("--mode OBFUSCATOR", Map.of(ConfigProperty.MODE, "OBFUSCATOR")),
                Arguments.of("-M 32", Map.of(ConfigProperty.WG_MTU, "32")),
                Arguments.of("--wg-mtu 32", Map.of(ConfigProperty.WG_MTU, "32")),
                Arguments.of("-i 127.0.0.1", Map.of(ConfigProperty.IP, "127.0.0.1")),
                Arguments.of("--ip 127.0.0.1", Map.of(ConfigProperty.IP, "127.0.0.1")),
                Arguments.of("-p 3232", Map.of(ConfigProperty.PORT, "3232")),
                Arguments.of("--port 3232", Map.of(ConfigProperty.PORT, "3232")),
                Arguments.of("-I 10.0.7.1", Map.of(ConfigProperty.PEER_IP, "10.0.7.1")),
                Arguments.of("--peer-ip 10.0.7.1", Map.of(ConfigProperty.PEER_IP, "10.0.7.1")),
                Arguments.of("-P 2323", Map.of(ConfigProperty.PEER_PORT, "2323")),
                Arguments.of("--peer-port 2323", Map.of(ConfigProperty.PEER_PORT, "2323")),
                Arguments.of("-q 10000", Map.of(ConfigProperty.QUEUE_SIZE, "10000")),
                Arguments.of("--queue-size 10000", Map.of(ConfigProperty.QUEUE_SIZE, "10000")),
                Arguments.of("-Q 2", Map.of(ConfigProperty.QUEUE_PROCESSORS, "2")),
                Arguments.of("--queue-processors 2", Map.of(ConfigProperty.QUEUE_PROCESSORS, "2")),
                Arguments.of("-s 64", Map.of(ConfigProperty.MAX_SESSIONS, "64")),
                Arguments.of("--max-sessions 64", Map.of(ConfigProperty.MAX_SESSIONS, "64")),
                // Multiple args
                Arguments.of("-i 127.0.0.1 -p 3232 -I 10.0.7.1", Map.of(
                        ConfigProperty.IP, "127.0.0.1",
                        ConfigProperty.PORT, "3232",
                        ConfigProperty.PEER_IP, "10.0.7.1"
                )),
                Arguments.of("--peer-ip 127.0.0.1 --peer-port 3232", Map.of(
                        ConfigProperty.PEER_IP, "127.0.0.1",
                        ConfigProperty.PEER_PORT, "3232"
                )),
                // Edge cases
                Arguments.of("-M \"32\"", Map.of(ConfigProperty.WG_MTU, "32")),
                Arguments.of("-M=32", Map.of(ConfigProperty.WG_MTU, "32"))
        );
    }
}