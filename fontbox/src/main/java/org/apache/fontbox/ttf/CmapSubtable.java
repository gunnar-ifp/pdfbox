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

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.fontbox.ttf.CodeRanges.Range;
import org.apache.fontbox.util.primitive.Int2IntHashMap;
import org.apache.fontbox.util.primitive.IntArrayList;

/**
 * A "cmap" subtable.
 * 
 * @author Ben Litchfield
 * @author Gunnar Brand
 */
public class CmapSubtable implements CmapLookup
{
    private static final Log LOG = LogFactory.getLog(CmapSubtable.class);

    enum Validation {
        /** Adjust values if possible to make ranges work. */
        ADJUST,
        /** Enables additional strict checks. Doesn't stop {@link #ADJUST} from working. */
        STRICT,
        /** Verifies that all glyphs of a range are inside [0; numGlyphs). */
        GLYPHS,
        /** Skips invalid ranges if above checks fail. */
        SKIP
    }
    
    private static final EnumSet<Validation> VALIDATION = EnumSet.of(Validation.ADJUST, Validation.SKIP);
    
    
    private static final int FLAGS  = 0b11 << 30;
    private static final int VALUE  = ~FLAGS;
    private static final int LIST   = 0b10 << 30;
    private static final int SINGLE = 0b11 << 30;
    
    
    private int platformId;
    private int platformEncodingId;
    private long subTableOffset;
    private int maxGid;
    private CodeRanges codeRanges;
    private ReadWriteLock lock;
    /**
     * Contains mappigns from glyph IDs to char codes, either the only char code (>=0)
     * or char code / index into {@link #multipleCodes} encoded with {@link #SINGLE} or {@link #LIST}.
     */
    // Note: if codes are not limited to 21 bit codepoints, then flags can be added to gids and multiple lookups done into this map.
    private Int2IntHashMap glyphIds;
    private List<int[]> multipleCodes;
    private BitSet missing;

    /**
     * This will read the required data from the stream.
     * 
     * @param data The stream to read the data from.
     * @throws IOException If there is an error reading the data.
     */
    void initData(TTFDataStream data) throws IOException
    {
        platformId = data.readUnsignedShort();
        platformEncodingId = data.readUnsignedShort();
        subTableOffset = data.readUnsignedInt();
    }

    /**
     * This will read the required data from the stream.
     * 
     * @param cmap the CMAP this encoding belongs to.
     * @param numGlyphs number of glyphs.
     * @param data The stream to read the data from.
     * @throws IOException If there is an error reading the data.
     */
    void initSubtable(CmapTable cmap, int numGlyphs, TTFDataStream data) throws IOException
    {
        final long offset = cmap.getOffset() + subTableOffset;
        data.seek(offset);
        final int format = data.readUnsignedShort();
        
        final CodeRanges ranges;
        switch (format)
        {
            case 0:
                ranges = CmapSubtableDirectTableFormats.parseFormat0(numGlyphs, data, offset);
                break;
                
            case 2:
                ranges = CmapSubtableOffsetTableFormats.parseFormat2(numGlyphs, data, offset);
                break;
                
            case 4:
                ranges = CmapSubtableOffsetTableFormats.parseFormat4(numGlyphs, data, offset);
                break;
                
            case 6:
                ranges = CmapSubtableDirectTableFormats.parseFormat6(numGlyphs, data, offset);
                break;
                
            case 8:
                ranges = CmapSubtableDeltaFormats.parseFormat8(numGlyphs, data, offset);
                break;
                
            case 10:
                ranges = CmapSubtableDirectTableFormats.parseFormat10(numGlyphs, data, offset);
                break;
                
            case 12:
                ranges = CmapSubtableDeltaFormats.parseFormat12(numGlyphs, data, offset);
                break;
                
            case 13:
                ranges = CmapSubtableDeltaFormats.parseFormat13(numGlyphs, data, offset);
                break;
                
            case 14:
                // Unicode Variation Sequences (UVS)
                // see http://blogs.adobe.com/CCJKType/2013/05/opentype-cmap-table-ramblings.html
                LOG.warn("Format 14 cmap table is not supported and will be ignored");
                ranges = CodeRanges.EMPTY;
                break;
                
            default:
                throw new IOException("Unknown cmap format:" + format);
        }

        if ( ranges.isEmpty() && format != 14 ) {
//            System.out.format("%02d: %5d, %04x - %04x -> %04x - %04x\n", format, ranges.size(),
//            ranges.minCode(), ranges.maxCode(), ranges.minGid(), ranges.maxGid());
            if ( numGlyphs == 0 ) {
                LOG.warn(String.format("cmap format %d: no glyphs", format));
            }
            else if ( ranges.isEmpty() ) {
                LOG.warn(String.format("cmap format %d: empty ranges", format));
            }
        }
        
        
        Range last = null;
        for ( Range r : ranges ) {
            if ( last != null && last.intersects(r) ) {
                if ( hasFeature(Validation.STRICT) ) {
                    fail(format, "range %s intersects range %s", last, r);
                } else {
                    warn(format, "range %s intersects range %s", last, r);
                }
                break;
            }
            last = r;
        }
        
        this.maxGid = numGlyphs - 1;
        this.codeRanges = ranges;
        this.lock = ranges.isEmpty() || ranges.hasFastCodeLookup() ? null : new ReentrantReadWriteLock();
    }
    

