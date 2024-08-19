/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.fontbox.ttf;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.IntUnaryOperator;


/**
 * Fast binary serach based "cmap" subtable character code to gid range lookup.
 * Supports constant, delta and lookup table ranges as well as reverse lookup.
 * <p>
 * Note: reverse lookup is always linear over the ranges and will
 * be very slow for table lookup ranges. {@link #hasFastCodeLookup()}
 * returns true for instancese that support a fast reverse lookup.
 *  
 * @author Gunnar Brand
 * @since 18.07.2024
 */
class CodeRanges implements Iterable<CodeRanges.Range>
{
    private final int[] EMPTY_INT = {};
    
    public final static CodeRanges EMPTY = new CodeRanges();
    
    
    private final Range[] ranges;
    private final int size, maxGid, minGid;
    private final boolean fastCode;

    
    public CodeRanges(Collection<? extends Range> ranges)
    {
        this(ranges.toArray(new Range[ranges.size()]));
    }

    
    /**
     * Creates a new instance fo the give ranges.
     * <p>
     * Note: the argument is used as is and modified (sorted).
     */
    public CodeRanges(Range... ranges)
    {
        Arrays.sort(ranges);
        int count = 0, max = -1, min = Integer.MAX_VALUE;
        boolean fast = ranges.length <= 32;
        for ( Range r : ranges ) {
            count += r.size();
            max = Math.max(max, r.maxGid());
            min = Math.min(min, r.minGid());
            fast = fast && (r instanceof DeltaRange && r.map(r.start) == min || r instanceof ValueRange);
        }
        this.ranges = ranges;
        this.size = count;
        this.maxGid = max;
        this.minGid = min == Integer.MAX_VALUE ? -1 : min;
        this.fastCode = fast;
    }
    
    
    @Override
    public Iterator<Range> iterator()
    {
        return Arrays.asList(ranges).iterator();
    }

    
    public int size()
    {
        return size;
    }
    
    
    public boolean isEmpty()
    {
        return size == 0;
    }
    
    
    public boolean hasFastCodeLookup()
    {
        return fastCode;
    }

    
    public int minCode()
    {
        return isEmpty() ? -1 : ranges[0].start;
    }
    
    
    public int maxCode()
    {
        return isEmpty() ? -1 : ranges[ranges.length - 1].end;
    }
    
    
    public int minGid()
    {
        return minGid;
    }

    
    public int maxGid()
    {
        return maxGid;
    }
    
    
    public int toGid(int code)
    {
        Range r = search(ranges, 0, ranges.length - 1, code);
        return r == null ? -1 : r.map(code);
    }
    
    
    public int toCode(int gid)
    {
        if ( gid < minGid || gid > maxGid ) return -1;
        
        // TODO: make faster for delta only ranges by building a reverse binary tree
        for ( Range r : ranges ) {
            int c = r.unmap(gid);
            if ( c != -1 ) return c;
        }
        return -1;
    }
    
    
    public int[] toCodes(int gid)
    {
        if ( gid < minGid || gid > maxGid ) return EMPTY_INT;

        int single = -1,  multi[] = null, count = 0;
        for ( int i = 0; i < ranges.length; i++ ) {
            final int c = ranges[i].unmap(gid);
            if ( c == -1 ) continue;
            
            if ( count > 1 ) {
                multi[count] = c;
            }
            else if ( count == 1 ) {
                multi = new int[ranges.length - i + 1];
                multi[0] = single;
                multi[1] = c;
            }
            else {
                single = c;
            }
            count++;
        }
        
        return multi == null
            ? single == -1 ? EMPTY_INT : new int[] { single }
            : multi.length == count ? multi : Arrays.copyOf(multi, count);
    }

    
    private static Range search(Range[] ranges, int low, int high, int value)
    {
        while ( low <= high ) {
            int mid = (low + high) >>> 1;
            Range r = ranges[mid];
            if ( value > r.end ) low = mid + 1;
            else if ( value < r.start ) high = mid - 1;
            else return r; //mid;
            //else return mid;
        }
        // key not found.
        return null;
        //return ~low; // = -(low + 1) = -low - 1 = (~low + 1) - 1 = ~low; 
    }

    
    /**
     * Wraps a byte array containing uint8 values. 
     */
    public static IntUnaryOperator bytes(final byte[] array)
    {
        return i -> array[i] & 0xff;
    }

    
    /**
     * Slices a byte buffer  containing uint8 values. 
     */
    public static IntUnaryOperator bytes(ByteBuffer buffer)
    {
        final ByteBuffer slice = buffer.slice();
        return i -> slice.get(i) & 0xff;
    }

    
    /**
     * Wraps a byte array containing uint16 values.
     * The returned lookup table will operate on 16 bits, i.e. offset 0 returns
     * the 16 bit at bytes 0 and 1, offset 1 will address bytes 2 and 3, etc. 
     */
    public static IntUnaryOperator chars(byte[] array)
    {
        return ByteBuffer.wrap(array).asCharBuffer()::get;
    }

    
    /**
     * Wraps a byte buffer containing uint16 values.
     * The returned lookup table will operate on 16 bits, i.e. offset 0 returns
     * the 16 bit at bytes 0 and 1, offset 1 will address bytes 2 and 3, etc. 
     */
    public static IntUnaryOperator chars(ByteBuffer buffer)
    {
        return buffer.asCharBuffer()::get;
    }
    
    
    @FunctionalInterface
    public interface MappingConsumer
    {
        void accept(int code, int gid);
    }
    
    
    abstract static class Range implements Comparable<Range>
    {
        /** The start code */
        public final int start;
        /** The end code, inclusive. */
        public final int end;
        protected final int mask;

