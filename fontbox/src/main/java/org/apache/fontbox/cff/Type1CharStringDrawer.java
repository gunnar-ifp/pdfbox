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
package org.apache.fontbox.cff;

import java.awt.geom.GeneralPath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.fontbox.cff.CharStringCommand.CommandConsumer;
import org.apache.fontbox.cff.CharStringCommand.CommandProvider;
import org.apache.fontbox.cff.CharStringCommand.Type1Command;
import org.apache.fontbox.encoding.StandardEncoding;
import org.apache.fontbox.type1.Type1CharStringReader;

/**
 * This class renders a Type 1 CharString.
 * <p>
 * TODO: add flex line rendering if path is requested for a small height
 * as described in the spec.
 *
 * @author Villu Ruusmann
 * @author John Hewson
 */
public class Type1CharStringDrawer implements CommandConsumer<Type1Command>
{
    private static final Log LOG = LogFactory.getLog(Type1CharStringDrawer.class);

    private final Type1CharStringReader font;
    private final String fontName;
    private final String glyphName;

    private final GeneralPath path = new GeneralPath();
    private final CharStringOperandStack flexPoints = new CharStringOperandStack(7 * 2);

    private double lsbX = 0, lsbY = 0, currentX = 0, currentY = 0;
    private int width = 0;
    private boolean isFlex = false;;
    
    private Collection<String> includes = null;

    /**
     * Constructs a new Type1CharString object.
     * @param font Parent Type 1 CharString font.
     * @param fontName Name of the font.
     * @param glyphName Name of the glyph.
     */
    public Type1CharStringDrawer(Type1CharStringReader font, String fontName, String glyphName)
    {
        this.font = font;
        this.fontName = fontName;
        this.glyphName = glyphName;
    }

    
    public GeneralPath getPath()
    {
        return path;
    }
    

    public int getWidth()
    {
        return width;
    }


    @Override
    public void apply(Type1Command command, CharStringOperandStack args) throws IOException, ArrayIndexOutOfBoundsException
    {
//        System.out.println("type1c: " + command + ", " + args.size());

        switch (command) {
            case RMOVETO:
                if (isFlex) {
                    flexPoints.push(args.pull());
                    flexPoints.push(args.pull());
                } else {
                    rmoveTo(args.pull(), args.pull());
                }
                break;

            case VMOVETO:
                // not in the Type 1 spec, but exists in some fonts
                if (isFlex) {
                    flexPoints.push(0);
                    flexPoints.push(args.pull());
                } else {
                    rmoveTo(0, args.pull());
                }
                break;

            case HMOVETO:
                if (isFlex) {
                    // not in the Type 1 spec, but exists in some fonts
                    flexPoints.push(args.pull());
                    flexPoints.push(0);
                } else {
                    rmoveTo(args.pull(), 0);
                }
                break;

            case RLINETO:
                rlineTo(args.pull(), args.pull());
                break;

            case HLINETO:
                rlineTo(args.pull(), 0);
                break;

            case VLINETO:
                rlineTo(0, args.pull());
                break;

            case RRCURVETO:
                rrcurveTo(args.pull(), args.pull(), args.pull(), args.pull(), args.pull(), args.pull());
                break;

            case CLOSEPATH:
                closePath();
                break;

            case SBW:
                setSideBearing(args.pull(), args.pull(), args.pull(), args.pull());
                break;

            case HSBW:
                setSideBearing(args.pull(), 0, args.pull(), 0);
                break;

            case VHCURVETO:
                rrcurveTo(0, args.pull(), args.pull(), args.pull(), args.pull(), 0); 
                break;

            case HVCURVETO:
                rrcurveTo(args.pull(), 0, args.pull(), args.pull(), 0, args.pull());
                break;

            case SEAC:
                seac(args.pull(), args.pull(), args.pull(), args.pullInt(), args.pullInt());
                break;

            case CALLOTHERSUBR:
                callothersubr(args.pullInt(), args);
                break;

            case SETCURRENTPOINT:
                setcurrentpoint(args.pull(), args.pull());
                break;

            // ignore hints
            case HSTEM:
            case VSTEM:
            case HSTEM3:
            case VSTEM3:
            case DOTSECTION:

            // end
            case ENDCHAR:
        }
    }

    
    /**
     * Sets the current absolute point without performing a moveto.
     * Used only with results from callothersubr
     */
    private void setcurrentpoint(double x, double y)
    {
//        isFlex = false;
//        flexPoints.clear();
        // we should be at this location aready...
//        if ( current.getX()!=x || current.getY()!=y ) {
//            LOG.warn("TODO:");
//        }
        setLocation(x, y);
    }

    
    private void setSideBearing(double sbx, double sby, double wx, double wy)
    {
        if ( includes==null ) {
            lsbX = sbx;
            lsbY = sby;
            width = (int)wx;
            //heigth = (int)wy;
            setLocation(sbx, sby);
        }
    }
    
