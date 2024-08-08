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
package org.apache.fontbox.cmap;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.fontbox.util.Bytes;
import org.apache.fontbox.util.primitive.Int2IntHashMap;
import org.apache.fontbox.util.primitive.Int2ObjectHashMap;

/**
 * This class represents a CMap file.
 *
 * @author Ben Litchfield
 */
public class CMap
{
    private static final Log LOG = LogFactory.getLog(CMap.class);

    private int wmode = 0;
    private String cmapName = null;
    private String cmapVersion = null;
    private int cmapType = -1;

    private String registry = null;
    private String ordering = null;
    private int supplement = 0;

    private CMap parent;
    
    // codespace ranges (up to 4 bytes, i.e. 4 dimensions), must not not overlap
    @SuppressWarnings("unchecked")
    private final List<CodespaceRange>[] codespaceRanges = new List[4];
    private int codespaceRangesMin = 4;
    private int codespaceRangesMax = 0;

    // CMAP Type 1: Character Code to Character selector (CID)
    // code length as defined in codespaces to an integer (max 16 bit) CID
    // codespace length can go from 1 to 4 bytes, so 4 different ranges lists are required.
    // CIDs are always 16 bit integers. CMAPs cannot define any limits for CIDs. 
    // TODO: A notdef mapping, defined using beginnotdefchar, endnotdefchar, beginnotdefrange, and endnotdefrange
    // TODO: shall be used if the normal mapping produces a CID for which no glyph is present in the associated CIDFont.
    // TODO: Ed: This must actually be detected by the user of this cmap, and then this cmap be queried,
    // TODO: not the cmap doing an auto substitution (though it sounds sensible to do so?)
    @SuppressWarnings("unchecked")
    private final List<CIDRange>[] cidRanges = new List[4];
    private int cidRangesMin = 4;
    private int cidRangesMax = 0;
    /** Maps individual character codes to cids.
     * TODO: should be long -> int and marker bits for code length or different maps per length.
     */
    private final Int2IntHashMap cidChar = new Int2IntHashMap().defaultReturnValue(-1);

    
    // CMAP Type 2: Character Code to Unicode UTF16BE String (of at most 512 bytes)
    // | The CMap file shall contain begincodespacerange and endcodespacerange operators that are
    // | consistent with the encoding that the font uses.
    // | In particular, for a simple font, the codespace shall be one byte long.
    // Since code to cid mapping results in an integer, for other font types gids / cids are usually integers,
    // a readCode() equivalent doesn't happen for cid to unicode mapping. Due to faulty cmap files,
    // toUnicode() should be supplied the length of the cid/gid in bytes (1 or 2) instead.
    // If missing a length, lookup should happen 1-byte to 2-byte.
    // For proper reverse mapping we'd need to keep the orignal cid byte[] length as defined in the cmap file.
    //
    // Note: toUnicode CMaps are still proper cmaps, so in theory partial matching should happen, i.e.
    // if a CID > 255 doesn't match any fbrange/fbchar, and it doesn't even match a codespace,
    // the first byte should be used instead (if it matches a range/char and codespace).
    // Also note that the unmappable CIDs should map to 0xFFFD, but we don't do that.
    @SuppressWarnings("unchecked")
    private final List<BasefontRange>[] basefontRanges = new List[2];
    private int basefontRangesMin = 2;
    private int basefontRangesMax = 0;
    /**
     * Maps a CID to a String. bit 16 will be set if cid was defined with 2 bytes.
     */
    private Int2ObjectHashMap<String> basefontChar = new Int2ObjectHashMap<>();
    /**
     * inverted map (keeps the shortest byte[] for duplicate strings), utf16 -> CID bytes.
     * TODO: map string to int and look up byte[] on demand
     */
    private Map<String, byte[]> basefontCharReverse = new HashMap<>();

    // Note: Later definitions overwrite earlier ones, so matching over a List<*Range> must happen with a reverse iterator!
    // Note: Reverse mapping must / should do the same.
    // TODO: Individual char mappings are prioritized higher ignoring their order.
    // TODO: Since we have no length information for either codes nor CIDs, it doesn't work 100%.
    
