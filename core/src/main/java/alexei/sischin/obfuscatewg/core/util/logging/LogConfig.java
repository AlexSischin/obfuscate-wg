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

import java.util.Objects;

public record LogConfig(
        Level logLevel
) {

    public LogConfig {
        Objects.requireNonNull(logLevel, "Log level must be not null");
    }

    public static LogConfig fromRawStrings(
            String logLevel
    ) {
        Objects.requireNonNull(logLevel, "Log level must be not null");

        return new LogConfig(
                parseLogLevel(logLevel)
        );
    }

    @Override
    public String toString() {
        return "LogConfig{" +
                "logLevel=" + logLevel +
                '}';
    }

    private static Level parseLogLevel(String s) {
        if (s == null) {
            return null;
        }
        return switch (s) {
            case "OFF" -> Level.OFF;
            case "ERROR" -> Level.ERROR;
            case "WARN" -> Level.WARN;
            case "INFO" -> Level.INFO;
            case "DEBUG" -> Level.DEBUG;
            case "TRACE", "ALL" -> Level.TRACE;
            default -> throw new IllegalArgumentException("Invalid log level: \"%s\"".formatted(s));
        };
    }
}