    /**
     * Flex (via OtherSubrs)
     * @param num OtherSubrs entry number
     */
    private void callothersubr(int num, CharStringOperandStack psStack)
    {
        switch (num) {
            // 8.3) Flex
            // Start flex and store current locations
            case 1:
                // Assume only rmoveto commands will follow, $current will stay at its position then.
                // TODO: throws error if any other command appears!
                isFlex = true;
                break;
            
            // discard rmoveto and relativize current location to previous for flex point array
            case 2:
                // we are storing the flex points in rmoveto, so nothing to do here.
                break;
                
            // end flex
            case 0:
                try {
                    if (flexPoints.size() < 7 * 2) {
                        LOG.warn(message(Type1Command.CALLOTHERSUBR, "flex without moveTo"));
                        break;
                    }
            
                    // reference point is relative to start point,
                    // first point is relative to reference point,
                    // make first point relative to the start point by adding reference point
                    final double refX = flexPoints.pull();
                    final double refY = flexPoints.pull();

                    rrcurveTo(
                        flexPoints.pull() + refX, flexPoints.pull() + refY,
                        flexPoints.pull(), flexPoints.pull(),
                        flexPoints.pull(), flexPoints.pull());
                    rrcurveTo(
                        flexPoints.pull(), flexPoints.pull(),
                        flexPoints.pull(), flexPoints.pull(),
                        flexPoints.pull(), flexPoints.pull());
                } finally {
                    isFlex = false;
                    flexPoints.clear();
                    // remove arg1 (size), keep arg2 and arg3
                    psStack.pop();
//                    psStack.clear();
//                    psStack.push(current.getY());
//                    psStack.push(current.getX());
                }
                break;
            
            // 8.1) Changing Hints Within a Character.
            // It is supported: keep the subr# on the PS stack, so it is called afterwards.
            // If not supported: replace subr# with 3
            case 3:
                if ( psStack.isEmpty() ) LOG.warn(message(Type1Command.CALLOTHERSUBR, "No subr# on PS stack"));
                break;
                
            default:
                LOG.warn(message(Type1Command.CALLOTHERSUBR, "Unsupported callothersubr #" + num));
        }
    }

    
    /**
     * Relative moveto.
     */
    private void rmoveTo(double dx, double dy)
    {
        double x = currentX + dx;
        double y = currentY + dy;
        path.moveTo(x, y);
        setLocation(x, y);
    }

    
    /**
     * Relative lineto.
     */
    private void rlineTo(double dx, double dy)
    {
        double x = currentX + dx;
        double y = currentY + dy;
        if (path.getCurrentPoint() == null) {
            LOG.warn(message(Type1Command.RLINETO, "Missing initial moveTo"));
            path.moveTo(x, y);
        } else {
            path.lineTo(x, y);
        }
        setLocation(x, y);
    }

    
    /**
     * Relative curveto.
     */
    private void rrcurveTo(double dx1, double dy1, double dx2, double dy2, double dx3, double dy3)
    {
        double x1 = currentX + dx1;
        double y1 = currentY + dy1;
        double x2 = x1 + dx2;
        double y2 = y1 + dy2;
        double x3 = x2 + dx3;
        double y3 = y2 + dy3;
        if (path.getCurrentPoint() == null) {
            LOG.warn(message(Type1Command.RRCURVETO, "Missing initial moveTo"));
            path.moveTo(x3, y3);
        } else {
            path.curveTo(x1, y1, x2, y2, x3, y3);
        }
        setLocation(x3, y3);
    }

    
    /**
     * Close path.
     */
    private void closePath()
    {
        if (path.getCurrentPoint() == null) {
            LOG.warn(message(Type1Command.CLOSEPATH, "Mising initial moveTo"));
        } else {
            path.closePath();
        }
        path.moveTo(currentX, currentY);
    }

    
    /**
     * Standard Encoding Accented Character
     *
     * Makes an accented character from two other characters.
     * @param asb 
     */
    private void seac(double asb, double adx, double ady, int bchar, int achar)
        throws IOException
    {
        if ( includes==null ) includes = new ArrayList<>(2);

        // hsbw/sbw of base character must be same as this character, so we ignore it.
        // hsbw/sbw x component of accent must be the same as asb argument.
        
        // base character character
        final String baseName = StandardEncoding.INSTANCE.getName(bchar);
        // we assume that current == origin
        if ( !includes.contains(baseName) ) {
            includes.add(baseName);
            try {
                CommandProvider<Type1Command> base = font.getType1CharString(baseName).getType1Stream();
                base.stream(this);
            } catch (IOException e) {
                LOG.warn(message(Type1Command.SEAC, "Invalid base character " + baseName + " (" + bchar + ")"));
            }
        }
        
        final String accentName = StandardEncoding.INSTANCE.getName(achar);
        // PDFBOX-5339: avoid ArrayIndexOutOfBoundsException 
        // reproducable with poc file crash-4698e0dc7833a3f959d06707e01d03cda52a83f4
        if ( includes.contains(accentName) ) {
            // TODO: we don't really need to ignore it, though...
            LOG.warn(message(Type1Command.SEAC, "Base and accent are the same character " + baseName + " (" + bchar + "/" + achar + ")"));
        } else {
            includes.add(accentName);
            try {
                CommandProvider<Type1Command> accent = font.getType1CharString(accentName).getType1Stream();
                closePath();
                setcurrentpoint(
                    // The old path adding code did an AF transform, with the accent path starting at "asb".
                    // So if "asb" was 15 and our "sbw" is 10, it had to move the accent path 5 pixels left.
                    //adx + lsbX - asb,
                    // Since we are drawing the accent while ignoring its hsbw/sbw command, we don't need to do this.
                    // TODO: what about the vertical side bearing?
                    adx + lsbX,
                    ady + lsbY);
                accent.stream(this);
            }
            catch (IOException e) {
                LOG.warn(message(Type1Command.SEAC, "Invalid accent character " + accentName + " (" + achar + ")"));
            }
        }
    }

    
    private void setLocation(double x, double y)
    {
        currentX = x;
        currentY = y;
    }
    

    private String message(Type1Command command, String prefix)
    {
        return Type1CharStringParser.message(fontName, glyphName, command, prefix);
    }
    
}
