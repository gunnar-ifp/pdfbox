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

import static org.apache.fontbox.cff.CharStringCommand.CFF2_CALL_LIMIT;
import static org.apache.fontbox.cff.CharStringCommand.CFF2_OPERAND_STACK_LIMIT;
import static org.apache.fontbox.cff.CharStringCommand.ESCAPE;
import static org.apache.fontbox.cff.CharStringCommand.NUMBER_START;

import java.io.IOException;
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.fontbox.cff.CharStringCommand.CFF2Command;
import org.apache.fontbox.cff.CharStringCommand.CFF2Operator;
import org.apache.fontbox.cff.CharStringCommand.CommandConsumer;
import org.apache.fontbox.cff.CharStringCommand.CommandProvider;

/**
 * Parses a binary CFF2 charstring command stream
 * and sends individual commands to a consumer.
 * <p>
 * It also:
 * <ul>
 * <li>detects unknown commands,
 * <li>maintains the stack of numerical operands,
 * <li>verifies that stack sizes fit commands,
 * <li>handles subroutine operators, 
 * </ul>
 * 
 * Note that certain commands are only sent for debugging purposes,
 * see {@link CFF2Operator} for details.
 *
 * Order of commands in Type 2 Charstring:<br>
 * <b>{hs* vs* cm* hm* mt subpath}? {mt subpath}*</b><br>
 * Where:
 * <ul>
 * <li>hs = hstem or hstemhm command
 * <li>vs = vstem or vstemhm command
 * <li>cm = cntrmask operator
 * <li>hm = hintmask operator
 * <li>mt = moveto (i.e. any of the moveto) operators
 * <li>subpath = refers to the construction of a subpath (one
 * complete closed contour), which may include hintmask
 * operators where appropriate.
 * </ul>
 * 
 * @see <a href="https://learn.microsoft.com/en-us/typography/opentype/spec/cff2charstr">The CFF2 CharString Format</a>
 *
 * @author Gunnar Brand
 * @since 09.02.2023
 */
public final class CFF2CharStringParser implements CommandProvider<CFF2Command>
{
    private static final Log LOG = LogFactory.getLog(CFF2CharStringParser.class);


    private final String fontName, glyphName;
    private final byte[] bytes;
    private final byte[][] subrs;
    private final byte[][] gsubrs;
    private final CharStringOperandStack stack = new CharStringOperandStack(CFF2_OPERAND_STACK_LIMIT / 2);
    private int hstemCount = 0, vstemCount = -1;


    /**
     * Constructs a new CFF2CharStringParser object.
     *
     * @param fontName font name
     * @param glyphName glyph name
     * @param bytes the given mapping as byte array
     * @param subrs list of local subroutines
     */
    public CFF2CharStringParser(String fontName, String glyphName, byte[] bytes, byte[][] subrs, byte[][] gsubrs)
    {
        this.fontName = fontName;
        this.glyphName = glyphName;
        this.bytes = bytes;
        this.subrs = subrs;
        this.gsubrs = gsubrs;
    }

    
    public CFF2CharStringParser(String fontName, int glyph, byte[] bytes, byte[][] subrs, byte[][] gsubrs)
    {
        this(fontName, "0x" + Integer.toHexString(glyph), bytes, subrs, gsubrs);
    }
    

