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

import java.util.concurrent.ThreadFactory;

public class BackendThreadFactory implements ThreadFactory {

    private volatile Thread currentThread;

    @Override
    public synchronized Thread newThread(Runnable r) {
        if (currentThread != null && currentThread.isAlive()) {
            throw new IllegalStateException("Thread already exists and is alive");
        }
        this.currentThread = new Thread(r);
        this.currentThread.setName("backend");
        return this.currentThread;
    }

    public void interruptCurrentThread() {
        this.currentThread.interrupt();
    }
}
