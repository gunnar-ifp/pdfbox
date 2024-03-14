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
package org.apache.pdfbox.pdmodel.font;

import java.awt.geom.GeneralPath;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.fontbox.cff.Type2CharString;
import org.apache.fontbox.cmap.CMap;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.GlyphData;
import org.apache.fontbox.ttf.OTFParser;
import org.apache.fontbox.ttf.OpenTypeFont;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.fontbox.util.BoundingBox;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.util.Matrix;

/**
 * Type 2 CIDFont (TrueType).
 * 
 * @author Ben Litchfield
 */
public class PDCIDFontType2 extends PDCIDFont
{
    private static final Log LOG = LogFactory.getLog(PDCIDFontType2.class);

    private final TrueTypeFont ttf;
    private final int[] cid2gid;
    private final boolean isEmbedded;
    private final boolean isDamaged;
    private final CmapLookup cmap; // may be null
    private Matrix fontMatrix;
    private BoundingBox fontBBox;
    private final Set<Integer> noMapping = new HashSet<Integer>();

    /**
     * Constructor.
     * 
     * @param fontDictionary The font dictionary according to the PDF specification.
     * @param parent The parent font.
     * @throws IOException
     */
    public PDCIDFontType2(COSDictionary fontDictionary, PDType0Font parent) throws IOException
    {
        this(fontDictionary, parent, null);
    }
    
    /**
     * Constructor.
     * 
     * @param fontDictionary The font dictionary according to the PDF specification.
     * @param parent The parent font.
     * @param trueTypeFont The true type font used to create the parent font
     * @throws IOException
     */
    public PDCIDFontType2(COSDictionary fontDictionary, PDType0Font parent, TrueTypeFont trueTypeFont) throws IOException
    {
        super(fontDictionary, parent);

        PDFontDescriptor fd = getFontDescriptor();
        if (trueTypeFont != null)
        {
            ttf = trueTypeFont;
            isEmbedded = true;
            isDamaged = false;
        }
        else
        {
            boolean fontIsDamaged = false;
            TrueTypeFont ttfFont = null;

            PDStream stream = null;
            if (fd != null)
            {
                stream = fd.getFontFile2();
                if (stream == null)
                {
                    stream = fd.getFontFile3();
                }
                if (stream == null)
                {
                    // Acrobat looks in FontFile too, even though it is not in the spec, see PDFBOX-2599
                    stream = fd.getFontFile();
                }
            }
            if (stream != null)
            {
                try
                {
                    // embedded OTF or TTF
                    OTFParser otfParser = new OTFParser(true);
                    OpenTypeFont otf = otfParser.parse(stream.createInputStream());
                    ttfFont = otf;
    
                    if (otf.isPostScript())
                    {
                        // PDFBOX-3344 contains PostScript outlines instead of TrueType
                        fontIsDamaged = true;
                        LOG.warn("Found CFF/OTF but expected embedded TTF font " + fd.getFontName());
                    }
                }
                catch (IOException e)
                {
                    fontIsDamaged = true;
                    LOG.warn("Could not read embedded OTF for font " + getBaseFont(), e);
                }
            }
            isEmbedded = ttfFont != null;
            isDamaged = fontIsDamaged;
    
            if (ttfFont == null)
            {
                ttfFont = findFontOrSubstitute();
            }
            ttf = ttfFont;
        }
        cmap = ttf.getUnicodeCmapLookup(false);
        cid2gid = readCIDToGIDMap();
    }

    private TrueTypeFont findFontOrSubstitute() throws IOException
    {
        TrueTypeFont ttfFont;

        CIDFontMapping mapping = FontMappers.instance()
                .getCIDFont(getBaseFont(), getFontDescriptor(),
                        getCIDSystemInfo());
        if (mapping.isCIDFont())
        {
            ttfFont = mapping.getFont();
        }
        else
        {
            ttfFont = (TrueTypeFont) mapping.getTrueTypeFont();
            if (ttfFont == null)
            {
                // shouldn't happen?!
                throw new IOException("mapping.getTrueTypeFont() returns null, please report");
            }
        }
        if (mapping.isFallback())
        {
            LOG.warn("Using fallback font " + ttfFont.getName() +
                    " for CID-keyed TrueType font " + getBaseFont());
        }
        return ttfFont;
    }

