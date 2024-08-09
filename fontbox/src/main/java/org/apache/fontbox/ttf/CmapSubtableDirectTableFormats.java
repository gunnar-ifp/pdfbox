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

import static org.apache.fontbox.ttf.CmapSubtable.verifyGlyphs;
import static org.apache.fontbox.ttf.CmapSubtable.message;
import static org.apache.fontbox.ttf.CmapSubtable.verify16;
import static org.apache.fontbox.ttf.CmapSubtable.verify32;

import java.io.IOException;

import org.apache.fontbox.ttf.CodeRanges.LookupRange;
import org.apache.fontbox.ttf.CodeRanges.Range;

/**
 * "cmap" subtable parsers for 1:1 lookup table formats.
 * <p>
 * TODO: verify size
 * 
 * @author Gunnar Brand
 */
class CmapSubtableDirectTableFormats
{

    /**
     * Parses a
     * <a href="https://learn.microsoft.com/en-us/typography/opentype/spec/cmap#format-0-byte-encoding-table">
     * Format 0: Byte encoding table</a>
     * subtable
     * 
     * @param numGlyphs number of glyphs to be read
     * @param data the data stream of the to be parsed ttf font
     * @throws IOException If there is an error parsing the true type font.
     */
    public static CodeRanges parseFormat0(int numGlyphs, TTFDataStream data, long streamOffset) throws IOException
    {
        data.seek(streamOffset + 6); // skip format, length and version
        
        Range range = new LookupRange(0, 255, CodeRanges.bytes(data.read(256)));
        
        if ( verifyGlyphs(0, range, numGlyphs) ) {
            return CodeRanges.EMPTY;
        }
        
        return new CodeRanges(range);
    }

    
    /**
     * Parses a
     * <a href="https://learn.microsoft.com/en-us/typography/opentype/spec/cmap#format-6-trimmed-table-mapping">
     * Format 6: Trimmed table mapping</a>
     * subtable.
     * 
     * @param numGlyphs number of glyphs to be read
     * @param data the data stream of the to be parsed ttf font
     * @throws IOException If there is an error parsing the true type font.
     */
    public static CodeRanges parseFormat6(int numGlyphs, TTFDataStream data, long streamOffset) throws IOException
    {
        data.seek(streamOffset + 6);
        
        final int firstCode  = data.readUnsignedShort();
        final int entryCount = data.readUnsignedShort();
        final int lastCode   = firstCode + entryCount - 1;
        
        if ( entryCount == 0 || verify16(6, firstCode, lastCode, false) ) {
            return CodeRanges.EMPTY;
        }
        
        Range range = new LookupRange(firstCode, lastCode, CodeRanges.chars(data.read(entryCount * 2)));
        
        if ( verifyGlyphs(6, range, numGlyphs) ) {
            return CodeRanges.EMPTY;
        }

        return new CodeRanges(range);
    }

    
    /**
     * Parses a
     * <a href="https://learn.microsoft.com/en-us/typography/opentype/spec/cmap#format-10-trimmed-array">
     * Format 10: Trimmed array</a>
     * subtable.
     * 
     * @param numGlyphs number of glyphs to be read
     * @param data the data stream of the to be parsed ttf font
     * @throws IOException If there is an error parsing the true type font.
     */
    public static CodeRanges parseFormat10(int numGlyphs, TTFDataStream data, long streamOffset) throws IOException
    {
        data.seek(streamOffset + 12);
        
        final int startCharCode = data.readInt();
        final int numChars      = data.readInt();
        final int endCharCode   = startCharCode + numChars - 1;

        if ( numChars < 0 || numChars > Character.MAX_CODE_POINT + 1 ) {
            throw new IOException(message(10, "too many characters: %d", Integer.toUnsignedLong(numChars)));
        }
        
        // TODO: if feature ADJUST, make endCharCode smaller if over MAX_CODE_POINT
        
        if ( numChars == 0 || verify32(10, startCharCode, endCharCode, false) ) {
            return CodeRanges.EMPTY;
        }
        
        Range range = new LookupRange(startCharCode, endCharCode, CodeRanges.chars(data.read(numChars * 2)));
        
        if ( verifyGlyphs(10, range, numGlyphs) ) {
            return CodeRanges.EMPTY;
        }

        return new CodeRanges(range);
    }
    
}
