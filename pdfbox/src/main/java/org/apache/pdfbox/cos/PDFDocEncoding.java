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

package org.apache.pdfbox.cos;

import java.util.Arrays;

import org.apache.fontbox.util.primitive.Int2IntHashMap;

/**
 * The "PDFDocEncoding" encoding. Note that this is *not* a Type 1 font encoding, it is used only
 * within PDF "text strings".
 */
final class PDFDocEncoding
{
    static final char REPLACEMENT_CHARACTER = '\uFFFD';
    static final char[] CODE_TO_UNI = new char[256];
    static final Int2IntHashMap UNI_TO_CODE = new Int2IntHashMap(40);

    static
    {
        Arrays.fill(CODE_TO_UNI, '\uFFFF');
        
        // deviations (based on the table in ISO 32000-1:2008)
        // block 1
        CODE_TO_UNI[0x18] = '\u02D8'; // BREVE
        CODE_TO_UNI[0x19] = '\u02C7'; // CARON
        CODE_TO_UNI[0x1A] = '\u02C6'; // MODIFIER LETTER CIRCUMFLEX ACCENT
        CODE_TO_UNI[0x1B] = '\u02D9'; // DOT ABOVE
        CODE_TO_UNI[0x1C] = '\u02DD'; // DOUBLE ACUTE ACCENT
        CODE_TO_UNI[0x1D] = '\u02DB'; // OGONEK
        CODE_TO_UNI[0x1E] = '\u02DA'; // RING ABOVE
        CODE_TO_UNI[0x1F] = '\u02DC'; // SMALL TILDE
        // block 2
        CODE_TO_UNI[0x7F] = REPLACEMENT_CHARACTER; // undefined
        CODE_TO_UNI[0x80] = '\u2022'; // BULLET
        CODE_TO_UNI[0x81] = '\u2020'; // DAGGER
        CODE_TO_UNI[0x82] = '\u2021'; // DOUBLE DAGGER
        CODE_TO_UNI[0x83] = '\u2026'; // HORIZONTAL ELLIPSIS
        CODE_TO_UNI[0x84] = '\u2014'; // EM DASH
        CODE_TO_UNI[0x85] = '\u2013'; // EN DASH
        CODE_TO_UNI[0x86] = '\u0192'; // LATIN SMALL LETTER SCRIPT F
        CODE_TO_UNI[0x87] = '\u2044'; // FRACTION SLASH (solidus)
        CODE_TO_UNI[0x88] = '\u2039'; // SINGLE LEFT-POINTING ANGLE QUOTATION MARK
        CODE_TO_UNI[0x89] = '\u203A'; // SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
        CODE_TO_UNI[0x8A] = '\u2212'; // MINUS SIGN
        CODE_TO_UNI[0x8B] = '\u2030'; // PER MILLE SIGN
        CODE_TO_UNI[0x8C] = '\u201E'; // DOUBLE LOW-9 QUOTATION MARK (quotedblbase)
        CODE_TO_UNI[0x8D] = '\u201C'; // LEFT DOUBLE QUOTATION MARK (quotedblleft)
        CODE_TO_UNI[0x8E] = '\u201D'; // RIGHT DOUBLE QUOTATION MARK (quotedblright)
        CODE_TO_UNI[0x8F] = '\u2018'; // LEFT SINGLE QUOTATION MARK (quoteleft)
        CODE_TO_UNI[0x90] = '\u2019'; // RIGHT SINGLE QUOTATION MARK (quoteright)
        CODE_TO_UNI[0x91] = '\u201A'; // SINGLE LOW-9 QUOTATION MARK (quotesinglbase)
        CODE_TO_UNI[0x92] = '\u2122'; // TRADE MARK SIGN
        CODE_TO_UNI[0x93] = '\uFB01'; // LATIN SMALL LIGATURE FI
        CODE_TO_UNI[0x94] = '\uFB02'; // LATIN SMALL LIGATURE FL
        CODE_TO_UNI[0x95] = '\u0141'; // LATIN CAPITAL LETTER L WITH STROKE
        CODE_TO_UNI[0x96] = '\u0152'; // LATIN CAPITAL LIGATURE OE
        CODE_TO_UNI[0x97] = '\u0160'; // LATIN CAPITAL LETTER S WITH CARON
        CODE_TO_UNI[0x98] = '\u0178'; // LATIN CAPITAL LETTER Y WITH DIAERESIS
        CODE_TO_UNI[0x99] = '\u017D'; // LATIN CAPITAL LETTER Z WITH CARON
        CODE_TO_UNI[0x9A] = '\u0131'; // LATIN SMALL LETTER DOTLESS I
        CODE_TO_UNI[0x9B] = '\u0142'; // LATIN SMALL LETTER L WITH STROKE
        CODE_TO_UNI[0x9C] = '\u0153'; // LATIN SMALL LIGATURE OE
        CODE_TO_UNI[0x9D] = '\u0161'; // LATIN SMALL LETTER S WITH CARON
        CODE_TO_UNI[0x9E] = '\u017E'; // LATIN SMALL LETTER Z WITH CARON
        CODE_TO_UNI[0x9F] = REPLACEMENT_CHARACTER; // undefined
        CODE_TO_UNI[0xA0] = '\u20AC'; // EURO SIGN
        // "block 3"
        CODE_TO_UNI[0xAD] = REPLACEMENT_CHARACTER; // undefined
        // end of deviations

        // initialize with basically ISO-8859-1
        for (int code = 0; code < 256; code++) {
            char ch = CODE_TO_UNI[code];
            if ( ch == 0xffff ) {
                CODE_TO_UNI[code] = (char)code;
            }
            else if ( ch != REPLACEMENT_CHARACTER ) {
                UNI_TO_CODE.put(ch, code);
            }
        }
    }
    
    private PDFDocEncoding()
    {
    }

    /**
     * Returns the string representation of the given PDFDocEncoded bytes.
     */
    public static String toString(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder(bytes.length);
        for (byte b : bytes)
        {
            sb.append(CODE_TO_UNI[b & 0xff]);
        }
        return sb.toString();
    }

    /**
     * Returns the given string encoded with PDFDocEncoding.
     */
    public static byte[] getBytes(String text)
    {
        byte[] bytes = new byte[text.length()];
        for ( int i = 0; i < bytes.length; i++ )
        {
            bytes[i] = (byte)Math.max(0, getCode(text.charAt(i)));
        }
        return bytes;
    }

    /**
     * Returns true if the given character is available in PDFDocEncoding.
     *
     * @param character UTF-16 character
     */
    public static boolean containsChar(char character)
    {
        return getCode(character) != -1;
    }

    /**
     * Returns the code for the character or {@code -1} if not PDFDocEncoding character.
     */
    private static int getCode(char ch)
    {
        // replacement mapping is wrong, but keeps compatibility with older versions
        return ch < 256 && CODE_TO_UNI[ch] == ch ? ch
            : ch == REPLACEMENT_CHARACTER ? 0x9F
                : UNI_TO_CODE.getOrDefault(ch, -1);
    }
    
}