    /**
     * The given byte array will be parsed and converted to a CFF2 sequence.
     *
     * @throws IOException if an error occurs during reading
     */
    @Override
    public void stream(CommandConsumer<CFF2Command> consumer) throws IOException
    {
        Objects.nonNull(consumer);
        parse(new DataInput(bytes), consumer, 0);
        consumer.end();
    }
    
    
    private void parse(DataInput input, CommandConsumer<CFF2Command> consumer, int depth) throws IOException
    {
        if ( depth>CFF2_CALL_LIMIT ) throw new IOException(message("CFF2 call limit exceeded"));
        
        while (input.hasRemaining()) {
            if ( stack.size()>CFF2_OPERAND_STACK_LIMIT * 2 ) throw new IOException(message("CFF2 stack limit exceeded"));

            final int b0 = input.readUnsignedByte();
            
            if (b0 == CharStringCommand.SHORTINT) {
                stack.push(input.readShort());
                continue;
            }
            
            if ( b0>=NUMBER_START ) {
                if ( b0 <= 246 ) {
                    stack.push(b0 - 139);
                }
                else if ( b0 <= 250 ) {
                    int b1 = input.readUnsignedByte();
                    stack.push((b0 - 247) * 256 + b1 + 108);
                }
                else if ( b0 <= 254 ) {
                    int b1 = input.readUnsignedByte();
                    stack.push(-(b0 - 251) * 256 - b1 - 108);
                }
                else {
                    // 32bit signed integer as Fixed 16.16
                    //stack.push(Math.scalb((double)input.readInt(), -16));
                    stack.push(input.readInt() * (1d / 65536));
                }
                continue;
            }

            final int b1 = b0==ESCAPE ? input.readUnsignedByte() : -1; 
            final CFF2Operator operator = CFF2Operator.get(b0, b1);
            if ( operator==null ) {
                if ( b0==ESCAPE ) {
                    LOG.warn(message("Unknown escaped CFF2 command 0x12 0x" + Integer.toHexString(b1)));
                } else {
                    LOG.warn(message("Unknown CFF2 command 0x" + Integer.toHexString(b0)));
                }
                stack.clear();
                continue;
            }

//            System.out.println("cff2o: " + operator);
            
            //if ( !(command.verify(stack.size()) ) {
            if ( stack.size() < operator.getArgumentCount() ) {
                //LOG.warn(message(command, "Missing or wrong number of operands, have " + stack.size() + ", need (at least) " + command.getArgumentCount()));
                LOG.warn(message(operator, "Missing operands, have " + stack.size() + ", need (at least) " + operator.getArgumentCount()));
                if ( !operator.keepStack ) stack.clear();
                continue;
            }

            try {
                switch (operator) {
                    // The Type 2 charstring format supports six hint operators:
                    // hstem, vstem, hstemhm, vstemhm, hintmask, and cntrmask.
                    // The hint information must be declared at the beginning of a charstring
                    // (see section 3.1) using the hstem, hstemhm, vstem, and vstemhm operators.
                    case HSTEM:
                    case HSTEMHM:
                        hstemCount += stack.size() / 2;
                        consumer.apply(operator.command, stack);
                        break;
                        
                    case VSTEM:
                    case VSTEMHM:
                        if ( vstemCount<0 ) vstemCount = 0;
                        vstemCount += stack.size() / 2;
                        consumer.apply(operator.command, stack);
                        break;
                    
                    case CNTRMASK:
                        // If hstem and vstem hints are both declared at the beginning of a charstring,
                        // and this sequence is followed directly by the hintmask or cntrmask operators,
                        // the vstem hint operator need not be included.
                        // Ed: can only be true for first mask occurence, obviously!
                        boolean emulatevstem = vstemCount<0;
                        if ( emulatevstem ) vstemCount = stack.size() / 2;
                        // The number of data bytes must be exactly the number needed
                        // to represent the number of stems in the original stem list
                        // (those stems specified by the hstem, vstem, hstemhm, or vstemhm commands),
                        // using one bit in the data bytes for each stem in the original stem list.
                        if ( hstemCount + vstemCount > CharStringCommand.CFF2_STEM_HINT_LIMIT ) {
                            LOG.warn(message(operator, "Stem hint limit exceeded: " + (hstemCount + vstemCount)));
                        }
                        for ( int c = (hstemCount + vstemCount + 7) / 8; c>0; c-- ) input.readUnsignedByte();
                        if ( emulatevstem ) {
                            CFF2Command c = input.peekUnsignedByte(0)==CharStringCommand.HINTMASK ? CFF2Command.VSTEMHM : CFF2Command.VSTEM;
                            consumer.apply(c, stack);
                            stack.clear();
                        }
                        consumer.apply(CFF2Command.CNTRMASK, stack);
                        break;

                    case HINTMASK:
                        if ( vstemCount<0 ) {
                            vstemCount = stack.size() / 2;
                            consumer.apply(CFF2Command.VSTEMHM, stack);
                            stack.clear();
                        }
                        if ( hstemCount + vstemCount > CharStringCommand.CFF2_STEM_HINT_LIMIT ) {
                            LOG.warn(message(operator, "Stem hint limit exceeded: " + (hstemCount + vstemCount)));
                        }
                        for ( int c = (hstemCount + vstemCount + 7) / 8; c>0; c-- ) input.readUnsignedByte();
                        consumer.apply(CFF2Command.HINTMASK, stack);
                        break;

                    case RMOVETO:
                    case HMOVETO:
                    case VMOVETO:
                        consumer.apply(operator.command, stack);
                        break;


                    ///////////////////////////////////////////////////////////////////////////////////////////////////////////// 
                    // Locally handled operations. They all keep the stack
                        
                    case CALLSUBR:
                    case CALLGSUBR:
                        // The numbering of subroutines is encoded more compactly by
                        // using the negative half of the number space, which effectively
                        // doubles the number of compactly encodable subroutine
                        // numbers. The bias applied depends on the number of subrs
                        // (gsubrs). If the number of subrs (gsubrs) is less than 1240, the
                        // bias is 107. Otherwise if it is less than 33900, it is 1131; otherwise
                        // it is 32768. This bias is added to the encoded subr (gsubr)
                        // number to find the appropriate entry in the subr (gsubr) array.
                        // Global subroutines may be used in a FontSet even if it only contains one font.
                        final byte[][] table = operator==CFF2Operator.CALLSUBR ? subrs : gsubrs;
                        final int num = table==null ? 0 : table.length;
                        final int subr = stack.popInt() + (num<1240 ? 107 : num<33900 ? 1131 : 32768);
                        if ( subr<0 || subr>=num ) {
                            LOG.warn(message(operator, "Invalid (global)subr# " + subr));
                        } else {
                            parse(new DataInput(table[subr]), consumer, depth++);
                        }
                        break;

                    /*
                    case VSINDEX:
                        consumer.apply(CFF2Command.VSINDEX, stack);
                        break;
                        
                    case BLEND:
                        consumer.apply(CFF2Command.BLEND, stack);
                        break;
                    */

                    default:
                        if ( operator.command==null ) {
                            LOG.warn(message(operator, "Unhandled CFF2 operator!"));
                        } else {
                            consumer.apply(operator.command, stack);
                        }
                }
            }
            catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                LOG.warn(message(operator, e.getMessage()));
            }
            finally {
                if ( !operator.keepStack ) stack.clear();
            }
        }
    }
      
 
    private String message(String prefix)
    {
        return Type1CharStringParser.message(fontName, glyphName, prefix);
    }

    
    private String message(CFF2Operator operator, String prefix)
    {
        return Type1CharStringParser.message(fontName, glyphName, operator, prefix);
    }
    
}
