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
import java.util.List;

/**
 * Specifies access to glyph description classes, simple and composite.
 * 
 * This class is based on code from Apache Batik a subproject of Apache XMLGraphics.
 * see http://xmlgraphics.apache.org/batik/ for further details.
 * 
 */
public abstract class GlyphDescription 
{
    public final static boolean HINTING_ENABLED = Boolean.getBoolean("org.apache.fontbox.ttf.GlyfDescript.enable-hinting");
    

    // Flags describing a coordinate of a glyph.
    /**
     * if set, the point is on the curve.
     */
    protected static final byte ON_CURVE = 0x01;
    /**
     * if set, the x-coordinate is 1 byte long.
     */
    protected static final byte X_SHORT_VECTOR = 0x02;
    /**
     * if set, the y-coordinate is 1 byte long.
     */
    protected static final byte Y_SHORT_VECTOR = 0x04;
    /**
     * if set, the next byte specifies the number of additional 
     * times this set of flags is to be repeated.
     */
    protected static final byte REPEAT = 0x08;
    /**
     * This flag as two meanings, depending on how the
     * x-short vector flags is set.
     * If the x-short vector is set, this bit describes the sign
     * of the value, with 1 equaling positive and 0 negative.
     * If the x-short vector is not set and this bit is also not
     * set, the current x-coordinate is a signed 16-bit delta vector.
     */
    protected static final byte X_DUAL = 0x10;
    /**
     * This flag as two meanings, depending on how the
     * y-short vector flags is set.
     * If the y-short vector is set, this bit describes the sign
     * of the value, with 1 equaling positive and 0 negative.
     * If the y-short vector is not set and this bit is also not
     * set, the current y-coordinate is a signed 16-bit delta vector.
     */
    protected static final byte Y_DUAL = 0x20;

    
    private int[] instructions;

    
    /**
     * Constructor.
     * 
     * @param numberOfContours the number of contours
     * @param bais the stream to be read
     * @throws IOException is thrown if something went wrong
     */
    GlyphDescription() 
    {
    }


    /**
     * Read the hinting instructions.
     * @param bais the stream to be read
     * @param count the number of instructions to be read 
     * @throws IOException is thrown if something went wrong
     */
    void readInstructions(TTFDataStream bais, int count) throws IOException
    {
        if ( !HINTING_ENABLED ) {
            bais.seek(bais.getCurrentPosition() + count);
            count = 0;
        }
        instructions = bais.readUnsignedByteArray(count);
    }
    
    
    /**
     * Returns the hinting instructions (if enabled via {@link #HINTING_ENABLED}).
     * @return an array containing the hinting instructions.
     */
    public int[] getInstructions() 
    {
        return instructions;
    }
    
    
    /**
     * Returns whether this point is a composite or not.
     */
    public abstract boolean isComposite();
 

    /**
     * Returns the number of components.
     */
    public abstract int getComponentCount();

    
    /**
     * Returns a read-only list of all components in a composite glyph,
     * an empty list if not a composite glyph.
     */
    public abstract List<GlyphComponent> getComponents();
    

    /**
     * Returns the number of contours.
     */
    public abstract int getContourCount(); 

    
    /**
     * Returns the number of points.
     */
    public abstract int getPointCount();

    
    /** 
     * Returns the index of the ending point of the given contour.
     * 
     * @param contour the number of the contour
     * @return the index of the ending point of the given contour
     */
    public abstract int getEndPtOfContours(int contour);
    
    
    /**
     * Returns {@code true} if the point is on the curve, {@code false} otherwise.
     * @param index the given point
     */
    public abstract boolean isOnCurve(int index);
    
    
    /**
     * Returns the x coordinate of the given point.
     * @param index the given point
     */
    public abstract int getXCoordinate(int index);

    
    /**
     * Returns the y coordinate of the given point.
     * @param index the given point
     */
    public abstract int getYCoordinate(int index);

}
