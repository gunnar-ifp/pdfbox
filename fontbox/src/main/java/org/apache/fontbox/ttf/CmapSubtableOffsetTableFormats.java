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
import static org.apache.fontbox.ttf.CmapSubtable.hasFeature;
import static org.apache.fontbox.ttf.CmapSubtable.message;
import static org.apache.fontbox.ttf.CmapSubtable.verify16;
import static org.apache.fontbox.ttf.CmapSubtable.verifyGlyphs;
import static org.apache.fontbox.ttf.CmapSubtable.warn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntUnaryOperator;

import org.apache.fontbox.ttf.CmapSubtable.Validation;
import org.apache.fontbox.ttf.CodeRanges.DeltaRange;
import org.apache.fontbox.ttf.CodeRanges.LookupRange;
import org.apache.fontbox.ttf.CodeRanges.Range;

/**
 * "cmap" subtable parsers for 1:1 offset based lookup table formats.
 * 
 * @author Gunnar Brand
 */
public class CmapSubtableOffsetTableFormats
{
    /**
     * Parses a
     * <a href="https://learn.microsoft.com/en-us/typography/opentype/spec/cmap#format-2-high-byte-mapping-through-table">
     * Format 2: High byte mapping through table</a>
     * subtable.
     * 
     * @param numGlyphs number of glyphs to be read
     * @param data The stream to read the data from.
     * @throws IOException If there is an error reading the data.
     */
    public static CodeRanges parseFormat2(int numGlyphs, TTFDataStream data, long streamOffset) throws IOException
    {
        data.seek(streamOffset + 6);
        
        final CharBuffer keys    = ByteBuffer.wrap(data.read(512)).asCharBuffer();
        final ByteBuffer headers = ByteBuffer.wrap(data.read(keys.chars().max().getAsInt() + 8));
        
        // Note:
        // The original code did iterate over the number of subtables only, this is not correct.
        // The index in subHeaderKeys (0 - 255) is the high byte (unless mapped to subheader 0),
        // more than one high byte can use the same subheader if they wanted to.
        
        final List<LookupRange> ranges = new ArrayList<>(256);
        LookupRange header0 = null;
        int glyphStart = Integer.MAX_VALUE, glyphEnd = 0;
        for ( int hi = 0; hi < 256; hi++ ) {
            if ( hasFeature(Validation.STRICT) && (keys.get(hi) & 0x7) != 0 ) {
                fail(2, "invalid offset %#x", keys.get(hi));
                continue;
            }

            final int offset = keys.get(hi) & ~7;
            if ( offset == 0 && header0 != null ) {
                if ( hi < header0.start || hi > header0.end ) {
                    if ( hasFeature(Validation.STRICT) ) {
                        fail(2, "byte character %#x outside 8 bit range %s", hi, header0);
                    } else {
                        // TODO: warn only once
                        warn(2, "byte character %#x outside 8 bit range %s", hi, header0);
                    }
                }
                continue;
            }
            
            final int firstCode     = headers.getChar (offset);
            int       lastCode      = headers.getChar (offset + 2) + firstCode - 1;
            final int idDelta       = headers.getShort(offset + 4);
            int       idRangeOffset = headers.getChar (offset + 6) + 6 + 512 + offset + 6; // absolute offset in table
            
            if ( lastCode < firstCode ) continue;
            
            if ( lastCode > 255 ) {
                if ( firstCode > 255 || !hasFeature(Validation.ADJUST) ) {
                    fail(2, "invalid low byte range %x:%#x - %#x", hi, firstCode, lastCode);
                    continue;
                }
                lastCode = 255;
            }

            if ( hasFeature(Validation.STRICT) && (idRangeOffset & 1) != 0 ) {
                fail(2, "invalid idRangeOffset %#x", headers.getChar(offset + 6));
                continue;
            }
            idRangeOffset /= 2;
            
            // TODO: verify that offsets do not point outside of the table
            glyphStart = Math.min(glyphStart, idRangeOffset);
            glyphEnd   = Math.max(glyphEnd,   idRangeOffset + lastCode - firstCode + 1);

            final LookupRange range;
            if ( offset == 0 ) {
                range = header0 = new LookupRange(firstCode, lastCode, idDelta, idRangeOffset);
                if ( hi < header0.start || hi > header0.end ) {
                    if ( hasFeature(Validation.STRICT) ) {
                        fail(2, "byte character %#x outside 8 bit range %s", hi, header0);
                    } else {
                        warn(2, "byte character %#x outside 8 bit range %s", hi, header0);
                    }
                }
            } else {
                range = new LookupRange(hi << 8 | firstCode, hi << 8 | lastCode, idDelta, idRangeOffset);
            }

            ranges.add(range);
        }
        
        data.seek(streamOffset + glyphStart * 2);
        final IntUnaryOperator glyphs = CodeRanges.chars(data.read((glyphEnd - glyphStart) * 2));
        for ( Iterator<LookupRange> it = ranges.iterator(); it.hasNext(); ) {
            LookupRange r = it.next();
            r.setTable(glyphs);
            r.setOffset(r.getOffset() - glyphStart);
            if ( verifyGlyphs(2, r, numGlyphs) ) it.remove();
        }
        
        return new CodeRanges(ranges);
    }


