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
package org.apache.fontbox.util;

import java.nio.charset.StandardCharsets;

/**
 * Methods that simplify string creation out of various types, byte array utility methods,
 * and helper methods to read integers out of bytes and byte arrays. 
 * <p>
 * Methods of this class returning strings or arrays should be used instead of {@code clone()}
 * or {@link String} constructors to benefit from the deduplication caching implemented by this class.
 * <p>
 * This is mostly used for character, glyph and font handling to avoid costly recreation of instances.
 * 
 * @author Gunnar Brand
 * @since 10.07.2024
 */
public class Bytes
{
    private final static boolean REDUCED = "reduced".equalsIgnoreCase(System.getProperty("org.apache.fontbox.util.Bytes.cache", "full"));

    private final static int MIN_SMP = 0x010000;

    
    private final static byte[]   EMPTY  = new byte  [0];
    
    private final static String[] LAT    = new String[0x100]; 
    private final static byte[][] BYTES  = new byte  [0x100][]; 
    
    // Deduplication caching simply assigns each value an index. The old value is evicted and overwritten
    // if conflicting values map to the same index. This is deemed acceptable since
    // the worst case scenario - every call a cache miss - mimics the old behavior
    // (ignoring potential method inlining and escape analysis removing object allocations).
    // 
    // Right now the index is the value modulo the table's length. This works well if input ranges
    // are expected to be "in blocks":
    // The BMP layout makes it look as it will work well with a 0x4000 or 0x8000 (east asian or CJK) cache size.
    // The SMP is clustered and sparsly populated, there shouldn't be much difference in memory consumption
    // for a full size table vs a reduced one, except for the memory cost of the larger table itself.
    // Because the SMP cache is actually used for all non-BMP planes, and the SIP plane is almost fully populated,
    // collisions are very likely to happen for a reduced table and PDFs containing CJK unified ideographs. 
    
    private final static String[] BMP    = new String[getLength("bmp", REDUCED ? 0x4000 : MIN_SMP, MIN_SMP)]; 
    private final static byte[][] CHARS  = new byte  [getLength("bmp", REDUCED ? 0x4000 : MIN_SMP, MIN_SMP)][];
    private final static String[] SMP    = new String[getLength("smp", REDUCED ? 0x4000 : MIN_SMP, MIN_SMP * 2)];
    // no reverse mapping cache for non-BMP, yet.
    
    
    /**
     * Returns a char of the last one or two bytes in the given byte array.
     */
    public static char getChar(byte[] bytes)
    {
        return getChar(bytes, 0, bytes.length);
    }

    
    /**
     * Returns a char of the last one or two bytes in the given byte array.
     */
    public static char getChar(byte[] bytes, int offset, int length)
    {
        switch (length) {
            default: offset += length - 2;
            case 2: return (char)toInt(bytes[offset], bytes[offset + 1]);
            case 1: return (char)toInt(bytes[offset]);
            case 0: return 0;
        }
    }
    
    
    /**
     * Returns an int from up to the last 4 bytes in the given byte array.
     */
    public static int getUnsigned(byte[] bytes)
    {
        return getUnsigned(bytes, 0, bytes.length);
    }
    

    /**
     * Returns an int from up to the last 4 bytes in the given byte array.
     */
    public static int getUnsigned(byte[] bytes, int offset, int length)
    {
        switch (length) {
            default: offset += length - 4;
            case 4: return toInt(bytes[offset], bytes[offset + 1], bytes[offset + 2], bytes[offset + 3]);
            case 3: return toInt(bytes[offset], bytes[offset + 1], bytes[offset + 2]);
            case 2: return toInt(bytes[offset], bytes[offset + 1]);
            case 1: return toInt(bytes[offset]);
            case 0: return 0;
        }
    }

    
    /**
     * Same as {@link Byte#toUnsignedInt(byte)}.
     */
    public static int toInt(byte byte0)
    {
        return byte0 & 0xff;
    }

    
    /**
     * Combines the lower 8 bits of each of the two byte to a 16 bit integer.
     */
    public static int toInt(byte byte0, byte byte1)
    {
        return combine(byte0 & 0xff, byte1 & 0xff);
    }

    
    /**
     * Combines the lower 8 bits of each of the three byte to a 24 bit integer.
     */
    public static int toInt(byte byte0, byte byte1, byte byte2)
    {
        return combine(byte0 & 0xff, byte1 & 0xff, byte2 & 0xff);
    }

    
    /**
     * Combines the lower 8 bits of each of the four bytes into a 32 bit integer.
     */
    public static int toInt(byte byte0, byte byte1, byte byte2, byte byte3)
    {
        return combine(byte0 & 0xff, byte1 & 0xff, byte2 & 0xff, byte3 & 0xff);
    }
    
    
    /**
     * Combines the lower 8 bits of each of the two integers to a 16 bit integer.
     */
    public static int toInt(int byte0, int byte1)
    {
        return combine(byte0 & 0xff, byte1 & 0xff);
    }

    
    /**
     * Combines the lower 8 bits of each of the three integers to a 24 bit integer.
     */
    public static int toInt(int byte0, int byte1, int byte2)
    {
        return combine(byte0 & 0xff, byte1 & 0xff, byte2 & 0xff);
    }

    
    /**
     * Combines the lower 8 bits of each of the four integer into a 32 bit integer.
     */
    public static int toInt(int byte0, int byte1, int byte2, int byte3)
    {
        return combine(byte0 & 0xff, byte1 & 0xff, byte2 & 0xff, byte3 & 0xff);
    }

    
    private static int combine(int byte0, int byte1)
    {
        return byte0 << 8 | byte1;
    }

    
    private static int combine(int byte0, int byte1, int byte2)
    {
        return byte0 << 16 | byte1 << 8 | byte2;
    }

    
    private static int combine(int byte0, int byte1, int byte2, int byte3)
    {
        return byte0 << 24 | byte1 << 16 | byte2 << 8 | byte3;
    }
    

