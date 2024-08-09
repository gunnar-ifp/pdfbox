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

import static org.apache.fontbox.ttf.CmapSubtable.fail;
import static org.apache.fontbox.ttf.CmapSubtable.message;
import static org.apache.fontbox.ttf.CmapSubtable.verify32;
import static org.apache.fontbox.ttf.CmapSubtable.verifyGlyphs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import org.apache.fontbox.ttf.CodeRanges.DeltaRange;
import org.apache.fontbox.ttf.CodeRanges.Range;
import org.apache.fontbox.ttf.CodeRanges.ValueRange;;

/**
 * "cmap" subtable parsers for n:n offset delta table formats.
 * <p>
 * TODO: verify lengths
 * 
 * @author Gunnar Brand
 */
public class CmapSubtableDeltaFormats
{
    /**
     * Parses a
     * <a href="https://learn.microsoft.com/en-us/typography/opentype/spec/cmap#format-8-mixed-16-bit-and-32-bit-coverage">
     * Format 8: mixed 16-bit and 32-bit coverage</a>
     * subtable.
     * 
     * @param numGlyphs number of glyphs to be read
     * @param data the data stream of the to be parsed ttf font
     * @throws IOException If there is an error parsing the true type font.
     */
    public static CodeRanges parseFormat8(int numGlyphs, TTFDataStream data, long streamOffset) throws IOException
    {
        /*
         * Format 8 is very confusingly described:
         * | Subtable format 8 was designed to support Unicode supplementary-plane characters in UTF-16 encoding
         * 
         * It then states the fact that if codepoints are used, any 16-bit value > 0x10 cannot be the first
         * 16-bit of  a 32-bit codepoint value since codpoints only go up to 0x10FFFF.
         * And thus, except for 0 - 0x10, the word length of the character is immediatly known.
         * This is true and confusingly not further referenced and talked about. 
         * 
         * The font also contains a 65536 bit table to check a 16 bit value against, to see if is the
         * start a 32 bit value or not, even if the font itself doesn't have that many glyphs (to aid string iteration).
         * For the range definition, it says that the upper 16-bits must be 0, when the bit for the lower 16-bit
         * is not set in the bitset.
         * 
         * This leaves the conclusion that char codes are UTF16BE, i.e. either a BMP character or a high and low surrogate
         * pair and that the bitset must have set bits for all high and low surrogate values: high surrogates so 
         * a client can query the bitset with a high surrogate found in a string and add another 16-bit,
         * and low surrogates because otherwise the font loading fails.
         * 
         * Since the font seems to be "optimized", i.e. end code instead of length to avoid a addition,
         * it makes sense to feature this bitset instead of a client doing a  surrogate range check themselves.
         * Plus, in theory, a font could mark certain surrogate ranges as 16-bit pass through if it really wanted.
         * 
         * Note: This makes it look like anything goes (practical implications not known)
         * | 0 is not a special value for the high word of a 32-bit code point.
         * | A font must not have both a glyph for the code point 0x0000
         * | and glyphs for code points with a high word of 0x0000.
         */
        
//        data.seek(streamOffset + 4);
//        final long length = data.readUnsignedInt();
        
        data.seek(streamOffset + 12);
        
        final byte[] bits = data.read(8192);
        final IntPredicate is32 = i -> (bits[i >>> 3] & (1 << (~i & 7))) != 0;
        
        int numGroups = data.readInt();
        if ( numGroups == 0 ) {
            return CodeRanges.EMPTY;
        }
        if ( numGroups < 0 || numGroups > Character.MAX_CODE_POINT + 1 ) {
            throw new IOException(message(8, "too many SequentialMapGroups: %d", Integer.toUnsignedLong(numGroups)));
        }
        
        final List<Range> ranges = new ArrayList<>(numGroups);
        while ( --numGroups >= 0 )
        {
            final int startCharCode = data.readInt();
            final int endCharCode   = data.readInt();
            final int startGlyphId  = data.readInt();

            // validate sequential map group
            if ( Integer.compareUnsigned(startCharCode, endCharCode) > 0 ) {
                fail(8, "invalid range %#x - %#x", startCharCode, endCharCode);
                continue;
            }
            
            // Since the spec is no talking about surrogates, the following code
            // needs to brute force check all combinations against is32.
            int startHi = startCharCode >>> 16;
            int endHi   = endCharCode   >>> 16;
            int code    = startCharCode;
            if ( startHi == 0 ) {
                if ( endHi != 0 ) {
                    throw new IOException(message(8, "invalid 16 bit range %#x - %#x", startCharCode, endCharCode));
                }
                do {
                    if ( is32.test(code) ) {
                        throw new IOException(message(8, "invalid 16 bit code %#x", code));
                    }
                } while ( endCharCode != code++ );
            } else {
                // high and low surrogate
                do {
                    if ( !is32.test(code >>> 16) || !is32.test(code & 0xffff) ) {
                        throw new IOException(message(8, "invalid 32 bit code %#x", code));
                    }
                } while ( endCharCode != code++ );
            }
            
            // old pdfbox code assumed codes were UCS-4 instead of UTF-16BE, only ever allowed BMP code points.
            // Checked these against is32 and if marked 32 bit converted them into surrogate pairs
            // and then promptly back to a codepoint ><; (https://www.unicode.org/faq//utf_bom.html#utf16-4
            // it took the code from contains back and forth computations next to each other which most likely caused that mistake).
            // I assume the UTF-16BE codes in the pdf data stream get converted to codepoints when this font is used,
            // so we store ranges based on this.
            int start = startHi == 0 ? (char)startCharCode : Character.toCodePoint((char)startHi, (char)startCharCode);
            int end   = startHi == 0 ? (char)endCharCode   : Character.toCodePoint((char)endHi,   (char)endCharCode);
            
            if ( verify32(8, start, end, startGlyphId < 0) ) continue;
            Range range = new DeltaRange(start, end, -1, startGlyphId);
            if ( verifyGlyphs(8, range, numGlyphs) ) continue;
            
            ranges.add(range);
        }
        
        return new CodeRanges(ranges);
    }

    
   /**
     * Parses a
     * <a href="https://learn.microsoft.com/en-us/typography/opentype/spec/cmap#format-12-segmented-coverage">
     * Format 12: Segmented coverage</a>
     * subtable.
     * 
     * @param numGlyphs number of glyphs to be read
     * @param data the data stream of the to be parsed ttf font
     * @throws IOException If there is an error parsing the true type font.
     */
    public static CodeRanges parseFormat12(int numGlyphs, TTFDataStream data, long streamOffset) throws IOException
    {
        data.seek(streamOffset + 12);

        int numGroups = data.readInt();
        if ( numGroups == 0 ) {
            return CodeRanges.EMPTY;
        }
        if ( numGroups < 0 || numGroups > Character.MAX_CODE_POINT + 1 ) {
            throw new IOException(message(12, "too many SequentialMapGroups: %d", Integer.toUnsignedLong(numGroups)));
        }
        
        final List<Range> ranges = new ArrayList<>(numGroups);
        while ( --numGroups >= 0 ) {
            int startCharCode = data.readInt();
            int endCharCode   = data.readInt();
            int startGlyphId  = data.readInt();
            
            // TODO: if feature ADJUST, make endCharCode smaller if over MAX_CODE_POINT
            
            if ( verify32(12, startCharCode, endCharCode, startGlyphId < 0) ) continue;
            Range range = new DeltaRange(startCharCode, endCharCode, -1, startGlyphId);
            if ( verifyGlyphs(12, range, numGlyphs) ) continue;
            
            ranges.add(range);
        }
        
        return new CodeRanges(ranges);
    }

    
    /**
     * Parses a
     * <a href="https://learn.microsoft.com/en-us/typography/opentype/spec/cmap#format-13-many-to-one-range-mappings">
     * Format 13: Many-to-one range mappings</a>
     * subtable.
     * 
     * @param numGlyphs number of glyphs to be read
     * @param data the data stream of the to be parsed ttf font
     * @throws IOException If there is an error parsing the true type font.
     */
    public static CodeRanges parseFormat13(int numGlyphs, TTFDataStream data, long streamOffset) throws IOException
    {
        data.seek(streamOffset + 12);

        int numGroups = data.readInt();
        if ( numGroups == 0 ) {
            return CodeRanges.EMPTY;
        }
        if ( numGroups < 0 || numGroups > Character.MAX_CODE_POINT + 1 ) {
            throw new IOException(message(13, "too many ConstantMapGroups: %d", Integer.toUnsignedLong(numGroups)));
        }
        
        final List<Range> ranges = new ArrayList<>(numGroups);
        while ( --numGroups >= 0 ) {
            int startCharCode = data.readInt();
            int endCharCode   = data.readInt();
            int startGlyphId  = data.readInt();
            
            // TODO: if feature ADJUST, make endCharCode smaller if over MAX_CODE_POINT
            
            if ( verify32(13, startCharCode, endCharCode, startGlyphId < 0) ) continue;
            Range range = new ValueRange(startCharCode, endCharCode, startGlyphId);
            if ( verifyGlyphs(13, range, numGlyphs) ) continue;
            
            ranges.add(range);
        }
        
        return new CodeRanges(ranges);
    }
    
}
