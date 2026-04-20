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

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * Thread-safe map-alike data structure with limited capacity that removes eldest entries when full
 * and allows to retrieve the removed element in order to perform resource cleanup.
 *
 * @param <K> key.
 * @param <V> value.
 */
public final class ConcurrentResourceMap<K, V> {

    private final int capacity;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong accessNumber = new AtomicLong(Long.MIN_VALUE);
    private final Comparator<Map.Entry<K, ValueWrapper<V>>> accessNumberComparator = Comparator
            .comparing(e -> e.getValue().lastAccessNumber().get());
    private final Map<K, ValueWrapper<V>> delegate;

    public ConcurrentResourceMap(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity is final and must be greater than 0");
        }
        this.capacity = capacity;
        this.delegate = new HashMap<>(capacity, 1);
    }

    @Nullable
    public V get(K key) {
        this.lock.readLock().lock();
        try {
            ValueWrapper<V> valueWrapper = this.delegate.get(key);
            if (valueWrapper == null) {
                return null;
            }
            long number = this.accessNumber.getAndIncrement();
            valueWrapper.lastAccessNumber().set(number);
            return valueWrapper.value();
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public PutResult<V> put(K key, V value) throws IllegalArgumentException {
        this.lock.writeLock().lock();
        try {
            return putWithoutLock(key, value);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public ComputeIfAbsentResult<V> computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction, "Mapping function must be not null");
        this.lock.writeLock().lock();
        try {
            V existingValue;
            if ((existingValue = get(key)) == null) {
                V newValue = mappingFunction.apply(key);
                PutResult<V> putResult = putWithoutLock(key, newValue);
                return new ComputeIfAbsentResult<>(putResult.eldestValue(), newValue);
            }
            return new ComputeIfAbsentResult<>(null, existingValue);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public RemoveResult<V> remove(K key) {
        this.lock.writeLock().lock();
        try {
            ValueWrapper<V> removedValueWrapper = this.delegate.remove(key);
            return new RemoveResult<>(
                    removedValueWrapper != null
                            ? removedValueWrapper.value()
                            : null
            );
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public int size() {
        this.lock.readLock().lock();
        try {
            return this.delegate.size();
        } finally {
            this.lock.readLock().unlock();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (getClass().isInstance(obj)) {
            ConcurrentResourceMap<?, ?> other = (ConcurrentResourceMap<?, ?>) obj;
            this.lock.readLock().lock();
            try {
                return this.delegate.equals(other.delegate);
            } finally {
                this.lock.readLock().unlock();
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        this.lock.readLock().lock();
        try {
            return Objects.hash(this.delegate);
        } finally {
            this.lock.readLock().unlock();
        }
    }

    private PutResult<V> putWithoutLock(K key, V value) {
        V eldestValue = null;
        V replacedValue = null;
        long accessNumber = this.accessNumber.getAndIncrement();
        if ((this.delegate.size() == this.capacity) && !(this.delegate.containsKey(key))) {
            Map.Entry<K, ValueWrapper<V>> eldestEntry = this.delegate.entrySet().stream()
                    .min(this.accessNumberComparator)
                    .orElseThrow();
            this.delegate.remove(eldestEntry.getKey());
            eldestValue = eldestEntry.getValue().value();
        }
        ValueWrapper<V> valueWrapper = new ValueWrapper<>(value, new AtomicLong(accessNumber));
        ValueWrapper<V> replacedValueWrapper = this.delegate.put(key, valueWrapper);
        if (replacedValueWrapper != null) {
            replacedValue = replacedValueWrapper.value();
        }
        return new PutResult<>(eldestValue, replacedValue);
    }

    public record PutResult<V>(
            @Nullable
            V eldestValue,
            @Nullable
            V replacedValue
    ) {}

    public record ComputeIfAbsentResult<V>(
            @Nullable
            V eldestValue,
            V existingOrNewValue
    ) {}

    public record RemoveResult<V>(
            @Nullable
            V removedValue
    ) {}

    private record ValueWrapper<V>(
            V value,
            AtomicLong lastAccessNumber
    ) {}
}
