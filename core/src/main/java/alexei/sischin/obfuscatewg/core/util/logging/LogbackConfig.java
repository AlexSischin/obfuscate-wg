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
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.OutputStreamAppender;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

@UtilityClass
public class LogbackConfig {

    public static void init() {
        configure(Level.INFO);
    }

    public static void init(@Nullable OutputStreamAppender<ILoggingEvent> additionalAppender) {
        configure(Level.INFO, additionalAppender);
    }

    public static void configure(LogConfig config) {
        configure(config.logLevel());
    }

    public static void configure(LogConfig config, @Nullable OutputStreamAppender<ILoggingEvent> additionalAppender) {
        configure(config.logLevel(), additionalAppender);
    }

    private static void configure(Level rootLevel) {
        configure(rootLevel, null);
    }

    private static void configure(Level rootLevel, @Nullable OutputStreamAppender<ILoggingEvent> additionalAppender) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(rootLevel);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder.start();

        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(context);
        consoleAppender.setEncoder(encoder);
        consoleAppender.start();
        rootLogger.detachAndStopAllAppenders();
        rootLogger.addAppender(consoleAppender);

        if (additionalAppender != null) {
            additionalAppender.setContext(context);
            additionalAppender.setEncoder(encoder);
            additionalAppender.start();
            rootLogger.addAppender(additionalAppender);
        }
    }
}
