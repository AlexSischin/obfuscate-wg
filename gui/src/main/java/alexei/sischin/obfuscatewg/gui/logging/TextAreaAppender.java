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

package alexei.sischin.obfuscatewg.gui.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@RequiredArgsConstructor
public class TextAreaAppender extends OutputStreamAppender<ILoggingEvent> {

    private static final int BYTE_BUF_SIZE = 10240;
    private static final int MAX_TEXT_SIZE = 1024 * 100;
    private static final int REFRESH_INTERVAL_MS = 1_000;

    private final TextArea textArea;

    private final StringBuilder logBuffer  = new StringBuilder();
    private final Thread thread = new Thread(this::updateLoop, "text-area-appender");
    private final ConcurrentLinkedQueue<String> logEntryQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void start() {
        OutputStream os = new CharBufferOutputStream(StandardCharsets.UTF_8, this::charCallback, BYTE_BUF_SIZE);
        super.setOutputStream(os);
        super.start();
        this.thread.setDaemon(true);
        this.thread.start();
    }

    @Override
    public void stop() {
        this.thread.interrupt();
        super.stop();
    }

    private void updateLoop() {
        Optional.ofNullable(textArea.getText())
                .ifPresent(logBuffer::append);
        try {
            while (!Thread.interrupted()) {
                boolean hasNewLogs = false;
                String logEntry;
                while ((logEntry = logEntryQueue.poll()) != null) {
                    hasNewLogs = true;
                    logBuffer.append(logEntry);
                    int overflow = logBuffer.length() - MAX_TEXT_SIZE;
                    if (overflow > 0) {
                        logBuffer.delete(0, overflow);
                    }
                }
                if (hasNewLogs) {
                    String newText = logBuffer.toString();
                    Platform.runLater(() -> {
                        this.textArea.setText(newText);
                        textArea.applyCss();
                        textArea.layout();
                        textArea.setScrollTop(Double.MAX_VALUE);
                    });
                }
                Thread.sleep(REFRESH_INTERVAL_MS);
            }
        } catch (InterruptedException e) {
            // exit
        }
    }

    private void charCallback(CharBuffer charBuffer) {
        String logEntry = charBuffer.toString();
        logEntryQueue.add(logEntry);
    }
}
