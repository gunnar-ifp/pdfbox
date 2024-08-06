/*
 * Copyright (C) 2002-2024 Sebastiano Vigna
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.fontbox.util.primitive;

import static org.apache.fontbox.util.primitive.HashCommon.arraySize;
import static org.apache.fontbox.util.primitive.HashCommon.maxFill;

import java.util.Arrays;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;

/**
 * A type-specific hash map with a fast, small-footprint implementation.
 *
 * <p>
 * Instances of this class use a hash table to represent a map. The table is filled up to a
 * specified <em>load factor</em>, and then doubled in size to accommodate new entries. If the table
 * is emptied below <em>one fourth</em> of the load factor, it is halved in size; however, the table
 * is never reduced to a size smaller than that at creation time: this approach makes it possible to
 * create maps with a large capacity in which insertions and deletions do not cause immediately
 * rehashing. Moreover, halving is not performed when deleting entries from an iterator, as it would
 * interfere with the iteration process.
 *
 * <p>
 * Note that {@link #clear()} does not modify the hash table size. Rather, a family of
 * {@linkplain #trim() trimming methods} lets you control the size of the table; this is
 * particularly useful if you reuse instances of this class.
 *
 * @see HashCommon
 */
@SuppressWarnings("hiding")
public class Int2IntHashMap
{

    @FunctionalInterface
    public interface IntIntConsumer
    {
        void accept(int key, int value);
    }

    private static final boolean ASSERTS = false;
    /** The array of keys. */
    private int[] key;
    /** The array of values. */
    private int[] value;
    /** The mask for wrapping a position counter. */
    private int mask;
    /** Whether this map contains the key zero. */
    private boolean containsNullKey;
    /** The current table size. */
    private int n;
    /** Threshold after which we rehash. It must be the table size times {@link #f}. */
    private int maxFill;
    /** We never resize below this threshold, which is the construction-time {#n}. */
    private final int minN;
    /** Number of entries in the set (including the key zero, if present). */
    private int size;
    /** The acceptable load factor. */
    private final float f;

    /**
     * The default return value for {@code get()}, {@code put()} and {@code remove()}.
     */
    private int defRetValue;

    public Int2IntHashMap defaultReturnValue(final int rv) {
        defRetValue = rv;
        return this;
    }

    public int defaultReturnValue() {
        return defRetValue;
    }

    /**
     * Creates a new hash map.
     *
     * <p>
     * The actual table size will be the least power of two greater than {@code expected}/{@code f}.
     *
     * @param expected the expected number of elements in the hash map.
     * @param f the load factor.
     */

    private Int2IntHashMap(final int expected, final float f) {
        if (f <= 0 || f >= 1) throw new IllegalArgumentException("Load factor must be greater than 0 and smaller than 1");
        if (expected < 0) throw new IllegalArgumentException("The expected number of elements must be nonnegative");
        this.f = f;
        minN = n = arraySize(expected, f);
        mask = n - 1;
        maxFill = maxFill(n, f);
        key = new int[n + 1];
        value = new int[n + 1];
    }

