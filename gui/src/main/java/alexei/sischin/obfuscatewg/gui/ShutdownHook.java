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

package alexei.sischin.obfuscatewg.gui;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ShutdownHook extends Thread {

    private final ExecutorService backendExecutor;

    public ShutdownHook(ExecutorService backendExecutor) {
        super("shutdown");
        this.backendExecutor = backendExecutor;
    }

    @Override
    public void run() {
        log.debug("Gracefully shutting down backend");
        this.backendExecutor.shutdownNow();
        try {
            if (!this.backendExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.error("Failed to await backend executor termination");
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for backend executor termination");
        }
    }
}