    private static final String SPACE = " ";
    private int spaceMapping = -1;

    
    /**
     * Creates a new instance of CMap.
     */
    CMap()
    {
    }

    
    /**
     * Returns true if there are any CID mappings, false otherwise.
     */
    public boolean hasCIDMappings()
    {
        return !cidChar.isEmpty() || cidRangesMax != 0 || parent != null && parent.hasCIDMappings();
    }

    
    /**
     * Returns true if there are any Unicode mappings, false otherwise.
     */
    public boolean hasUnicodeMappings()
    {
        return !basefontChar.isEmpty() || basefontRangesMax != 0 || parent != null && parent.hasUnicodeMappings();
    }

    
    /**
     * Reads a character code from a string in the content stream.
     * <p>
     * See "CMap Mapping" and "Handling Undefined Characters" in PDF32000 for more details.
     *
     * @param in string stream
     * @return character code
     * @throws IOException if there was an error reading the stream or CMap
     */
    public int readCode(InputStream in) throws IOException
    {
        int b0 = in.read(), b1 = 0, b2 = 0, b3 = 0;
        int idx = codespaceRangesMin - 1;
        if ( idx > 0 ) {
            b1 = in.read();
            if ( idx > 1 ) {
                b2 = in.read();
                if ( idx > 2 ) b3 = in.read();
            }
        }
        if ( (b0 | b1 | b2 | b3) < 0 ) throw new EOFException();
        in.mark(codespaceRangesMax - codespaceRangesMin);

        switch (idx) {
            case 0:
                for ( CodespaceRange r : codespaceRanges[0] ) {
                    if ( r.matches(b0) ) return b0;
                }
                if ( ++idx == codespaceRangesMax || (b1 = in.read()) == -1 ) break;
            case 1:
                if ( codespaceRanges[1] != null ) {
                    for ( CodespaceRange r : codespaceRanges[1] ) {
                        if ( r.matches(b0, b1) ) return Bytes.toInt(b0, b1);
                    }
                }
                if ( ++idx == codespaceRangesMax || (b2 = in.read()) == -1 ) break;
            case 2:
                if ( codespaceRanges[2] != null ) {
                    for ( CodespaceRange r : codespaceRanges[2] ) {
                        if ( r.matches(b0, b1, b2) ) return Bytes.toInt(b0, b1, b2);
                    }
                }
                if ( ++idx == codespaceRangesMax || (b3 = in.read()) == -1 ) break;
            case 3:
                for ( CodespaceRange r : codespaceRanges[3] ) {
                    if ( r.matches(b0, b1, b2, b3) ) return Bytes.toInt(b0, b1, b2, b3);
                }
                idx++;
        }
        
        if (LOG.isWarnEnabled())
        {
            StringBuilder sb = new StringBuilder(128);
            sb.append("Invalid character code sequence ");
            for (int i = 0; i < idx; ++i) sb.append("0x%02X (%04o) ");
            sb.append("in CMap %9$s");
            LOG.warn(String.format(sb.toString(), b0, b0, b1, b1, b2, b2, b3, b3, cmapName));
        }
        
        // PDFBOX-4811 reposition to where we were after initial read
        if (in.markSupported())
        {
            in.reset();
        }
        else
        {
            LOG.warn("mark() and reset() not supported, " + (codespaceRangesMax - codespaceRangesMin)
                + " bytes have been skipped");
        }
        
        // TODO: this is a clear violation of 9.7.3.6 a) and b)
        // TODO: kinda like a) but even if partial matching.
        // TODO: b) requires sorted code space ranges and partial matches and whatnot...
        // TODO: Adobe Reader probably does it right but people only witnessed the result
        // Adobe Reader behavior:
        switch (codespaceRangesMin) {
            default:
            case 1: return b0;
            case 2: return Bytes.toInt(b0, b1);
            case 3: return Bytes.toInt(b0, b1, b2);
            case 4: return Bytes.toInt(b0, b1, b2, b3);
        }
    }

    
    /**
     * Returns the CID for the given character code (if there are code to CID mappings).
     *
     * @param code character code as returned by {@link #readCode(InputStream)}.
     * @return CID 
     */
    public int toCID(int code)
    {
       int cid = cidChar.get(code);
        if ( cid >= 0 ) return cid;
        
        for ( int i = cidRangesMin - 1; i < cidRangesMax; i++ ) {
            List<CIDRange> ranges = cidRanges[i];
            if ( ranges == null ) continue;
            
            for ( int j = ranges.size() - 1; j >= 0; j-- ) {
                int ch = ranges.get(j).map(code);
                if ( ch != -1 ) return ch;
            }
        }
        
        return parent == null ? 0 : parent.toCID(code);
    }

    
    /**
     * Returns the sequence of Unicode characters for the given CID.
     *
     * @return Unicode characters (may be more than one, e.g "fi" ligature)
     */
    public String toUnicode(int cid)
    {
        String utf = cid < 256 ? basefontChar.get(cid) : basefontChar.get(cid | 0x10000);
        if ( utf != null ) return utf;
        
        if ( cid < 256 ) {
            utf = basefontChar.get(cid | 0x10000);
            if ( utf != null ) return utf;
        }
        
        for ( int i = cid < 256 ? basefontRangesMin - 1 : 1; i < basefontRangesMax; i++ ) {
            List<BasefontRange> ranges = basefontRanges[i];
            if ( ranges == null ) continue;
            
            for ( int j = ranges.size() - 1; j >= 0; j-- ) {
                utf = ranges.get(j).map(cid);
                if ( utf != null ) return utf;
            }
        }
        
        // TODO: for "real cmap codespace" behavior:
        // TODO: if  2 byte cid not matching a 2 byte coodespace, use the first byte and repeat call
                
        return parent == null ? null : parent.toUnicode(cid);
    }
    
    
    /**
     * Get the CID bytes for an unicode string.
     * Used for writing into a content stream.
     *
     * @param unicode The unicode string.
     * @return the code bytes or null if there is none.
     */
    public byte[] getCodesFromUnicode(String unicode)
    {
        byte[] bytes = basefontCharReverse.get(unicode);
        if ( bytes != null ) return bytes;
        
        for ( int i = basefontRangesMin - 1; i < basefontRangesMax; i++ ) {
            List<BasefontRange> ranges = basefontRanges[i];
            if ( ranges == null ) continue;
            
            for ( int j = ranges.size() - 1; j >= 0; j-- ) {
                bytes = ranges.get(j).toCode(unicode);
                if ( bytes != null ) return bytes;
            }
        }
        
        return parent == null ? null : parent.getCodesFromUnicode(unicode);
    }


