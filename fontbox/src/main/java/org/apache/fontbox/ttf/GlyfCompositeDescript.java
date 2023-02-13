/*

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */
package org.apache.fontbox.ttf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Glyph description for composite glyphs. Composite glyphs are made up of one
 * or more simple glyphs, usually with some sort of transformation applied to
 * each.
 *
 * This class is based on code from Apache Batik a subproject of Apache
 * XMLGraphics. see http://xmlgraphics.apache.org/batik/ for further details.
 */
public class GlyfCompositeDescript extends GlyfDescript
{
    /**
     * Log instance.
     */
    private static final Log LOG = LogFactory.getLog(GlyfCompositeDescript.class);

    private final GlyfCompositeComp[] components;
    private final int contourCount;
    private final int pointCount;

    /**
     * Constructor.
     * 
     * @param bais the stream to be read
     * @param glyphTable the Glyphtable containing all glyphs
     * @param Set of known composite glpyhs in this composite glyph chain.
     * @throws IOException is thrown if something went wrong
     */
    GlyfCompositeDescript(TTFDataStream bais, GlyphTable glyphTable, Set<Integer> known) throws IOException
    {
        super((short) -1, bais);

        // Load all of the composite components
        final List<GlyfCompositeComp> comps = new ArrayList<GlyfCompositeComp>();
        GlyfCompositeComp last;
        do
        {
            last = new GlyfCompositeComp(bais);
            comps.add(last);
        } 
        while ((last.getFlags() & GlyfCompositeComp.MORE_COMPONENTS) != 0);

        // Are there hinting instructions to read?
        if ((last.getFlags() & GlyfCompositeComp.WE_HAVE_INSTRUCTIONS) != 0)
        {
            readInstructions(bais, (bais.readUnsignedShort()));
        }
        
        // Load children and initialize counts
        final Map<Integer, GlyphDescription> glyphs = new HashMap<>();
        int contourOffset = 0, pointOffset = 0;
        for (GlyfCompositeComp c : comps) {
            final Integer index = c.getGlyphIndex();
            GlyphDescription gd = glyphs.get(index);
            if ( gd==null ) {
                try {
                    // TODO: composite unnecessarily cached.
                    GlyphData glyph = glyphTable.getGlyph(index, known);
                    if (glyph == null) {
                        // TODO: never null for gid inside font's gid range...
                        LOG.error("Missing glyph description for index " + index);
                    } else {
                        gd = glyph.getDescription();
                        glyphs.put(index, gd);
                    }
                }
                catch (IOException e) {
                    LOG.error(e);
                }
            }
            c.init(gd, contourOffset, pointOffset);
            if ( gd!=null ) {
                contourOffset += gd.getContourCount();
                pointOffset   += gd.getPointCount();
            }
        }

        this.components   = comps.toArray(new GlyfCompositeComp[comps.size()]);
        this.contourCount = contourOffset;
        this.pointCount   = pointOffset;
    }


    /**
     * Get number of components.
     * 
     * @return the number of components
     */
    public int getComponentCount()
    {
        return components.length;
    }
    

    @Override
    public boolean isComposite()
    {
        return true;
    }


    @Override
    public int getPointCount()
    {
        return pointCount;
    }


    @Override
    public int getContourCount()
    {
        return contourCount;
    }


    @Override
    public int getEndPtOfContours(int contour)
    {
        GlyfCompositeComp c = lookupCompositeCompContour(contour);
        if (c != null)
        {
            GlyphDescription gd = c.getGlyph();
            return c.getPointOffset() + gd.getEndPtOfContours(contour - c.getContourOffset());
        }
        return 0;
    }


    @Override
    public byte getFlags(int point)
    {
        GlyfCompositeComp c = lookupCompositeCompPoint(point);
        if (c != null)
        {
            GlyphDescription gd = c.getGlyph();
            return gd.getFlags(point - c.getPointOffset());
        }
        return 0;
    }


    @Override
    public short getXCoordinate(int point)
    {
        GlyfCompositeComp c = lookupCompositeCompPoint(point);
        if (c != null)
        {
            GlyphDescription gd = c.getGlyph();
            point -= c.getPointOffset();
            int x = gd.getXCoordinate(point);
            int y = gd.getYCoordinate(point);
            return (short) (c.scaleX(x, y) + c.getXTranslate());
        }
        return 0;
    }

    
    @Override
    public short getYCoordinate(int point)
    {
        GlyfCompositeComp c = lookupCompositeCompPoint(point);
        if (c != null)
        {
            GlyphDescription gd = c.getGlyph();
            point -= c.getPointOffset();
            int x = gd.getXCoordinate(point);
            int y = gd.getYCoordinate(point);
            return (short) (c.scaleY(x, y) + c.getYTranslate());
        }
        return 0;
    }


    private GlyfCompositeComp lookupCompositeCompContour(int contour)
    {
        for (GlyfCompositeComp c : components)
        {
            int offset = c.getContourOffset();
            if (offset <= contour ) {
                GlyphDescription gd = c.getGlyph();
                if ( gd != null && contour < (offset + gd.getContourCount()) ) return c;
            } else {
                break;
            }
        }
        return null;
    }

    private GlyfCompositeComp lookupCompositeCompPoint(int point)
    {
        for (GlyfCompositeComp c : components)
        {
            int offset = c.getPointOffset();
            if (offset <= point ) {
                GlyphDescription gd = c.getGlyph();
                if ( gd != null && point < (offset + gd.getPointCount()) ) return c;
            } else {
                break;
            }
        }
        return null;
    }

}
