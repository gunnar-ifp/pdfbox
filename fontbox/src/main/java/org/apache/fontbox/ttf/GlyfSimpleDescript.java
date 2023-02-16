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

import java.awt.geom.GeneralPath;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This class is based on code from Apache Batik a subproject of Apache XMLGraphics. see
 * http://xmlgraphics.apache.org/batik/ for further details.
 * 
 * @see "https://learn.microsoft.com/en-us/typography/opentype/spec/glyf"
 */
class GlyfSimpleDescript extends GlyphDescription
{
    private static final Log LOG = LogFactory.getLog(GlyfSimpleDescript.class);

    private final int contourCount;
    private final int pointCount;
    private final int[] contourEnds;
    private final int[] onCurveFlag;
    private final int[] coordinates;

    /**
     * Constructor for an empty description.
     * 
     * @throws IOException is thrown if something went wrong
     */
    GlyfSimpleDescript() throws IOException
    {
        this((short)0, null, (short)0);
    }

    
    /**
     * Constructor.
     * 
     * @param numberOfContours number of contours
     * @param bais the stream to be read
     * @param x0 the initial X-position
     * @throws IOException is thrown if something went wrong
     */
    GlyfSimpleDescript(short numberOfContours, TTFDataStream bais, short x0) throws IOException
    {
        /*
         * https://developer.apple.com/fonts/TTRefMan/RM06/Chap6glyf.html
         * "If a glyph has zero contours, it need not have any glyph data." set the pointCount to zero to initialize
         * attributes and avoid nullpointer but maybe there shouldn't have GlyphDescript in the GlyphData?
         */
        if (numberOfContours > 0) {
            // Simple glyph description
            int[] temp = bais.readUnsignedShortArray(numberOfContours);
            final int lastEndPt = temp[numberOfContours - 1];
            
            // PDFBOX-2939: assume an empty glyph
            if ( !(numberOfContours == 1 && lastEndPt == 65535) ) {
                super.readInstructions(bais, bais.readUnsignedShort());

                this.contourCount = numberOfContours;
                this.pointCount  = lastEndPt + 1;
                this.contourEnds = temp;
                this.onCurveFlag = new int[(pointCount + 31) / 32];
                this.coordinates = new int[pointCount];
        
                byte[] flags = readFlags(bais, pointCount);
                readCoords(bais, flags, x0);
                for ( int i = 0; i<pointCount; i++ ) {
                    if ( (flags[i] & ON_CURVE)!=0 ) onCurveFlag[i >>> 5] |= 1 << (i & 31);
                }
                return;
            }
        }
        this.contourCount = 0;
        this.pointCount = 0;
        this.contourEnds = null;
        this.onCurveFlag = null;
        this.coordinates = null;
    }

    
    @Override
    public boolean isComposite()
    {
        return false;
    }
    
    
    @Override
    public int getComponentCount()
    {
        return 0;
    }
    
    
    @Override
    public List<GlyphComponent> getComponents()
    {
        return Collections.emptyList();
    }

    
    @Override
    public int getContourCount() 
    {
        return contourCount;
    }
    

    @Override
    public int getPointCount()
    {
        return pointCount;
    }

    
    @Override
    public int getEndPtOfContours(int contour)
    {
        return contourEnds[contour];
    }


    @Override
    public boolean isOnCurve(int index)
    {
        return (onCurveFlag[index >> 5] & (1 << (index & 31))) != 0;
    }


    @Override
    public int getXCoordinate(int index)
    {
        return (short)coordinates[index];
    }


    @Override
    public int getYCoordinate(int index)
    {
        return coordinates[index] >> 16;
    }

    
    @Override
    public GeneralPath getPath()
    {
        return GlyphRenderer.DEFAULT.render(this, new GeneralPath());
    }


    /**
     * The table is stored as relative values, but we'll store them as absolutes.
     */
    private void readCoords(TTFDataStream bais, byte[] flags, int x) throws IOException
    {
        int y = 0;
        for (int i = 0; i < coordinates.length; i++)
        {
            if ((flags[i] & X_SHORT_VECTOR) == 0)
            {
                if ( (flags[i] & X_DUAL) == 0) x += bais.readSignedShort();
            }
            else
            {
                int sx = bais.readUnsignedByte();
                x += (flags[i] & X_DUAL) == 0 ? -sx : sx;
            }
            coordinates[i] = 0xffff & x;
        }

        for (int i = 0; i < coordinates.length; i++)
        {
            if ((flags[i] & Y_SHORT_VECTOR) == 0)
            {
                if ((flags[i] & Y_DUAL) == 0) y += bais.readSignedShort();
            }
            else
            {
                int sy = bais.readUnsignedByte();
                y += (flags[i] & Y_DUAL) == 0 ? -sy : sy;
            }
            coordinates[i] |= y << 16;
        }
    }

    
    /**
     * The flags are run-length encoded.
     */
    private static byte[] readFlags(TTFDataStream bais, int flagCount) throws IOException
    {
        byte[] flags = new byte[flagCount];
        for (int index = 0; index < flagCount; index++) {
            final byte flag = (byte) bais.readUnsignedByte();
            flags[index] = flag;
            
            if ((flag & REPEAT) != 0) {
                int repeats = bais.readUnsignedByte();
                while ( --repeats >= 0 && ++index < flagCount ) flags[index] = flag;
                if ( repeats > 0 || index == flagCount ) {
                    LOG.error("repeat count (" + repeats + ") higher than remaining space");
                }
            }
        }
        return flags;
    }
}