    @Override
    public Matrix getFontMatrix()
    {
        if (fontMatrix == null)
        {
            // 1000 upem, this is not strictly true
            fontMatrix = new Matrix(0.001f, 0, 0, 0.001f, 0, 0);
        }
        return fontMatrix;
    }

    @Override
    public BoundingBox getBoundingBox() throws IOException
    {
        if (fontBBox == null)
        {
            fontBBox = generateBoundingBox();
        }
        return fontBBox;
    }

    private BoundingBox generateBoundingBox() throws IOException
    {
        if (getFontDescriptor() != null)
        {
            PDRectangle bbox = getFontDescriptor().getFontBoundingBox();
            if (bbox != null &&
                    (Float.compare(bbox.getLowerLeftX(), 0) != 0 || 
                     Float.compare(bbox.getLowerLeftY(), 0) != 0 ||
                     Float.compare(bbox.getUpperRightX(), 0) != 0 ||
                     Float.compare(bbox.getUpperRightY(), 0) != 0))
            {
                return new BoundingBox(bbox.getLowerLeftX(), bbox.getLowerLeftY(),
                                       bbox.getUpperRightX(), bbox.getUpperRightY());
            }
        }
        return ttf.getFontBBox();
    }

    @Override
    public int codeToCID(int code)
    {
        CMap cMap = parent.getCMap();

        // Acrobat allows bad PDFs to use Unicode CMaps here instead of CID CMaps, see PDFBOX-1283
        if (!cMap.hasCIDMappings() && cMap.hasUnicodeMappings())
        {
            String unicode = cMap.toUnicode(code);
            if (unicode != null)
            {
                return unicode.codePointAt(0); // actually: code -> CID
            }
        }

        return cMap.toCID(code);
    }

    /**
     * Returns the GID for the given character code.
     *
     * @param code character code
     * @return GID
     * @throws IOException
     */
    @Override
    public int codeToGID(int code) throws IOException
    {
        if (!isEmbedded)
        {
            // The conforming reader shall select glyphs by translating characters from the
            // encoding specified by the predefined CMap to one of the encodings in the TrueType
            // font's 'cmap' table. The means by which this is accomplished are implementation-
            // dependent.
            // omit the CID2GID mapping if the embedded font is replaced by an external font
            String name = getName();
            if (cid2gid != null && !isDamaged && name != null && name.equals(ttf.getName()))
            {
                // Acrobat allows non-embedded GIDs - todo: can we find a test PDF for this?
                // PDFBOX-5612: should happen only if it's really the same font
                // this is not perfect, we may have to improve this because some identical fonts
                // have different names
                LOG.warn("Using non-embedded GIDs in font " + getName());
                int cid = codeToCID(code);
                if (cid < cid2gid.length)
                {
                    return cid2gid[cid];
                }
                else
                {
                    return 0;
                }
            }
            else
            {
                // fallback to the ToUnicode CMap, test with PDFBOX-1422 and PDFBOX-2560
                String unicode = parent.toUnicode(code);
                if (unicode == null)
                {
                    if (!noMapping.contains(code))
                    {
                        // we keep track of which warnings have been issued, so we don't log multiple times
                        noMapping.add(code);
                        LOG.warn("Failed to find a character mapping for " + code + " in " + getName());
                    }
                    // Acrobat is willing to use the CID as a GID, even when the font isn't embedded
                    // see PDFBOX-2599
                    return codeToCID(code);
                }
                else if (unicode.length() > 1)
                {
                    LOG.warn("Trying to map multi-byte character using 'cmap', result will be poor");
                }
                
                // a non-embedded font always has a cmap (otherwise FontMapper won't load it)
                return cmap.getGlyphId(unicode.codePointAt(0));
            }
        }
        else
        {
            // If the TrueType font program is embedded, the Type 2 CIDFont dictionary shall contain
            // a CIDToGIDMap entry that maps CIDs to the glyph indices for the appropriate glyph
            // descriptions in that font program.

            int cid = codeToCID(code);
            if (cid2gid != null)
            {
                // use CIDToGIDMap
                if (cid < cid2gid.length)
                {
                    return cid2gid[cid];
                }
                else
                {
                    return 0;
                }
            }
            else
            {
                boolean isOTF = ttf instanceof OpenTypeFont;
                // "Identity" is the default for CFF-based OpenTypeFonts
                if (isOTF && ((OpenTypeFont) ttf).isPostScript())
                {
                    return cid;
                }
                // "Identity" is the default for TrueTypeFonts if the CID is within the range
                return !isOTF && cid < ttf.getNumberOfGlyphs() ? cid : 0;
            }
        }
    }