    /**
     * Adds a codespace range.
     *
     * @param range A single codespace range.
     */
    void addCodespaceRange( CodespaceRange range )
    {
        getRangesList(codespaceRanges, range.length()).add(range);
        codespaceRangesMin = Math.min(codespaceRangesMin, range.length());
        codespaceRangesMax = Math.max(codespaceRangesMax, range.length());
    }
    
    
    /**
     * Adds an individual code to CID mapping.
     *
     * @param code character code (1 - 4 bytes)
     * @param cid CID, only lower 16 bytes should be set
     */
    void addCIDMapping(byte[] code, int cid)
    {
        cidChar.put(Bytes.getUnsigned(code), cid);
    }

    
    /**
     * Adds a code to CID Range.
     *
     * @param startCode starting character code of the CID range.
     * @param endCode ending character code of the CID range.
     * @param cid the cid to be started with, always 16 bit.
     */
    void addCIDRange(byte[] startCode, byte[] endCode, int cid)
    {
        final int length = startCode.length;
        final List<CIDRange> ranges = getRangesList(cidRanges, length);
        final int start = Bytes.getUnsigned(startCode), end = Bytes.getUnsigned(endCode);
        if ( ranges.size() == 0 || !ranges.get(ranges.size() - 1).extend(start, end, cid, length) ) {
            ranges.add(new CIDRange(start, end, cid, length));
            cidRangesMin = Math.min(cidRangesMin, length);
            cidRangesMax = Math.max(cidRangesMax, length);
        }
    }

    
    /**
     * Adds a CID to Unicode mapping.
     *
     * @param code The CID code to map from. <b>Important: The byte[] instance is kept and must not be modified!</b>
     * @param unicode The Unicode characters to map to.
     */
    void addBasefontMapping(byte[] code, String utf16)
    {
        // the problem:
        // <00> <40>
        // <00> <41>
        // -> cid 0 will return <41>, but both <40> and <41> will return 0, even though <40> is undefined!
        // thus return value of basefontChar.put() must be removed from reverse map if basefontCharReverse.get(old)==cid bytes
        // TODO: the same problem exists for fbranges when iterating (and not easily fixable: ranges must be  split while adding
        // or made into binary lookup trees), so maybe this is a not really an issue and should be ignored.
        
        int cid = Bytes.getUnsigned(code);
        String oldUtf = basefontChar.put(cid | (code.length == 1 ? 0 : 0x10000), utf16);
        if ( oldUtf != null && !oldUtf.equals(utf16) ) {
            basefontCharReverse.merge(oldUtf, code, (c1, c2) -> Arrays.equals(c1, c2) ? null : c1);
        }
        basefontCharReverse.merge(utf16, code, (c1, c2) -> c1.length < c2.length ? c1 : c2);
                
        // fixme: ugly little hack
        if ( SPACE.equals(utf16) ) spaceMapping = cid;
    }
    
    
    void addBasefontRangeOldOOME(byte[] startCode, byte[] endCode, byte[] utf16)
    {
        final int start = Bytes.getUnsigned(startCode), end = Bytes.getUnsigned(endCode);
        for ( int i = 0, e = end - start + 1; i < e; i++ ) {
            if ( i > 0 ) {
                if ( Bytes.increment(utf16) < utf16.length - 1 ) break;
                Bytes.increment(startCode);
            }
            addBasefontMapping(Bytes.copy(startCode), Bytes.toString(utf16));
        }
    }
    
    
    /**
     * Adds a CID to utf string range.
     *
     * @param startCode starting CID code.
     * @param endCode ending CID code.
     * @param utf16 The UTF16BE string.
     */
    void addBasefontRange(byte[] startCode, byte[] endCode, byte[] utf16)
    {
        final int length = startCode.length;
        final List<BasefontRange> ranges = getRangesList(basefontRanges, length);
        final int start = Bytes.getUnsigned(startCode), end = Bytes.getUnsigned(endCode);
        
        if ( start == end ) {
            addBasefontMapping(Bytes.copy(startCode), Bytes.toString(utf16));
            return;
        }
        
        BasefontRange range = null;
        if ( ranges.size() != 0 ) {
            range = ranges.get(ranges.size() - 1);
            if ( !range.extend(start, end, length, utf16, utf16.length) ) {
                range = null;
            }
        }
        
        if ( range == null ) {
            ranges.add(range = new BasefontRange(start, end, length, utf16, utf16.length));
            basefontRangesMin = Math.min(basefontRangesMin, length);
            basefontRangesMax = Math.max(basefontRangesMax, length);
        }
        
        // fixme: ugly little hack
        int cid = range.toCid(SPACE);
        if ( cid != -1 ) spaceMapping = cid;
    }
    
    
    /**
     * Implementation of the usecmap operator.
     * 
     * @param cmap The cmap to load mappings from.
     */
    void useCmap( CMap cmap )
    {
        this.parent = cmap;
        
        for ( List<CodespaceRange> ranges : cmap.codespaceRanges ) {
            if ( ranges == null ) continue;
            for ( CodespaceRange range : ranges ) addCodespaceRange(range);
        }
    }