    /**
     * @return Returns the platformEncodingId.
     */
    public int getPlatformEncodingId()
    {
        return platformEncodingId;
    }

    /**
     * @param platformEncodingIdValue The platformEncodingId to set.
     */
    public void setPlatformEncodingId(int platformEncodingIdValue)
    {
        platformEncodingId = platformEncodingIdValue;
    }

    /**
     * @return Returns the platformId.
     */
    public int getPlatformId()
    {
        return platformId;
    }

    /**
     * @param platformIdValue The platformId to set.
     */
    public void setPlatformId(int platformIdValue)
    {
        platformId = platformIdValue;
    }

    @Override
    public String toString()
    {
        return "{" + getPlatformId() + " " + getPlatformEncodingId() + "}";
    }


    @Override
    public int getGlyphId(int characterCode)
    {
        int gid = codeRanges.toGid(characterCode);
        return gid < 0 || gid > maxGid ? 0 : gid;
    }
    
    
    /**
     * Returns the character code for the given GID, or null if there is none.
     *
     * @param gid glyph id
     * @return character code
     * 
     * @deprecated the mapping may be ambiguous, see {@link #getCharCodes(int)}.
     * The first mapped value is returned by default.
     */
    @Deprecated
    public Integer getCharacterCode(int gid)
    {
        return getFirstCharCode(gid);
    }
    
    
    // The following two methods share a lot of the code, this is due to read locking necessary to access "multiple".
    @Override
    public int getFirstCharCode(int gid)
    {
        if ( gid < 0 || gid > maxGid ) return -1;
        
        if ( codeRanges.hasFastCodeLookup() ) {
            return codeRanges.toCode(gid);
        }
        
        final int min = codeRanges.minGid(), index = gid - min;
        if ( index < 0 || gid > codeRanges.maxGid() ) return -1;
        
        lock.readLock().lock();
        try {
            if ( glyphIds != null ) {
                int code = glyphIds.get(index);
                if ( code > -1 ) return code;
                if ( code < -1 ) return (code & SINGLE) == SINGLE ? code & VALUE : multipleCodes.get(code & VALUE)[0];
                if ( missing.get(index) ) return -1;
             }
        } finally {
            lock.readLock().unlock();
        }
        
        lock.writeLock().lock();
        try {
            int code = regetCode(index);
            if ( code > -1 ) return code;
            if ( code < -1 ) return (code & SINGLE) == SINGLE ? code & VALUE : multipleCodes.get(code & VALUE)[0];
            if ( missing.get(index) ) return -1;
            
            code = codeRanges.toCode(gid);
            if ( code == -1 ) missing.set(index); else glyphIds.put(index, code);
            return code;
        } finally {
            lock.writeLock().unlock();
        }
    }

    
    @Override
    public List<Integer> getCharCodes(int gid)
    {
        if ( gid < 0 || gid > maxGid ) return null;
        
        // deoptimized code because method rarely used
        
        if ( codeRanges.hasFastCodeLookup() ) {
            int[] codes = codeRanges.toCodes(gid);
            return codes.length == 0 ? null : new IntArrayList(codes);
        }

        final int min = codeRanges.minGid(), index = gid - min;
        if ( index < 0 || gid > codeRanges.maxGid() ) return null;
        
        lock.readLock().lock();
        try {
            if ( glyphIds != null ) {
                int code = glyphIds.get(index);
                if ( code < -1 ) {
                    return (code & SINGLE) == SINGLE
                        ? Collections.singletonList(code & VALUE)
                        : new IntArrayList(multipleCodes.get(code & VALUE));
                }
                if ( code == -1 && missing.get(index) ) return null;
             }
        } finally {
            lock.readLock().unlock();
        }
        
        lock.writeLock().lock();
        try {
            int code = regetCode(index);
            if ( code < -1 ) {
                return (code & SINGLE) == SINGLE
                    ? Collections.singletonList(code & VALUE)
                    : new IntArrayList(multipleCodes.get(code & VALUE));
            }
            if ( code == -1 && missing.get(index) ) return null;
            
            int[] codes = codeRanges.toCodes(gid);
            if ( codes.length == 0 ) {
                missing.set(index);
            }
            else if ( codes.length == 1 ) {
                glyphIds.put(index, codes[0] | SINGLE);
            }
            else {
                glyphIds.put(index, multipleCodes.size() | LIST);
                multipleCodes.add(codes);
            }
            return codes.length == 0 ? null : new IntArrayList(codes);
        } finally {
            lock.writeLock().unlock();
        }
    }

    
    private int regetCode(int index)
    {
        if ( glyphIds == null ) {
            this.glyphIds = new Int2IntHashMap().defaultReturnValue(-1);
            this.multipleCodes = new ArrayList<>();
            this.missing       = new BitSet();
            return -1;
        }
        return glyphIds.get(index);
    }

    
    static boolean hasFeature(Validation feature)
    {
        return VALIDATION.contains(feature);
    }
    
    
    static String message(int format, String message, Object... args)
    {
        return String.format("cmap format " + format + ": " + message, args);
    }
    
    
    static void warn(int format, String message, Object... args) throws IOException
    {
        LOG.warn(message(format, message, args));
    }

    
    static void fail(int format, String message, Object... args) throws IOException
    {
        if ( hasFeature(Validation.SKIP) ) {
            warn(format, message, args);
        } else {
            throw new IOException(message(format, message, args));
        }
    }
    