    /**
     * Creates a new hash map with {@link HashCommon#DEFAULT_LOAD_FACTOR} as load factor.
     *
     * @param expected the expected number of elements in the hash map.
     */
    public Int2IntHashMap(final int expected) {
        this(expected, HashCommon.DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a new hash map with initial expected {@link HashCommon#DEFAULT_INITIAL_SIZE} entries and
     * {@link HashCommon#DEFAULT_LOAD_FACTOR} as load factor.
     */
    public Int2IntHashMap() {
        this(HashCommon.DEFAULT_INITIAL_SIZE, HashCommon.DEFAULT_LOAD_FACTOR);
    }

//    /**
//     * Creates a new hash map with {@link HashCommon#DEFAULT_LOAD_FACTOR} as load factor using the elements of
//     * two parallel arrays.
//     *
//     * @param k the array of keys of the new hash map.
//     * @param v the array of corresponding values in the new hash map.
//     * @throws IllegalArgumentException if {@code k} and {@code v} have different lengths.
//     */
//    public Int2IntOpenHashMap(final int[] k, final int[] v) {
//        this(k.length);
//        if (k.length != v.length) throw new IllegalArgumentException("The key array and the value array have different lengths (" + k.length + " and " + v.length + ")");
//        for (int i = 0; i < k.length; i++) this.put(k[i], v[i]);
//    }

    private int realSize() {
        return containsNullKey ? size - 1 : size;
    }

    public void forEachKey(final IntConsumer consumer) {
        if (containsNullKey) consumer.accept(key[n]);
        for (int pos = n; pos-- != 0;) {
            final int k = key[pos];
            if (!((k) == (0))) consumer.accept(k);
        }
    }

    public void forEachValue(final IntConsumer consumer) {
        if (containsNullKey) consumer.accept(value[n]);
        for (int pos = n; pos-- != 0;) {
            final int k = key[pos];
            if (!((k) == (0))) consumer.accept(value[pos]);
        }
    }

    public void forEachKeyValue(final IntIntConsumer consumer) {
        if (containsNullKey) consumer.accept(key[n], value[n]);
        for (int pos = n; pos-- != 0;) {
            final int k = key[pos];
            if (!((k) == (0))) consumer.accept(k, value[pos]);
        }
    }


    /**
     * Ensures that this map can hold a certain number of keys without rehashing.
     *
     * @param capacity a number of keys; there will be no rehashing unless the map {@linkplain #size()
     *            size} exceeds this number.
     */
    public void ensureCapacity(final int capacity) {
        final int needed = arraySize(capacity, f);
        if (needed > n) rehash(needed);
    }

    private void tryCapacity(final long capacity) {
        final int needed = (int)Math.min(1 << 30, Math.max(2, HashCommon.nextPowerOfTwo((long)Math.ceil(capacity / f))));
        if (needed > n) rehash(needed);
    }

    private int removeEntry(final int pos) {
        final int oldValue = value[pos];
        size--;
        shiftKeys(pos);
        if (n > minN && size < maxFill / 4 && n > HashCommon.DEFAULT_INITIAL_SIZE) rehash(n / 2);
        return oldValue;
    }

    private int removeNullEntry() {
        containsNullKey = false;
        final int oldValue = value[n];
        size--;
        if (n > minN && size < maxFill / 4 && n > HashCommon.DEFAULT_INITIAL_SIZE) rehash(n / 2);
        return oldValue;
    }

    public void putAll(Int2IntHashMap m)
    {
        if (f <= .5) ensureCapacity(m.size()); // The resulting map will be sized for m.size() elements
        else tryCapacity(size() + m.size()); // The resulting map will be tentatively sized for size() + m.size()
        m.forEachKeyValue(this::put);
    }

    private int find(final int k) {
        if (((k) == (0))) return containsNullKey ? n : -(n + 1);
        int curr;
        final int[] key = this.key;
        int pos;
        // The starting point.
        if (((curr = key[pos = (HashCommon.mix((k))) & mask]) == (0))) return -(pos + 1);
        if (((k) == (curr))) return pos;
        // There's always an unused entry.
        while (true) {
            if (((curr = key[pos = (pos + 1) & mask]) == (0))) return -(pos + 1);
            if (((k) == (curr))) return pos;
        }
    }

    private void insert(final int pos, final int k, final int v) {
        if (pos == n) containsNullKey = true;
        key[pos] = k;
        value[pos] = v;
        if (size++ >= maxFill) rehash(arraySize(size + 1, f));
        if (ASSERTS) checkTable();
    }

    public int put(final int k, final int v) {
        final int pos = find(k);
        if (pos < 0) {
            insert(-pos - 1, k, v);
            return defRetValue;
        }
        final int oldValue = value[pos];
        value[pos] = v;
        return oldValue;
    }

    /**
     * Shifts left entries with the specified hash code, starting at the specified position, and empties
     * the resulting free entry.
     *
     * @param pos a starting position.
     */
    private final void shiftKeys(int pos) {
        // Shift entries with the same hash.
        int last, slot;
        int curr;
        final int[] key = this.key;
        for (;;) {
            pos = ((last = pos) + 1) & mask;
            for (;;) {
                if (((curr = key[pos]) == (0))) {
                    key[last] = (0);
                    return;
                }
                slot = (HashCommon.mix((curr))) & mask;
                if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) break;
                pos = (pos + 1) & mask;
            }
            key[last] = curr;
            value[last] = value[pos];
        }
    }

    public int remove(final int k) {
        if (((k) == (0))) {
            if (containsNullKey) return removeNullEntry();
            return defRetValue;
        }
        int curr;
        final int[] key = this.key;
        int pos;
        // The starting point.
        if (((curr = key[pos = (HashCommon.mix((k))) & mask]) == (0))) return defRetValue;
        if (((k) == (curr))) return removeEntry(pos);
        while (true) {
            if (((curr = key[pos = (pos + 1) & mask]) == (0))) return defRetValue;
            if (((k) == (curr))) return removeEntry(pos);
        }
    }

    public int get(final int k) {
        if (((k) == (0))) return containsNullKey ? value[n] : defRetValue;
        int curr;
        final int[] key = this.key;
        int pos;
        // The starting point.
        if (((curr = key[pos = (HashCommon.mix((k))) & mask]) == (0))) return defRetValue;
        if (((k) == (curr))) return value[pos];
        // There's always an unused entry.
        while (true) {
            if (((curr = key[pos = (pos + 1) & mask]) == (0))) return defRetValue;
            if (((k) == (curr))) return value[pos];
        }
    }

    public boolean containsKey(final int k) {
        if (((k) == (0))) return containsNullKey;
        int curr;
        final int[] key = this.key;
        int pos;
        // The starting point.
        if (((curr = key[pos = (HashCommon.mix((k))) & mask]) == (0))) return false;
        if (((k) == (curr))) return true;
        // There's always an unused entry.
        while (true) {
            if (((curr = key[pos = (pos + 1) & mask]) == (0))) return false;
            if (((k) == (curr))) return true;
        }
    }

    public boolean containsValue(final int v) {
        final int value[] = this.value;
        final int key[] = this.key;
        if (containsNullKey && ((value[n]) == (v))) return true;
        for (int i = n; i-- != 0;) if (!((key[i]) == (0)) && ((value[i]) == (v))) return true;
        return false;
    }

    public int getOrDefault(final int k, final int defaultValue) {
        if (((k) == (0))) return containsNullKey ? value[n] : defaultValue;
        int curr;
        final int[] key = this.key;
        int pos;
        // The starting point.
        if (((curr = key[pos = (HashCommon.mix((k))) & mask]) == (0))) return defaultValue;
        if (((k) == (curr))) return value[pos];
        // There's always an unused entry.
        while (true) {
            if (((curr = key[pos = (pos + 1) & mask]) == (0))) return defaultValue;
            if (((k) == (curr))) return value[pos];
        }
    }

    public int putIfAbsent(final int k, final int v) {
        final int pos = find(k);
        if (pos >= 0) return value[pos];
        insert(-pos - 1, k, v);
        return defRetValue;
    }

    public boolean remove(final int k, final int v) {
        if (((k) == (0))) {
            if (containsNullKey && ((v) == (value[n]))) {
                removeNullEntry();
                return true;
            }
            return false;
        }
        int curr;
        final int[] key = this.key;
        int pos;
        // The starting point.
        if (((curr = key[pos = (HashCommon.mix((k))) & mask]) == (0))) return false;
        if (((k) == (curr)) && ((v) == (value[pos]))) {
            removeEntry(pos);
            return true;
        }
        while (true) {
            if (((curr = key[pos = (pos + 1) & mask]) == (0))) return false;
            if (((k) == (curr)) && ((v) == (value[pos]))) {
                removeEntry(pos);
                return true;
            }
        }
    }

    public boolean replace(final int k, final int oldValue, final int v) {
        final int pos = find(k);
        if (pos < 0 || !((oldValue) == (value[pos]))) return false;
        value[pos] = v;
        return true;
    }

    public int replace(final int k, final int v) {
        final int pos = find(k);
        if (pos < 0) return defRetValue;
        final int oldValue = value[pos];
        value[pos] = v;
        return oldValue;
    }

    public int computeIfAbsent(final int k, final IntUnaryOperator mappingFunction) {
        java.util.Objects.requireNonNull(mappingFunction);
        final int pos = find(k);
        if (pos >= 0) return value[pos];
        final int newValue = mappingFunction.applyAsInt(k);
        insert(-pos - 1, k, newValue);
        return newValue;
    }

    public int computeIfPresent(final int k, final IntBinaryOperator remappingFunction) {
        java.util.Objects.requireNonNull(remappingFunction);
        final int pos = find(k);
        if (pos < 0) return defRetValue;
        final int newValue = remappingFunction.applyAsInt(k, value[pos]);
        if (newValue == defRetValue) {
            if (((k) == (0))) removeNullEntry();
            else removeEntry(pos);
            return defRetValue;
        }
        return value[pos] = newValue;
    }

    public int compute(final int k, final IntBinaryOperator remappingFunction) {
        java.util.Objects.requireNonNull(remappingFunction);
        final int pos = find(k);
        final int newValue = remappingFunction.applyAsInt(k, pos >= 0 ? value[pos] : defRetValue);
        if (newValue == defRetValue) {
            if (pos >= 0) {
                if (((k) == (0))) removeNullEntry();
                else removeEntry(pos);
            }
            return defRetValue;
        }
        int newVal = newValue;
        if (pos < 0) {
            insert(-pos - 1, k, newVal);
            return newVal;
        }
        return value[pos] = newVal;
    }

    public void clear() {
        if (size == 0) return;
        size = 0;
        containsNullKey = false;
        Arrays.fill(key, (0));
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Rehashes this map if the table is too large.
     */
    public boolean trim() {
        final int l = HashCommon.nextPowerOfTwo((int)Math.ceil(size / f));
        if (l >= this.n || size > maxFill(l, f)) return true;
        try {
            rehash(l);
        } catch (OutOfMemoryError cantDoIt) {
            return false;
        }
        return true;
    }

    /**
     * Rehashes the map.
     *
     * <p>
     * This method implements the basic rehashing strategy, and may be overridden by subclasses
     * implementing different rehashing strategies (e.g., disk-based rehashing). However, you should not
     * override this method unless you understand the internal workings of this class.
     *
     * @param newN the new size
     */
    private void rehash(final int newN) {
        final int key[] = this.key;
        final int value[] = this.value;
        final int mask = newN - 1; // Note that this is used by the hashing macro
        final int newKey[] = new int[newN + 1];
        final int newValue[] = new int[newN + 1];
        int i = n, pos;
        for (int j = realSize(); j-- != 0;) {
            while (((key[--i]) == (0)));
            if (!((newKey[pos = (HashCommon.mix((key[i]))) & mask]) == (0))) while (!((newKey[pos = (pos + 1) & mask]) == (0)));
            newKey[pos] = key[i];
            newValue[pos] = value[i];
        }
        newValue[newN] = value[n];
        n = newN;
        this.mask = mask;
        maxFill = maxFill(n, f);
        this.key = newKey;
        this.value = newValue;
    }

    /**
     * Returns a hash code for this map.
     *
     * This method overrides the generic method provided by the superclass. Since {@code equals()} is
     * not overriden, it is important that the value returned by this method is the same value as the
     * one returned by the overriden method.
     *
     * @return a hash code for this map.
     */
    @Override
    public int hashCode() {
        int h = 0;
        for (int j = realSize(), i = 0, t = 0; j-- != 0;) {
            while (((key[i]) == (0))) i++;
            t = (key[i]);
            t ^= (value[i]);
            h += t;
            i++;
        }
        // Zero / null keys have hash zero.
        if (containsNullKey) h += (value[n]);
        return h;
    }

    private void checkTable() {
    }
}
