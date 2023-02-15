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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
class GlyfCompositeDescript extends GlyphDescription
{
    /**
     * Log instance.
     */
    private static final Log LOG = LogFactory.getLog(GlyfCompositeDescript.class);

    private final GlyphComponent[] components;
    private final int contourCount;
    private final int pointCount;

    /**
     * Constructor.
     * 
     * @param bais the stream to be read
     * @param glyphTable the Glyphtable containing all glyphs
     * @param glyphs Set of known glpyhs in this composite glyph chain.
     * @throws IOException is thrown if something went wrong
     */
    GlyfCompositeDescript(int gid, TTFDataStream bais, GlyphTable glyphTable, Map<Integer, GlyphDescription> glyphs) throws IOException
    {
        // Load all of the composite components
        final List<GlyphComponent> comps = new ArrayList<GlyphComponent>();
        GlyphComponent last;
        do {
            last = new GlyphComponent(bais);
            comps.add(last);
        } 
        while ( last.hasFlag(GlyphComponent.MORE_COMPONENTS) );

        // Are there hinting instructions to read?
        if ( last.hasFlag(GlyphComponent.WE_HAVE_INSTRUCTIONS) ) {
            readInstructions(bais, (bais.readUnsignedShort()));
        }

        // Initialize components
        if ( glyphs==null) glyphs = new HashMap<>();
        glyphs.put(gid, this);
        
        int contourOffset = 0, pointOffset = 0;
        for ( GlyphComponent c : comps ) {
            final Integer index = c.getGlyphIndex();
            GlyphDescription gd = glyphs.get(index);
            if ( gd!=null && gd.isComposite() ) {
                LOG.error("Circular composite glyph reference detected in glyph " + gid + " -> " + index);
                gd = new GlyfSimpleDescript();
            }
            if ( gd==null ) {
                try {
                    // TODO: composite unnecessarily cached.
                    GlyphData glyph = glyphTable.getGlyph(index, glyphs);
                    if (glyph == null) {
                        // TODO: never null for gid inside font's gid range...
                        LOG.error("Missing glyph description in glyph " + gid + " for for gid " + index);
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
        
        this.components   = comps.toArray(new GlyphComponent[comps.size()]);
        this.contourCount = contourOffset;
        this.pointCount   = pointOffset;
    }


    @Override
    public boolean isComposite()
    {
        return true;
    }


    @Override
    public int getComponentCount()
    {
        return components.length;
    }
    
    
    @Override
    public List<GlyphComponent> getComponents()
    {
        return Collections.unmodifiableList(Arrays.asList(components));
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
        GlyphComponent c = lookupCompositeCompContour(contour);
        if (c != null)
        {
            GlyphDescription gd = c.getGlyph();
            return c.getPointOffset() + gd.getEndPtOfContours(contour - c.getContourOffset());
        }
        return 0;
    }


    @Override
    public boolean isOnCurve(int index)
    {
        GlyphComponent c = lookupCompositeCompPoint(index);
        if (c != null)
        {
            GlyphDescription gd = c.getGlyph();
            return gd.isOnCurve(index - c.getPointOffset());
        }
        return false;
    }


    @Override
    public int getXCoordinate(int index)
    {
        GlyphComponent c = lookupCompositeCompPoint(index);
        if (c != null)
        {
            GlyphDescription gd = c.getGlyph();
            index -= c.getPointOffset();
            return c.transformX(gd.getXCoordinate(index), gd.getYCoordinate(index));
        }
        return 0;
    }

    
    @Override
    public int getYCoordinate(int index)
    {
        GlyphComponent c = lookupCompositeCompPoint(index);
        if (c != null)
        {
            GlyphDescription gd = c.getGlyph();
            index -= c.getPointOffset();
            return c.transformY(gd.getXCoordinate(index), gd.getYCoordinate(index));
        }
        return 0;
    }


    private GlyphComponent lookupCompositeCompContour(int contour)
    {
        for (GlyphComponent c : components)
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

    private GlyphComponent lookupCompositeCompPoint(int index)
    {
        for (GlyphComponent c : components)
        {
            int offset = c.getPointOffset();
            if (offset <= index ) {
                GlyphDescription gd = c.getGlyph();
                if ( gd != null && index < (offset + gd.getPointCount()) ) return c;
            } else {
                break;
            }
        }
        return null;
    }

}