        protected Range(int start, int end, int mask) {
            this.start = start;
            this.end = end;
            this.mask = mask;
        }
        
        public int size() {
            return end - start + 1;
        }
        
        public final int map(int code) {
            return code < start || code > end ? 0 : translate(code) & mask;
        }
        
        /**
         * <b>Important: only to be called by {@link #map(int)}, must not be called directly.</b>
         * @param code current character code 
         */
        protected int translate(int code) {
            return code;
        }
        
        public int unmap(int gid) {
            for ( int code = start; code <= end; code++ ) {
                if ( map(code) == gid ) return code;
            }
            return -1;
        }
        
        public int maxGid() {
            int max = -1;
            for ( int code = start; code <= end; code++ ) {
                max = Math.max(max, map(code));
            }
            return max;
        }
        
        public int minGid() {
            int min = Integer.MAX_VALUE;
            for ( int code = start; code <= end; code++ ) {
                min = Math.min(min, map(code));
            }
            return min == Integer.MAX_VALUE ? -1 : min;
        }

        public void forEach(MappingConsumer consumer) {
            for ( int code = start; code <= end; code++ ) {
                consumer.accept(code, map(code));
            }
        }
        
        public boolean intersects(Range other) {
            return end >= other.start && start <= other.end;
        }
        
        @Override
        public int compareTo(Range o) {
            int i = Integer.compare(start, o.start);
            return i == 0 ? Integer.compare(end, o.end) : i;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + start;
            result = prime * result + end;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (!(obj instanceof Range)) return false;
            Range other = (Range)obj;
            if (start != other.start) return false;
            if (end != other.end) return false;
            return true;
        }
        
        @Override
        public String toString() {
            return String.format("%#x - %#x", start, end);
        }
    }


    /**
     * <ul>
     * <li>format13: int -> value
     * </ul>
     */
    static final class ValueRange extends Range
    {
        private final int value;

        public ValueRange(int start, int end, int value) {
            super(start, end, -1);
            this.value = value;
        }
        
        @Override
        protected int translate(int code) {
            return value;
        }
        
        @Override
        public int unmap(int gid) {
            return gid == value ? start : -1;
        }
        
        @Override
        public int maxGid() {
            return map(start);
        }
        
        @Override
        public int minGid() {
            return map(start);
        }
    }


    /**
     * <ul>
     * <li>format4:  char -> code + delta & 0xffff, notmappable -> 0
     * <li>format8:  int  -> code - start + startGlyph [& 0xffff?]
     * <li>format12: int  -> code - start + startGlyph [& 0xffff?]
     * </ul>
     */
    static final class DeltaRange extends Range
    {
        private final int delta;

        public DeltaRange(int start, int end, int mask, int target) {
            super(start, end, mask);
            this.delta = target - start;
        }
        
        @Override
        protected int translate(int code) {
            return code + delta;
        }
        
        @Override
        public int unmap(int gid) {
            int sgid = map(start);
            int egid = map(end);
            if ( sgid <= egid ) {
                return gid >= sgid && gid <= egid ? start + gid - sgid : -1;
            }
            // TODO: this code doesn't really works with all overflow scenarios IMHO 
//            if ( gid >= sgid ) {
//                return gid <= mask ? start + gid - sgid : -1;
//            }
//            return gid <= egid ? end - egid + gid : -1;
            return super.unmap(gid);
        }
        
        @Override
        public int maxGid() {
            int sgid = map(start);
            int egid = map(end);
            return sgid <= egid ? egid : mask;
        }
        
        @Override
        public int minGid() {
            int sgid = map(start);
            int egid = map(end);
            return sgid <= egid ? sgid : egid;
        }
    }

    
    /**
     * <ul>
     * <li>format0:  byte -> byte[code] [& 0xff]
     * <li>format6:  char -> char[code - start], notmappable -> 0
     * <li>format10: int  -> char[code - start]
     * 
     * <li>format2:  char -> char[code - start + offset], if gid != 0, gid + delta & 0xffff, notmappable -> 0
     * <li>format4:  char -> char[code - start + offset], if gid != 0, gid + delta & 0xffff, notmappable -> 0
     * </ul>
     * TODO: create reverse table for small table sizes (256 bytes?)
     */
    static class LookupRange extends Range
    {
        private final int delta;
        private IntUnaryOperator table;
        private int offset;

        public LookupRange(int start, int end, IntUnaryOperator table) {
            this(start, end, 0, 0);
            this.table = table;
        }

        public LookupRange(int start, int end, int delta, int offset) {
            super(start, end, 0xffff);
            this.delta = delta;
            this.offset = offset - start;
        }

        public void setTable(IntUnaryOperator table) {
            this.table = table;
        }

        public int getOffset() {
            return offset + start;
        }
        
        public void setOffset(int offset) {
            this.offset = offset - start;
        }
        
        @Override
        protected int translate(int code) {
            int gid = table.applyAsInt(code + offset);
            return gid == 0 ? 0 : gid + delta;
        }
    }
    
}
