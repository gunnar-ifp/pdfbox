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

import org.apache.fontbox.util.Bytes;

/**
 * Range of continuous unicode characters between a uint16 CID range.
 * <p>
 * Support range extension: range max is at most 65536, less if overflow of last character happens.
 * 
 * @author Gunnar Brand
 */
class BasefontRange
{
    private final int codeLength, utfLength;
    private final int codeStart;
    private int size;
    private final char[] chars;
    private final boolean valid;


    /**
     * @param cidStart start CID
     * @param cidEnd end CID
     * @param utf16be The UTF16BE string. Will be copied.
     */
    public BasefontRange(int cidStart, int cidEnd, int cidLength, byte[] utf16be, final int utfLength)
    {
        if ( cidLength > 2 ) throw new IllegalArgumentException("CID bigger uint16");
        
        this.codeLength = cidLength;
        this.utfLength = utfLength;
        this.codeStart = cidStart;
        // discard any strings that would result in last byte overflow, see PDF 9.10.3, example 2
        // also see PDFBOX-4661
        this.size = Math.min(0x100 - (utf16be[utf16be.length - 1] & 0xff), cidEnd - cidStart + 1);
        
        if ( utfLength == 0) {
            this.chars = new char[0];
            this.valid = false;
        }
        else if ( utfLength == 1 ) {
            this.chars = new char[] { (char)utf16be[0] }; // assume latin-1
            this.valid = true;
        }
        else  {
            this.chars = new char[(utfLength + 1) / 2];
            this.valid = utfLength % 2 == 0;
            for ( int i = 0, e = utfLength / 2; i < e; i++ ) {
                this.chars[i] = Bytes.getChar(utf16be, i * 2, 2);
            }
            if ( !valid ) this.chars[chars.length - 1] = '?';
        }
    }

    
    public int codeLength()
    {
        return codeLength;
    }

    
    public int utfLength()
    {
        return utfLength;
    }
    

    /**
     * Check if the given values represent a consecutive range of the given range.
     * If so, extend the given range instead of creating a new one.
     * 
     * @param cidStart start CID of the new range
     * @param cidEnd end CID of the new range
     * @param utf16be The UTF16BE string. Will be copied.
     * @return true if the given range was extended
     */
    public boolean extend(int cidStart, int cidEnd, int cidLength, byte[] utf16be, final int utf16Length)
    {
        if ( codeLength != cidLength || this.utfLength != utf16Length || codeStart + size != cidStart ) return false;
        
        // input ranges can only ever span changes in the last byte of utf16be w/o overflow.
        // we can we extend this to changes in the last char w/o overflow
        // Note: for valid strings, getChar() returns last char in bytes
        if ( valid && Bytes.getChar(utf16be, 0, utf16Length) != chars[chars.length - 1] + size  ) return false;
        
        // check if static characters match
        for ( int i = 0, e = chars.length - 1; i < e; i++ ) {
            if ( chars[i] != Bytes.getChar(utf16be, i * 2, 2) ) return false;
        }

        // use smaller delta, i.e. if last utf16 byte overflows, only use its range  
        int cidsize = cidEnd - cidStart + 1;
        if ( valid ) cidsize = Math.min(cidsize, 0x100 - (utf16be[utf16be.length - 1] & 0xff));
        this.size += cidsize;
        return true;
    }


    public boolean matches(int cid)
    {
        final int delta = cid - codeStart;
        return delta >= 0 && delta < size;
    }

    
    /**
     * Maps the given CID to the corresponding string in this range .
     *
     * @return corresponding string, or {@code null} if the CID is out of range.
     */
    public String map(int cid)
    {
        final int delta = cid - codeStart;
        if ( delta < 0 || delta >= size ) return null;

        char[] c = chars;
        if ( valid ) {
            if ( chars.length == 1 ) return Bytes.toString(      (char)(c[0] + delta));
            if ( chars.length == 2 ) return Bytes.toString(c[0], (char)(c[1] + delta));
            if ( delta > 0 ) {
                c = chars.clone();
                c[c.length - 1] += delta;
            }
        }
        return Bytes.toString(c);
    }
    

    /**
     * Maps the given UTF16 String to the corresponding CID in this range.
     *
     * @return corresponding CID, or {@code -1} if the string is out of range.
     */
    public int toCid(String utf)
    {
        if ( utf.length() != chars.length ) return -1;
        
        int delta = utf.charAt(utf.length() - 1) - chars[chars.length - 1];
        if ( delta < 0 || delta >= size || delta != 0 && !valid  ) return -1;
        
        for ( int i = 0, e = chars.length - 1; i < e; i++ ) {
            if ( chars[i] != utf.charAt(i) ) return -1;
        }
        
        return codeStart + delta;
    }
    
    
    /**
     * Maps the given UTF16 String to the corresponding CID in this range.
     *
     * @return corresponding CID bytes, or {@code null} if the string is out of range.
     */
    public byte[] toCode(String utf)
    {
        int cid = toCid(utf);
        return cid == -1 ? null : codeLength == 1 ? Bytes.ofByte(cid) : Bytes.ofChar(cid);
    }

}