    /**
     * Parses a
     * <a href="https://learn.microsoft.com/en-us/typography/opentype/spec/cmap#format-4-segment-mapping-to-delta-values">
     * Format 4: Segment mapping to delta values</a>
     * subtable.
     * 
     * @param numGlyphs number of glyphs to be read
     * @param data The stream to read the data from.
     * @throws IOException If there is an error reading the data.
     */
    public static CodeRanges parseFormat4(int numGlyphs, TTFDataStream data, long streamOffset) throws IOException
    {
        data.seek(streamOffset + 6);
        
        int raw = data.readUnsignedShort();
        if ( hasFeature(Validation.STRICT) && (raw & 1) != 0 ) {
            throw new IOException(message(4, "invalid segCountX2 %#x", raw));
        }
        final int segCountX2 = raw & ~1;
        if ( segCountX2 == 0 ) return CodeRanges.EMPTY;

        data.seek(streamOffset + 14);
        byte[] headers = data.read(segCountX2 * 4 + 2);
        CharBuffer endCodes   = ByteBuffer.wrap(headers, segCountX2 * 0 + 0, segCountX2).asCharBuffer();
        CharBuffer startCodes = ByteBuffer.wrap(headers, segCountX2 * 1 + 2, segCountX2).asCharBuffer();
        ShortBuffer idDeltas  = ByteBuffer.wrap(headers, segCountX2 * 2 + 2, segCountX2).asShortBuffer();
        CharBuffer idROffsets = ByteBuffer.wrap(headers, segCountX2 * 3 + 2, segCountX2).asCharBuffer();

        
        final List<Range> ranges = new ArrayList<>(segCountX2 / 2);
        int glyphStart = Integer.MAX_VALUE, glyphEnd = 0;
        for ( int count = segCountX2 / 2; count > 0; count-- ) {
            final int startCode = startCodes.get();
            final int endCode   = endCodes.get();
            final int idDelta   = idDeltas.get();
            int       idROffset = idROffsets.get();

            if ( hasFeature(Validation.STRICT) && (idROffset & 1) != 0 ) {
                fail(2, "invalid idRangeOffset %#x", idROffset);
                continue;
            }
            idROffset &= ~1;
            
            
            if ( verify16(4, startCode, endCode, endCode == 0xffff && startCode != 0xffff) ) {
                continue;
            }

            // Note: Docs incorrectly say "idDelta value is added directly to the character code offset",
            // but one has to use the code, not the offset, as demonstrated in the example: "idDelta[i] + c"
            // For table lookups the start code character offset is used, though.
            if ( idROffset==0 ) {
                if ( endCode == 0xffff && idDelta == 1 ) continue;
                ranges.add(new DeltaRange(startCode, endCode, 0xffff, startCode + idDelta));
            } else {
                // TODO: verify that offsets do not point outside of the table
                // TODO: exception: if end range, simply ignore
                if ( endCode == 0xffff ) continue; // as long as we are missing the check, ignore mapping
                int offset = 14 + segCountX2 * 3 + 2 + (idROffsets.position() - 1) * 2 + idROffset;
                glyphStart = Math.min(glyphStart, offset);
                glyphEnd   = Math.max(glyphEnd,   offset + (endCode - startCode + 1) * 2);
                ranges.add(new LookupRange(startCode, endCode, idDelta, offset));
            }
        }
        
        if ( glyphEnd > 0 ) {
            data.seek(streamOffset + glyphStart);
            final IntUnaryOperator glyphs = CodeRanges.chars(data.read(glyphEnd - glyphStart));
            for ( Iterator<Range> it = ranges.iterator(); it.hasNext(); ) {
                Range r = it.next();
                if ( r instanceof LookupRange ) {
                    LookupRange lr = (LookupRange)r;
                    lr.setTable(glyphs);
                    lr.setOffset((lr.getOffset() - glyphStart) / 2);
                }
                if ( verifyGlyphs(4, r, numGlyphs) ) it.remove();
            }
        }
        
        return new CodeRanges(ranges);
    }
    
}
