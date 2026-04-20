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

package alexei.sischin.obfuscatewg.core._test;

import lombok.experimental.UtilityClass;

import java.time.Duration;

@UtilityClass
public class Constants {

    public static final int TEST_BUF_SIZE = 64;
    public static final int TEST_WG_MTU = 64;
    public static final int TEST_QUEUE_SIZE = 100;
    public static final int TEST_QUEUE_PROCESSORS = 4;
    public static final int TEST_MAX_CLIENT_SESSIONS = 100;
    public static final int TEST_AWAIT_SECONDS = 5;
    public static final Duration MESSAGE_DELAY = Duration.ofNanos(1_000_000);
}
