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

import java.io.IOException;

import org.apache.fontbox.cff.CharStringCommand.CommandConsumer;
import org.apache.fontbox.cff.CharStringCommand.Type1Command;
import org.apache.fontbox.cff.CharStringCommand.Type2Command;

/**
 * Converts a Type2 charstring into a Type1 charstring.<br>
 * Right now hints are not supported and flex is always converted into curves
 * since converting it into Type1 flex is a bit bothersome.
 * We could supply the size to this converter in the future, though,
 * so it can convert flex into curves or lines. 
 * 
 * @author Gunnar Brand
 * @since 07.02.2023
 */
class Type2ToType1Converter implements CommandConsumer<Type2Command>
{
    private final static Type1Command[] ALT_LINE = { Type1Command.HLINETO, Type1Command.VLINETO }; 
    
    private final float defWidthX;
    private final float nominalWidthX;
    private final CommandConsumer<Type1Command> consumer;
    private final CharStringOperandStack stack = new CharStringOperandStack(CharStringCommand.TYPE1_OPERAND_STACK_LIMIT / 4);
    /** can either be 3, 2 or 0. */
    private int firstCommand = 3;
    
    public Type2ToType1Converter(int defaultWidthX, int nomWidthX, CommandConsumer<Type1Command> consumer)
    {
        this.defWidthX = defaultWidthX;
        this.nominalWidthX = nomWidthX;
        this.consumer = consumer;
    }