    @Override
    public float getHeight(int code) throws IOException
    {
        // todo: really we want the BBox, (for text extraction:)
        return (ttf.getHorizontalHeader().getAscender() + -ttf.getHorizontalHeader().getDescender())
                / ttf.getUnitsPerEm(); // todo: shouldn't this be the yMax/yMin?
    }

    @Override
    public float getWidthFromFont(int code) throws IOException
    {
        int gid = codeToGID(code);
        float width = ttf.getAdvanceWidth(gid);
        int unitsPerEM = ttf.getUnitsPerEm();
        if (unitsPerEM != 1000)
        {
            width *= 1000f / unitsPerEM;
        }
        return width;
    }

    @Override
    public byte[] encode(int unicode)
    {
        int cid = -1;
        if (isEmbedded)
        {
            // embedded fonts always use CIDToGIDMap, with Identity as the default
            if (parent.getCMap().getName().startsWith("Identity-"))
            {
                if (cmap != null)
                {
                    cid = cmap.getGlyphId(unicode);
                }
            }
            else
            {
                // if the CMap is predefined then there will be a UCS-2 CMap
                if (parent.getCMapUCS2() != null)
                {
                    cid = parent.getCMapUCS2().toCID(unicode);
                }
            }

            // otherwise we require an explicit ToUnicode CMap
            if (cid == -1)
            {
                CMap toUnicodeCMap = parent.getToUnicodeCMap();
                if (toUnicodeCMap != null)
                {
                    byte[] codes = toUnicodeCMap.getCodesFromUnicode(Character.toString((char) unicode));
                    if (codes != null)
                    {
                        return codes;
                    }
                }
                cid = 0;
            }
        }
        else
        {
            // a non-embedded font always has a cmap (otherwise it we wouldn't load it)
            cid = cmap.getGlyphId(unicode);
        }

        if (cid == 0)
        {
            throw new IllegalArgumentException(
                    String.format("No glyph for U+%04X (%c) in font %s", unicode, (char) unicode, getName()));
        }

        // CID is always 2-bytes (16-bit) for TrueType
        return new byte[] { (byte)(cid >> 8 & 0xff), (byte)(cid & 0xff) };
    }

    @Override
    public boolean isEmbedded()
    {
        return isEmbedded;
    }

    @Override
    public boolean isDamaged()
    {
        return isDamaged;
    }

    /**
     * Returns the embedded or substituted TrueType font. May be an OpenType font if the font is
     * not embedded.
     */
    public TrueTypeFont getTrueTypeFont()
    {
        return ttf;
    }

    @Override
    public GeneralPath getPath(int code) throws IOException
    {
        if (ttf instanceof OpenTypeFont && ((OpenTypeFont)ttf).isPostScript())
        {
            // we're not supposed to have CFF fonts inside PDCIDFontType2, but if we do,
            // then we treat their CIDs as GIDs, see PDFBOX-3344
            int cid = codeToGID(code);
            Type2CharString charstring = ((OpenTypeFont)ttf).getCFF().getFont().getType2CharString(cid);
            return charstring.getPath();
        }
        else
        {
            int gid = codeToGID(code);
            GlyphData glyph = ttf.getGlyph().getGlyph(gid);
            if (glyph != null)
            {
                return glyph.getPath();
            }
            return new GeneralPath();
        }
    }

    @Override
    public boolean hasGlyph(int code) throws IOException
    {
        return codeToGID(code) != 0;
    }
}
