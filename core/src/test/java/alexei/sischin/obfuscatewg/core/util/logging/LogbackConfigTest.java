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

package alexei.sischin.obfuscatewg.core.util.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.DefaultJoranConfigurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class LogbackConfigTest {

    static Logger victim = LoggerFactory.getLogger("test-logger");

    @AfterEach
    void afterEach() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        DefaultJoranConfigurator defaultLogbackConfigurator = new DefaultJoranConfigurator();
        defaultLogbackConfigurator.setContext(context);
        defaultLogbackConfigurator.configure(null);
    }

    @Test
    void init_givenValidConfiguration_producesExpectedLogIntoStdout() {
        LogbackConfig.init();
        ByteArrayOutputStream capturingBos = logTest();
        String actualLog = capturingBos.toString(StandardCharsets.UTF_8);
        String maskedLog = maskLog(actualLog);
        assertThat(maskedLog).isEqualToIgnoringWhitespace("""
            XXXX-XX-XX XX:XX:XX.XXX [main] INFO  test-logger - Test info log
            XXXX-XX-XX XX:XX:XX.XXX [main] WARN  test-logger - Test warn log
            XXXX-XX-XX XX:XX:XX.XXX [main] ERROR test-logger - Test error log
            XXXX-XX-XX XX:XX:XX.XXX [main] ERROR test-logger - Test error log with throwable
            alexei.sischin.obfuscatewg.XXX.LogbackConfigTest$TestException: Test exception
                at obfuscate.wg.core@X.X.X/alexei.sischin.obfuscatewg.XXX.LogbackConfigTest.logTest(XXX.java:XXX)
            """);
    }

    @Test
    void configure_givenAlreadyInitAndValidConfiguration_producesExpectedLogIntoStdout() {
        LogbackConfig.init();
        LogConfig config = new LogConfig(Level.TRACE);
        LogbackConfig.configure(config);
        ByteArrayOutputStream capturingBos = logTest();
        String actualLog = capturingBos.toString(StandardCharsets.UTF_8);
        String maskedLog = maskLog(actualLog);
        assertThat(maskedLog).isEqualToIgnoringWhitespace("""
            XXXX-XX-XX XX:XX:XX.XXX [main] TRACE test-logger - Test trace log
            XXXX-XX-XX XX:XX:XX.XXX [main] DEBUG test-logger - Test debug log
            XXXX-XX-XX XX:XX:XX.XXX [main] INFO  test-logger - Test info log
            XXXX-XX-XX XX:XX:XX.XXX [main] WARN  test-logger - Test warn log
            XXXX-XX-XX XX:XX:XX.XXX [main] ERROR test-logger - Test error log
            XXXX-XX-XX XX:XX:XX.XXX [main] ERROR test-logger - Test error log with throwable
            alexei.sischin.obfuscatewg.XXX.LogbackConfigTest$TestException: Test exception
                at obfuscate.wg.core@X.X.X/alexei.sischin.obfuscatewg.XXX.LogbackConfigTest.logTest(XXX.java:XXX)
            """);
    }

    private static ByteArrayOutputStream logTest() {
        PrintStream stdout = System.out;
        ByteArrayOutputStream capturingBos = new ByteArrayOutputStream(10240); // 10 KB
        PrintStream capturingStream = new PrintStream(capturingBos);
        System.setOut(capturingStream);
        try {
            victim.trace("Test trace log");
            victim.debug("Test debug log");
            victim.info("Test info log");
            victim.warn("Test warn log");
            victim.error("Test error log");
            victim.error("Test error log with throwable", new TestException());
        } finally {
            System.setOut(stdout);
        }
        return capturingBos;
    }

    private static String maskLog(String log) {
        return log.replaceAll(
                "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3}",
                "XXXX-XX-XX XX:XX:XX.XXX"
        ).replaceAll(
                "alexei\\.sischin\\.obfuscatewg\\..+\\.LogbackConfigTest",
                "alexei.sischin.obfuscatewg.XXX.LogbackConfigTest"
        ).replaceAll(
                "\\(\\w+.java:\\d+\\)",
                "(XXX.java:XXX)"
        ).replaceAll(
                "obfuscate.wg.core@\\d+.\\d+.\\d+",
                "obfuscate.wg.core@X.X.X"
        );
    }

    private static class TestException extends RuntimeException {
        public TestException() {
            super("Test exception");
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            return Arrays.copyOfRange(super.getStackTrace(), 0, 1);
        }
    }
}