    public static String toString(char ch)
    {
        if ( ch < 0x100 ) {
            String cached = LAT[ch];
            return cached == null ? LAT[ch] = Character.toString(ch) : cached;
        }
        
        int idx = index(ch, BMP.length);
        String cached = BMP[idx];
        return cached == null || cached.charAt(0) != ch
            ? BMP[idx] = Character.toString(ch) : cached;
    }

    
    public static String toString(int codepoint)
    {
        if ( codepoint < MIN_SMP ) return toString((char)codepoint);
        
        int idx = index(codepoint - MIN_SMP, SMP.length);
        String cached = SMP[idx];
        return cached == null || cached.codePointAt(0) != codepoint
            ? SMP[idx] = new String(new int[] { codepoint }, 0, 1) : cached;
    }
    
    
    public static String toString(char ch0, char ch1)
    {
        return Character.isSurrogatePair(ch0, ch1)
            ? toString(Character.toCodePoint(ch0, ch1))
            : String.valueOf(new char[] { ch0, ch1});
    }
    

    public static String toString(char[] chars)
    {
        switch (chars.length ) {
            case 0:
                return "";
                
            case 1:
                return toString(chars[0]);
                
            case 2: 
                if ( Character.isSurrogatePair(chars[0], chars[1]) ) {
                    int codepoint = Character.toCodePoint(chars[0], chars[1]);
                    int idx = index(codepoint - MIN_SMP, SMP.length);
                    String cached = SMP[idx];
                    return cached == null || cached.codePointAt(0) != codepoint
                        ? SMP[idx] = String.valueOf(chars) : cached;
                }
                    
            default:
                return String.valueOf(chars);
        }
    }
    

    /**
     * Converts a byte array into a string. Arrays of length 1 are assumed to be ISO-8859-1, longer ones UTF-16BE.
     */
    public static String toString(byte[] bytes)
    {
        switch (bytes.length) {
            case 0:
                return "";

            case 1:
                return toString((char)toInt(bytes[0]));

            case 2:
                return toString((char)toInt(bytes[0], bytes[1]));

            case 4:
                return toString((char)toInt(bytes[0], bytes[1]), (char)toInt(bytes[2], bytes[3]));

            default:
                return new String(bytes, StandardCharsets.UTF_16BE);
        }
    }
    

    /**
     * Increments the bytes as if it were a big BigEndian number.
     * 
     * @return the index of the least significant byte that did not overflow ({@code -1} if the whole number did overflow).
     */
    public static int increment(byte[] data)
    {
        int index = data.length - 1;
        while ( index >= 0 && ++data[index] == 0 ) index--;
        return index;
    }
    

    public static byte[] copy(byte[] bytes)
    {
        return intern(bytes, true);
    }
    
    
//    public static byte[] intern(byte[] bytes)
//    {
//        return intern(bytes, false);
//    }

    
    /**
     * @param copy Clones value if it cannot be interned (i.e. returns a value different from the argument, if it isn't the internal value).
     *   Should be used if the argument is not constant but the result of this call must be kept.
     */
    private static byte[] intern(byte[] bytes, boolean copy)
    {
        switch (bytes.length) {
            case 0:
                return EMPTY;
                
            case 1:
                return ofByte(bytes[0]);
                
            case 2:
                int idx = index(toInt(bytes[0], bytes[1]), CHARS.length);
                byte[] cached = CHARS[idx];
                return cached == null || cached[0] != bytes[0] || cached[1] != bytes[1]
                    ? CHARS[idx] = bytes.clone() : cached;
                
            default:
                return copy ? bytes.clone() : bytes;
        }
    }

    
    /**
     * Creates a byte array out of an 8 bit byte.
     */
    public static byte[] ofByte(int value)
    {
        int idx = value & 0xff;
        byte[] cached = BYTES[idx];
        return cached == null ? BYTES[idx] = new byte[] { (byte)value } : cached;
    }

    
    /**
     * Creates a byte array out of a 16 bit value.
     */
    public static byte[] ofChar(int value)
    {
        byte byte0 = (byte)(value >> 8), byte1 = (byte)value;
        int idx = index(value & 0xffff, CHARS.length);
        byte[] cached = CHARS[idx];
        return cached == null || cached[0] != byte0 || cached[1] != byte1
            ? CHARS[idx] = new byte[] { byte0, byte1 } : cached;
    }

    
    private static int index(int value, int length)
    {
        return value & (length - 1);
    }
    
    
    private static int getLength(String name, int dflt, int max)
    {
        int raw = Integer.getInteger("org.apache.fontbox.util.Bytes.cache." + name, dflt);
        return raw < dflt ? dflt : raw > max ? max : Integer.highestOneBit(raw);
    }
    
}
