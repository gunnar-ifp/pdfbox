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

/**
 * This class is based on code from Apache Batik a subproject of Apache XMLGraphics. see
 * http://xmlgraphics.apache.org/batik/ for further details.
 * 
 * @see "https://learn.microsoft.com/en-us/typography/opentype/spec/glyf"
 */
public class GlyphComponent
{
    private static final float F2_14 = 1f / 0x4000;

    // Flags for composite glyphs.

    /**
     * If set, the arguments are words; otherwise, they are bytes.
     */
    protected static final short ARG_1_AND_2_ARE_WORDS = 0x0001;
    
    /**
     * If set, the arguments are xy values; otherwise they are points.
     */
    public static final short ARGS_ARE_XY_VALUES = 0x0002;
    
    /**
     * If set, and {@link #ARGS_ARE_XY_VALUES} is also set,
     * the offset vector (after any transformation and variation deltas are applied)
     * is grid-fitted, with the x and y values rounded to the nearest pixel grid line.
     */
    public static final short ROUND_XY_TO_GRID = 0x0004;
    
    /**
     * If set, there is a simple scale; otherwise, scale = 1.0.
     */
    public static final short WE_HAVE_A_SCALE = 0x0008;
    
    /**
     * Indicates at least one more glyph after this one.
     */
    protected static final short MORE_COMPONENTS = 0x0020;
    
    /**
     * The x direction will use a different scale from the y direction.
     */
    public static final short WE_HAVE_AN_X_AND_Y_SCALE = 0x0040;
    
    /**
     * There is a 2 by2 transformation that will be used to scale the component.
     */
    public static final short WE_HAVE_A_TWO_BY_TWO = 0x0080;
    
    /**
     * Following the last component are instructions for the composite character.
     */
    protected static final short WE_HAVE_INSTRUCTIONS = 0x0100;
    
    /**
     * If set, this forces the aw and lsb (and rsb) for the composite to be equal to those from this original glyph.
     */
    protected static final short USE_MY_METRICS = 0x0200;
    
    /**
     * The composite is designed to have the component offset scaled. Ignored if ARGS_ARE_XY_VALUES is not set
     * or if {@link #UNSCALED_COMPONENT_OFFSET} is also set (is an error, default used).
     */
    public static final short SCALED_COMPONENT_OFFSET = 0x0800;
    
    /**
     * The composite is designed not to have the component offset scaled. Ignored if ARGS_ARE_XY_VALUES is not set.
     * This is the default value.
     */
    public static final short UNSCALED_COMPONENT_OFFSET = 0x1000;
    
    
    protected final int flags;
    protected final int glyphIndex;
    /** Offset of the referenced glyph's first point in parent glyph. */ 
    protected final int offsetPoints;
    /** Offset of the referenced glyph's first contour in parent glyph. */
    protected final int offsetContours;
    protected final GlyphDescription glyph;
    protected final float scaleX;
    protected final float scaleY;
    protected final float scale01;
    protected final float scale10;
    protected final float translateXf;
    protected final float translateYf;
    protected final int translateXi;
    protected final int translateYi;
//    protected final int point1;
//    protected final int point2;

    protected final short argument1;
    protected final short argument2;
    
    /**
     * 0 = integer translation, 1 = simple scale + translation, 2 = affine transform, 3 = float translation
     */
    private final int mode;
    

    
    /**
     * Constructor.
     * 
     * @param bais the stream to be read
     * @throws IOException is thrown if something went wrong
     */
    GlyphComponent(int flags, int glyphIndex, GlyphDescription gd, int contourOffset, int pointOffset, TTFDataStream bais)
        throws IOException
    {
        this.flags = flags;
        this.glyphIndex = glyphIndex;
        this.glyph = gd;
        this.offsetContours = contourOffset;
        this.offsetPoints = pointOffset;
        
        // Get the arguments as just their raw values
        if ( hasFlag(ARG_1_AND_2_ARE_WORDS) )
        {
            argument1 = bais.readSignedShort();
            argument2 = bais.readSignedShort();
        }
        else
        {
            argument1 = (short) bais.readSignedByte();
            argument2 = (short) bais.readSignedByte();
        }

        int scale = 0, translate = 0;
        // Get the scale values (if any), values are F2.14
        if ( hasFlag(WE_HAVE_A_TWO_BY_TWO) )
        {
            scale   = 2;
            scaleX  = bais.readSignedShort() * F2_14;
            scale01 = bais.readSignedShort() * F2_14;
            scale10 = bais.readSignedShort() * F2_14;
            scaleY  = bais.readSignedShort() * F2_14;
        }
        else if ( hasFlag(WE_HAVE_AN_X_AND_Y_SCALE) )
        {
            scale   = 1;
            scaleX  = bais.readSignedShort() * F2_14;
            scaleY  = bais.readSignedShort() * F2_14;
            scale01 = 0;
            scale10 = 0;
        }
        else if ( hasFlag(WE_HAVE_A_SCALE) )
        {
            scale   = 1;
            scaleX  = bais.readSignedShort() * F2_14;
            scaleY  = scaleX;
            scale01 = 0;
            scale10 = 0;
        }
        else
        {
            scaleX  = 1;
            scaleY  = 1;
            scale01 = 0;
            scale10 = 0;
        }
        
        // Assign the arguments according to the flags
        if ( hasFlag(ARGS_ARE_XY_VALUES) )
        {
            if ( hasFlag(SCALED_COMPONENT_OFFSET) && !hasFlag(UNSCALED_COMPONENT_OFFSET) ) {
                // see: https://fontforge.org/archive/Composites/index.html
                double ox = argument1 * Math.sqrt(scaleX * scaleX + scale01 * scale01);
                double oy = argument2 * Math.sqrt(scaleY * scaleY + scale10 * scale10);
                if ( hasFlag(ROUND_XY_TO_GRID) ) {
                    translateXf = translateXi = Math.round((float)ox);
                    translateYf = translateYi = Math.round((float)oy);
                } else {
                    translate   = 3;
                    translateXf = (float)ox;
                    translateYf = (float)oy;
                    translateXi = translateYi = 0;
                }
            } else {
                translateXf = translateXi = argument1; 
                translateYf = translateYi = argument2;
            }
        }
        else
        {
            // TODO: https://learn.microsoft.com/en-us/typography/opentype/spec/gvar#point-numbers-and-processing-for-composite-glyphs
            /* If ARGS_ARE_XY_VALUES is not set, then Argument1 is a point number in the parent glyph
             * (from contours incoporated and re-numbered from previous component glyphs);
             * and Argument2 is a point number (prior to re-numbering) from the child component glyph.
             * Phantom points from the parent or the child may be referenced. The child component glyph
             * is positioned within the parent glyph by aligning the two points.
             * If a scale or transform matrix is provided, the transformation is applied to the child’s point before the points are aligned.
             * 
             * In a variable font, when a component is positioned by alignment of points,
             * deltas are applied to component glyphs before this alignement is done.
             * Any deltas specified for the parent composite glyph to be applied to components positioned
             * by point aligment are ignored. See Point numbers and processing for composite glyphs in the 'gvar' chapter for details.
             */
//            point1 = argument1;
//            point2 = argument2;
            translateXf = translateXi = 0; 
            translateYf = translateYi = 0;
        }
        this.mode = scale==0 ? translate : scale;
    }