    static boolean verifyGlyphs(int format, Range range, int numGlyphs) throws IOException
    {
        if ( hasFeature(Validation.GLYPHS) && range.maxGid() >= numGlyphs ) {
            fail(format, "glyph %d >= %d", range.maxGid(), numGlyphs);
            return true;
        }
        return false;
    }
    
    
    static boolean verify16(int format, int startCharCode, int endCharCode, boolean invalidRange) throws IOException
    {
        if ( endCharCode < startCharCode || invalidRange ) {
            fail(format, "invalid range %#x - %#x", startCharCode, endCharCode);
            return true;
        }

        if ( startCharCode < 0 || startCharCode >= 0x10000 ) {
            fail(format, "invalid start character code %#x", startCharCode);
            return true;
        }

        if ( endCharCode < 0 || endCharCode >= 0x10000 ) {
            fail(format, "invalid end character code %#x", endCharCode);
            return true;
        }
        
        return false;
    }

    
    static boolean verify32(int format, int startCharCode, int endCharCode, boolean invalidRange) throws IOException
    {
        // TODO: The jury is still out on the strict surrogate checks
        
        if ( endCharCode < startCharCode || invalidRange
            || startCharCode < Character.MIN_SURROGATE && endCharCode > Character.MAX_SURROGATE )
        {
            fail(format, "invalid range %#x - %#x", startCharCode, endCharCode);
            return true;
        }

        if ( startCharCode < 0 || startCharCode > Character.MAX_CODE_POINT
            || Character.isBmpCodePoint(startCharCode) && Character.isSurrogate((char)startCharCode) )
        {
            fail(format, "invalid start character code %#x", startCharCode);
            return true;
        }

        if ( endCharCode < 0 || endCharCode > Character.MAX_CODE_POINT
            || Character.isBmpCodePoint(endCharCode) && Character.isSurrogate((char)endCharCode) )
        {
            fail(format, "invalid end character code %#x", endCharCode);
            return true;
        }
        
        return false;
    }
    
}