    @Override
    public void apply(Type2Command command, CharStringOperandStack args) throws IOException
    {
//        System.out.println("type2c: " + command + ", " + args.size());
        
        final boolean odd = args.size() % 2 == 1;

        switch (command) {
            case RMOVETO:
                addHsbw(args, args.size() > 2);
                closePath();
                startPath();
                consumer.apply(Type1Command.RMOVETO, args);
                break;

            case HMOVETO:
                addHsbw(args, args.size() > 1);
                closePath();
                startPath();
                consumer.apply(Type1Command.HMOVETO, args);
                break;

            case VMOVETO:
                addHsbw(args, args.size() > 1);
                closePath();
                startPath();
                consumer.apply(Type1Command.VMOVETO, args);
                break;
                
            case RLINETO:
                // {dxa dya}+
                startPath();
                while ( args.size() >= 2 ) {
                    consumer.apply(Type1Command.RLINETO, stack.set(args, 2));
                }
                break;

            case HLINETO:
                // dx1 {dya dxb}* | {dxa dyb}+
                startPath();
                for ( int idx = 0; args.size()>0; idx++ ) {
                    consumer.apply(ALT_LINE[idx & 1], stack.set(args.pull()));
                }
                break;

            case VLINETO:
                // dy1 {dxa dyb}* | {dya dxb}+
                startPath();
                for ( int idx = 1; args.size()>0; idx++ ) {
                    consumer.apply(ALT_LINE[idx & 1], stack.set(args.pull()));
                }
                break;

            case RRCURVETO:
                // {dxa dya dxb dyb dxc dyc}+
                startPath();
                while ( args.size() >= 6 ) {
                    consumer.apply(Type1Command.RRCURVETO, stack.set(args, 6));
                }
                break;
    
            case HHCURVETO:
                // dy1? {dxa dxb dyb dxc}+
                startPath();
                for ( double dy1 = odd ? args.pull() : 0; args.size() >= 4; dy1 = 0 ) {
                    stack.set6(args.pull(), dy1, args.pull(), args.pull(), args.pull(), 0);
                    consumer.apply(Type1Command.RRCURVETO, stack);
                }
                break; 
                
            case VVCURVETO:
                // dx1? {dya dxb dyb dyc}+
                startPath();
                for ( double dx1 = odd ? args.pull() : 0; args.size() >= 4; dx1 = 0 ) {
                    stack.set6(dx1, args.pull(), args.pull(), args.pull(), 0, args.pull());
                    consumer.apply(Type1Command.RRCURVETO, stack);
                }
                break; 

            case HVCURVETO:
                //  dx1 dx2 dy2 dy3 {dya dxb dyb dxc dxd dxe dye dyf}* dxf?
                // {dxa dxb dyb dyc  dyd dxe dye dxf}+ dyf?
            case VHCURVETO:
                //  dy1 dx2 dy2 dx3 {dxa dxb dyb dyc dyd dxe dye dxf}* dyf?
                // {dya dxb dyb dxc  dxd dxe dye dyf}+ dxf?
                startPath();
                for ( boolean hv = command==Type2Command.HVCURVETO; args.size() >= 4; hv = !hv ) {
                    double d1x_y = args.pull(), d2x = args.pull(), d2y = args.pull(), d3y_x = args.pull();
                    double d3x_y = args.size()>0 && args.size()<4 ? args.pull() : 0;
                    if ( hv ) {
                        stack.set6(d1x_y, 0, d2x, d2y, d3x_y, d3y_x);
                    } else {
                        stack.set6(0, d1x_y, d2x, d2y, d3y_x, d3x_y);
                    }
                    consumer.apply(Type1Command.RRCURVETO, stack);
                }
                break;
                
            case RCURVELINE:
                startPath();
                while ( args.size() >= 8 ) consumer.apply(Type1Command.RRCURVETO, stack.set(args, 6));
                consumer.apply(Type1Command.RLINETO, stack.set(args, 2));
                break; 

            case RLINECURVE:
                startPath();
                while ( args.size() >= 8 ) consumer.apply(Type1Command.RLINETO, stack.set(args, 2));
                consumer.apply(Type1Command.RRCURVETO, stack.set(args, 6));
                break; 

            case FLEX:
                // |- dx1 dy1 dx2 dy2 dx3 dy3 dx4 dy4 dx5 dy5 dx6 dy6 fd
                startPath();
                consumer.apply(Type1Command.RRCURVETO, stack.set(args, 6));
                consumer.apply(Type1Command.RRCURVETO, stack.set(args, 6));
                break;
    
            case HFLEX: {
                // |- dx1 dx2 dy2 dx3 dx4 dx5 dx6
                // hflex is used when the following are all true:
                // a) the starting and ending points, first and last control points have the same y value.
                // b) the joining point and the neighbor control points have the same y value.
                // c) the flex depth is 50
                double dx1 = args.pull(), dx2 = args.pull(), dy2 = args.pull(), dx3 = args.pull(),
                    dx4 = args.pull(), dx5 = args.pull(), dx6 = args.pull();
                startPath();
                consumer.apply(Type1Command.RRCURVETO, stack.set6(dx1, 0, dx2,  dy2, dx3, 0));
                consumer.apply(Type1Command.RRCURVETO, stack.set6(dx4, 0, dx5, -dy2, dx6, 0));
                break;
            }
    
            case HFLEX1: {
                // |- dx1 dy1 dx2 dy2 dx3 dx4 dx5 dy5 dx6
                // hflex1 is used if the conditions for hflex are not met but all of the following are true:
                // a) the starting and ending points have the same y value,
                // b) the joining point and the neighbor control points have the same y value.
                // c) the flex depth is 50
                // i.e. we have to revert all y offsets to get back to the starting point y for the dy6
                double dx1 = args.pull(), dy1 = args.pull(), dx2 = args.pull(), dy2 = args.pull(),
                    dx3 = args.pull(), dx4 = args.pull(), dx5 = args.pull(), dy5 = args.pull(),
                    dx6 = args.pull(), dy = dy1 + dy2 + dy5;
                // Note: previous implementation used 0 for dy6, which would keep it at dy5 
                startPath();
                consumer.apply(Type1Command.RRCURVETO, stack.set6(dx1, dy1, dx2, dy2, dx3,   0));
                consumer.apply(Type1Command.RRCURVETO, stack.set6(dx4,   0, dx5, dy5, dx6, -dy));
                break;
            }
    
            case FLEX1: {
                // |- dx1 dy1 dx2 dy2 dx3 dy3 dx4 dy4 dx5 dy5 d6
                // flex1 is used if the conditions for hflex and hflex1 are not met but all of the following are true:
                // a) the starting and ending points have the same x or y value,
                // b) the flex depth is 50.
                // The d6 argument will be either a dx or dy value, depending on the curve.
                // To determine the correct value, compute the distance from the starting point (x, y),
                // the first point of the first curve, to the last flex control point (dx5, dy5) call this (dx, dy).
                // If abs(dx) > abs(dy), then the last point’s x-value is given by d6, and its y-value is equal to y.
                // Otherwise, the last point’s x-value is equal to x and its y-value is given by d6.
                // Note: x / y = starting point coordinates
                double dx1 = args.pull(), dy1 = args.pull(), dx2 = args.pull(), dy2 = args.pull(),
                    dx3 = args.pull(), dy3 = args.pull(), dx4 = args.pull(), dy4 = args.pull(),
                    dx5 = args.pull(), dy5 = args.pull(), d6 = args.pull(),
                    dx = dx1 + dx2 + dx3 + dx4 + dx5, dy = dy1 + dy2 + dy3 + dy4 + dy5;
                startPath();
                consumer.apply(Type1Command.RRCURVETO, stack.set6(dx1, dy1, dx2, dy2, dx3, dy3));
                if ( Math.abs(dx)>Math.abs(dy) ) {
                    consumer.apply(Type1Command.RRCURVETO, stack.set6(dx4, dy4, dx5, dy5, d6, -dy));
                } else {
                    consumer.apply(Type1Command.RRCURVETO, stack.set6(dx4, dy4, dx5, dy5, -dx, d6));
                }
                firstCommand = 0;
                break;
            }
    
            // Operator for Finishing a Path
            case ENDCHAR:
                // - {adx ady bchar achar}? endchar |-  (please note that arguments are take from top of stack
                // but width argument is supposed to be bottom of stack
                addHsbw(args, args.size()==1 || args.size()>=5);
                closePath();
                if ( args.size()>=4 ) {
                    int achar = args.popInt(), bchar = args.popInt();
                    double ady = args.pop(), adx = args.pop();
                    consumer.apply(Type1Command.SEAC, stack.set5(0, adx, ady, bchar, achar));
                } else {
                    consumer.apply(Type1Command.ENDCHAR, args);
                }
                break;
    
            // Hint Operators (all ignored except for HSBW handling)
            case HSTEM:
            case HSTEMHM:
//                if ( firstCommand ) addHsbw(operands, odd);
//                for ( double y = 0, dy = 0; operands.size() >= 2; y += dy ) {
//                    y += operands.pull();
//                    dy = operands.pull();
//                    consumer.apply(stack.set2(y, dy), Type1Command.HSTEM);
//                }
//                break;
            case VSTEM:
            case VSTEMHM:
//                if ( firstCommand ) addHsbw(operands, odd);
//                for ( double x = 0, dx = 0; operands.size() >= 2; x += dx ) {
//                    x += operands.pull();
//                    dx = operands.pull();
//                    consumer.apply(stack.set2(x, dx), Type1Command.VSTEM);
//                }
//                break;
            case HINTMASK:
            case CNTRMASK:
                addHsbw(args, odd);
                break;
                
            case DOTSECTION:
                consumer.apply(Type1Command.DOTSECTION, args);
                break;
        }
    }

    
    @Override
    public void end() throws IOException
    {
        consumer.end();
    }

    
    private void addHsbw(CharStringOperandStack operands, boolean hasWidth) throws IOException
    {
        if ( (firstCommand & 1) == 1 ) {
            consumer.apply(Type1Command.HSBW, stack.set2(0, hasWidth ? operands.pull() + nominalWidthX : defWidthX));
            firstCommand--;
        }
    }


    private void startPath()
    {
        firstCommand = 0;
    }

    
    /**
     * Closes path if previously being moved or drawn to.
     */
    private void closePath() throws IOException
    {
        if ( firstCommand==0 ) {
            consumer.apply(Type1Command.CLOSEPATH, stack.set());
            firstCommand = 2;
        }
    }
    
}