    /**
     * Returns the offset of this glyph's first contour in the parent glyph's contour list.
     */
    public int getContourOffset()
    {
        return offsetContours;
    }
    
    
    /**
     * Returns the offset of this glyphs first point in the parent glyph's point list.
     */
    public int getPointOffset()
    {
        return offsetPoints;
    }

    
    /**
     * Returns the glyph of this component. If the glyph was not in the font, then {@code null}.
     */
    public GlyphDescription getGlyph()
    {
        return glyph;
    }
    

    /**
     * Returns argument 1.
     */
    public short getArgument1()
    {
        return argument1;
    }

    /**
     * Returns argument 2.
     */
    public short getArgument2()
    {
        return argument2;
    }

    /**
     * Returns the flags of the glyph.
     */
    public int getFlags()
    {
        return flags;
    }

    public boolean hasFlag(int flag)
    {
        return (flags & flag) == flag;
    }
    
    /**
     * Returns the index of the first contour.
     */
    public int getGlyphIndex()
    {
        return glyphIndex;
    }

    /**
     * Returns the scale-01 value, which scales X in {@link #transformY(int, int)}.
     */
    public float getScale01()
    {
        return scale01;
    }

    /**
     * Returns the scale-10 value, which scales Y in {@link #transformX(int, int)}.
     */
    public float getScale10()
    {
        return scale10;
    }

    /**
     * Returns the x-scaling value.
     * 
     * @return the x-scaling value.
     */
    public float getXScale()
    {
        return scaleX;
    }

    /**
     * Returns the y-scaling value.
     * 
     * @return the y-scaling value.
     */
    public float getYScale()
    {
        return scaleY;
    }

    /**
     * Returns the x-translation value.
     * <p>
     * Note: not necessarily an integer, depending on
     * {@link #SCALED_COMPONENT_OFFSET} and {@link #ROUND_XY_TO_GRID} flags.
     */
    public float getXTranslate()
    {
        return translateXf;
    }

    /**
     * Returns the y-translation value.
     * <p>
     * Note: not necessarily an integer, depending on
     * {@link #SCALED_COMPONENT_OFFSET} and {@link #ROUND_XY_TO_GRID} flags.
     */
    public float getYTranslate()
    {
        return translateYf;
    }

    
    /**
     * Transforms and grid fits the x-coordinate of a point for this component.
     * 
     * @param x The x-coordinate of the point to transform
     * @param y The y-coordinate of the point to transform
     * @return The transformed x-coordinate
     */
    public int transformX(int x, int y)
    {
        // Note: no extra logic for integer translation with scaling required,
        // if translation is integer, it doesn't affect the rounding, if it's float
        // we probably don't want to round translation and scale independently anyways.
        switch (mode) {
            default:
            case 0: return translateXi + x;
            case 1: return Math.round(translateXf + x * scaleX);
            case 2: return Math.round(translateXf + x * scaleX + y * scale10);
            case 3: return Math.round(translateXf + x);
        }
    }

    
    /**
     * Transforms aand grid fits the y-coordinate of a point for this component.
     * 
     * @param x The x-coordinate of the point to transform
     * @param y The y-coordinate of the point to transform
     * @return The transformed y-coordinate
     */
    public int transformY(int x, int y)
    {
        switch (mode) {
            default:
            case 0: return translateYi + y;
            case 1: return Math.round(translateYf + y * scaleY);
            case 2: return Math.round(translateYf + y * scaleY + x * scale01);
            case 3: return Math.round(translateYf + y);
        }
    }
    
}
