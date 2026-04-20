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

package alexei.sischin.obfuscatewg.core.concurrent;

import alexei.sischin.obfuscatewg.core._test.InterruptingExecutors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class ConcurrentResourceMapTest {

    @Test
    public void get_givenSingleThread_returnsCorrectValue() {
        int nElements = 10;
        ConcurrentResourceMap<Integer, String> victim = buildPopulatedVictim(nElements);

        assertThat(victim.get(4)).isEqualTo("4");
        assertThat(victim.get(0)).isEqualTo("0");
        assertThat(victim.get(9)).isEqualTo("9");
        assertThat(victim.get(10)).isNull();
    }

    @SneakyThrows
    @Test
    public void get_givenMultipleThreads_returnsExpectedValuesAndDoesNotThrow() {
        int nElements = 10;
        int nThreads = 20;
        ConcurrentResourceMap<Integer, String> victim = buildPopulatedVictim(nElements);

        CyclicBarrier startBarrier = new CyclicBarrier(nThreads);
        CountDownLatch finishLatch = new CountDownLatch(nThreads);
        Queue<List<String>> capturedValues = new ConcurrentLinkedQueue<>();
        try (ExecutorService executor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            for (int i = 0; i < nThreads; i++) {
                executor.submit(() -> {
                    try {
                        startBarrier.await(5, TimeUnit.SECONDS);
                        List<String> results = new LinkedList<>();
                        results.add(victim.get(4));
                        results.add(victim.get(0));
                        results.add(victim.get(9));
                        results.add(victim.get(10));
                        capturedValues.add(results);
                        finishLatch.countDown();
                    } catch (Exception e) {
                        log.error("Unexpected error in test thread", e);
                    }
                });
            }
            assertThat(finishLatch.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(capturedValues).containsExactlyElementsOf(Stream.generate(() -> {
            List<String> expectedResults = new LinkedList<>();
            expectedResults.add("4");
            expectedResults.add("0");
            expectedResults.add("9");
            expectedResults.add(null);
            return expectedResults;
        }).limit(nThreads).toList());
    }

    @Test
    public void put_givenSingleThread_doesNotBreakVictimState() {
        int nElements = 10;
        ConcurrentResourceMap<Integer, String> victim = buildPopulatedVictim(nElements);

        String getResult1 = victim.get(0);
        assertThat(getResult1).isEqualTo("0");

        ConcurrentResourceMap.PutResult<String> putResult1 = victim.put(1, "1!");
        assertThat(putResult1.eldestValue()).isNull();
        assertThat(putResult1.replacedValue()).isEqualTo("1");

        ConcurrentResourceMap.PutResult<String> putResult2 = victim.put(10, "10!");
        assertThat(putResult2.eldestValue()).isEqualTo("2");
        assertThat(putResult2.replacedValue()).isNull();
    }

    @SneakyThrows
    @Test
    public void put_givenMultipleThreadsAndOverflow_doesLeaveCorrectStateAndDoesNotThrow() {
        int nElements = 10;
        int nElementsExpected = (int) (nElements * 1.5);
        int nThreads = nElementsExpected * 10;
        ConcurrentResourceMap<Integer, String> victim = buildPopulatedVictim(nElements);

        CyclicBarrier startBarrier = new CyclicBarrier(nThreads);
        CountDownLatch finishLatch = new CountDownLatch(nThreads);
        Map<Integer, ConcurrentResourceMap.PutResult<String>> resultCaptor = new ConcurrentHashMap<>();
        try (ExecutorService executor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            for (int i = 0; i < nThreads; i++) {
                final int threadIndex = i;
                final int elementIndex = i % nElementsExpected;
                executor.submit(() -> {
                    try {
                        startBarrier.await(5, TimeUnit.SECONDS);
                        ConcurrentResourceMap.PutResult<String> result
                                = victim.put(elementIndex, "%s!".formatted(elementIndex));
                        resultCaptor.put(threadIndex, result);
                        finishLatch.countDown();
                    } catch (Exception e) {
                        log.error("Unexpected error in test thread", e);
                    }
                });
            }
            assertThat(finishLatch.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(victim.size()).isEqualTo(nElements);
        for (int i = 0; i < nElementsExpected; i++) {
            assertThat(victim.get(i)).isIn(null, "%s!".formatted(i));
        }
        for (int i = 0; i < nThreads; i++) {
            int elementIndex = i % nElementsExpected;
            ConcurrentResourceMap.PutResult<String> result = resultCaptor.get(i);
            assertThat(result.replacedValue())
                    .isIn(null, "%s".formatted(elementIndex), "%s!".formatted(elementIndex));
        }
    }

    @SneakyThrows
    @Test
    public void put_givenMultipleThreadsAndNoOverflow_doesLeaveCorrectStateAndDoesNotThrow() {
        int nElements = 10;
        int nElementsExpected = (int) (nElements * 1.5);
        int nThreads = nElementsExpected * 10;
        ConcurrentResourceMap<Integer, String> victim = buildPopulatedVictim(nElementsExpected, nElements);

        CyclicBarrier startBarrier = new CyclicBarrier(nThreads);
        CountDownLatch finishLatch = new CountDownLatch(nThreads);
        Map<Integer, ConcurrentResourceMap.PutResult<String>> resultCaptor = new ConcurrentHashMap<>();
        try (ExecutorService executor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            for (int i = 0; i < nThreads; i++) {
                final int threadIndex = i;
                final int elementIndex = i % nElementsExpected;
                executor.submit(() -> {
                    try {
                        startBarrier.await(5, TimeUnit.SECONDS);
                        ConcurrentResourceMap.PutResult<String> result
                                = victim.put(elementIndex, "%s!".formatted(elementIndex));
                        resultCaptor.put(threadIndex, result);
                        finishLatch.countDown();
                    } catch (Exception e) {
                        log.error("Unexpected error in test thread", e);
                    }
                });
            }
            assertThat(finishLatch.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(victim.size()).isEqualTo(nElementsExpected);
        for (int i = 0; i < nElementsExpected; i++) {
            assertThat(victim.get(i)).isEqualTo("%s!".formatted(i));
        }
        for (int i = 0; i < nThreads; i++) {
            int elementIndex = i % nElementsExpected;
            ConcurrentResourceMap.PutResult<String> result = resultCaptor.get(i);
            assertThat(result.replacedValue())
                    .isIn(null, "%s".formatted(elementIndex), "%s!".formatted(elementIndex));
            assertThat(result.eldestValue()).isNull();
        }
    }

    @Test
    public void computeIfAbsent_givenSingleThread_doesNotBreakVictimState() {
        int nElements = 10;
        ConcurrentResourceMap<Integer, String> victim = buildPopulatedVictim(nElements);

        ConcurrentResourceMap.ComputeIfAbsentResult<String> result1 = victim.computeIfAbsent(0, _ -> "0!");
        assertThat(result1.eldestValue()).isNull();
        assertThat(result1.existingOrNewValue()).isEqualTo("0");

        ConcurrentResourceMap.ComputeIfAbsentResult<String> result2 = victim.computeIfAbsent(10, _ -> "10!");
        assertThat(result2.eldestValue()).isEqualTo("1");
        assertThat(result2.existingOrNewValue()).isEqualTo("10!");
    }

    @SneakyThrows
    @Test
    public void computeIfAbsent_givenMultipleThreadsAndNoOverflow_doesLeaveCorrectStateAndDoesNotThrow() {
        int nElements = 10;
        int nElementsExpected = (int) (nElements * 1.5);
        int nThreads = nElementsExpected * 10;
        ConcurrentResourceMap<Integer, String> victim = buildPopulatedVictim(nElementsExpected, nElements);

        CyclicBarrier startBarrier = new CyclicBarrier(nThreads);
        CountDownLatch finishLatch = new CountDownLatch(nThreads);
        Map<Integer, ConcurrentResourceMap.ComputeIfAbsentResult<String>> resultCaptor = new ConcurrentHashMap<>();
        try (ExecutorService executor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            for (int i = 0; i < nThreads; i++) {
                final int threadIndex = i;
                final int elementIndex = i % nElementsExpected;
                executor.submit(() -> {
                    try {
                        startBarrier.await(5, TimeUnit.SECONDS);
                        ConcurrentResourceMap.ComputeIfAbsentResult<String> result
                                = victim.computeIfAbsent(elementIndex, "%s!"::formatted);
                        resultCaptor.put(threadIndex, result);
                        finishLatch.countDown();
                    } catch (Exception e) {
                        log.error("Unexpected error in test thread", e);
                    }
                });
            }
            assertThat(finishLatch.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(victim.size()).isEqualTo(nElementsExpected);
        for (int i = 0; i < nElementsExpected; i++) {
            if (i < nElements) {
                assertThat(victim.get(i)).isEqualTo("%s".formatted(i));
            } else {
                assertThat(victim.get(i)).isEqualTo("%s!".formatted(i));
            }
        }
        for (int i = 0; i < nThreads; i++) {
            int elementIndex = i % nElementsExpected;
            ConcurrentResourceMap.ComputeIfAbsentResult<String> result = resultCaptor.get(i);
            if (elementIndex < nElements) {
                assertThat(result.existingOrNewValue())
                        .isEqualTo("%s".formatted(elementIndex));
            } else {
                assertThat(result.existingOrNewValue())
                        .isEqualTo("%s!".formatted(elementIndex));
            }
            assertThat(result.eldestValue()).isNull();
        }
    }

    @SneakyThrows
    @Test
    public void computeIfAbsent_givenMultipleThreadsAndOverflow_doesLeaveCorrectStateAndDoesNotThrow() {
        int nElements = 10;
        int nElementsExpected = (int) (nElements * 1.5);
        int nThreads = nElementsExpected * 10;
        ConcurrentResourceMap<Integer, String> victim = buildPopulatedVictim(nElements);

        CyclicBarrier startBarrier = new CyclicBarrier(nThreads);
        CountDownLatch finishLatch = new CountDownLatch(nThreads);
        Map<Integer, ConcurrentResourceMap.ComputeIfAbsentResult<String>> resultCaptor = new ConcurrentHashMap<>();
        try (ExecutorService executor = InterruptingExecutors.newThreadPerTaskExecutor()) {
            for (int i = 0; i < nThreads; i++) {
                final int threadIndex = i;
                final int elementIndex = i % nElementsExpected;
                executor.submit(() -> {
                    try {
                        startBarrier.await(5, TimeUnit.SECONDS);
                        ConcurrentResourceMap.ComputeIfAbsentResult<String> result
                                = victim.computeIfAbsent(elementIndex, "%s!"::formatted);
                        resultCaptor.put(threadIndex, result);
                        finishLatch.countDown();
                    } catch (Exception e) {
                        log.error("Unexpected error in test thread", e);
                    }
                });
            }
            assertThat(finishLatch.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(victim.size()).isEqualTo(nElements);
        for (int i = 0; i < nElementsExpected; i++) {
            assertThat(victim.get(i)).isIn(null, "%s!".formatted(i));
        }
        for (int i = 0; i < nThreads; i++) {
            int elementIndex = i % nElementsExpected;
            ConcurrentResourceMap.ComputeIfAbsentResult<String> result = resultCaptor.get(i);
            assertThat(result.existingOrNewValue())
                    .isIn(null, "%s".formatted(elementIndex), "%s!".formatted(elementIndex));
        }
    }

    private ConcurrentResourceMap<Integer, String> buildPopulatedVictim(int capacity) {
        return buildPopulatedVictim(capacity, capacity);
    }

    private ConcurrentResourceMap<Integer, String> buildPopulatedVictim(int capacity, int elements) {
        ConcurrentResourceMap<Integer, String> victim = new ConcurrentResourceMap<>(capacity);
        assertThat(victim.size()).isEqualTo(0);
        for (int i = 0; i < elements; i++) {
            ConcurrentResourceMap.PutResult<String> putResult = victim.put(i, String.valueOf(i));
            assertThat(putResult).isNotNull();
            assertThat(putResult.eldestValue()).isNull();
            assertThat(putResult.replacedValue()).isNull();
            assertThat(victim.size()).isEqualTo(i + 1);
        }
        return victim;
    }
}