    /**
     * Returns the WMode of a CMap.
     * 0 represents a horizontal and 1 represents a vertical orientation.
     */
    public int getWMode() 
    {
        return wmode;
    }

    /**
     * Sets the WMode of a CMap.
     * 
     * @param newWMode the new WMode.
     */
    public void setWMode(int newWMode) 
    {
        wmode = newWMode;
    }

    /**
     * Returns the name of the CMap.
     */
    public String getName() 
    {
        return cmapName;
    }

    /**
     * Sets the name of the CMap.
     * 
     * @param name the CMap name.
     */
    public void setName(String name) 
    {
        cmapName = name;
    }

    /**
     * Returns the version of the CMap.
     */
    public String getVersion() 
    {
        return cmapVersion;
    }

    /**
     * Sets the version of the CMap.
     * 
     * @param version the CMap version.
     */
    public void setVersion(String version) 
    {
        cmapVersion = version;
    }

    /**
     * Returns the type of the CMap.
     */
    public int getType() 
    {
        return cmapType;
    }

    /**
     * Sets the type of the CMap.
     * 
     * @param type the CMap type.
     */
    public void setType(int type) 
    {
        cmapType = type;
    }

    /**
     * Returns the registry of the CIDSystemInfo.
     */
    public String getRegistry() 
    {
        return registry;
    }

    /**
     * Sets the registry of the CIDSystemInfo.
     * 
     * @param newRegistry the registry.
     */
    public void setRegistry(String newRegistry) 
    {
        registry = newRegistry;
    }

    /**
     * Returns the ordering of the CIDSystemInfo.
     */
    public String getOrdering() 
    {
        return ordering;
    }

    /**
     * Sets the ordering of the CIDSystemInfo.
     * 
     * @param newOrdering the ordering.
     */
    public void setOrdering(String newOrdering) 
    {
        ordering = newOrdering;
    }

    /**
     * Returns the supplement of the CIDSystemInfo.
     */
    public int getSupplement() 
    {
        return supplement;
    }

    /**
     * Sets the supplement of the CIDSystemInfo.
     * 
     * @param newSupplement the supplement.
     */
    public void setSupplement(int newSupplement) 
    {
        supplement = newSupplement;
    }
    
    /** 
     * Returns the mapping for the space character.
     */
    public int getSpaceMapping()
    {
        return spaceMapping != -1 || parent == null ? spaceMapping : parent.getSpaceMapping();
    }

    @Override
    public String toString()
    {
        return cmapName;
    }
    

    private static <T> List<T> getRangesList(List<T>[] lists, int codeLength)
    {
        List<T> list = lists[codeLength - 1];
        return list == null ? lists[codeLength - 1] = new ArrayList<>() : list;
    }

}
