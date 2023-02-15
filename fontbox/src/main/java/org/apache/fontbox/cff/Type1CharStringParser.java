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

import static org.apache.fontbox.cff.CharStringCommand.ESCAPE;
import static org.apache.fontbox.cff.CharStringCommand.NUMBER_START;
import static org.apache.fontbox.cff.CharStringCommand.POP;
import static org.apache.fontbox.cff.CharStringCommand.TYPE1_CALL_LIMIT;
import static org.apache.fontbox.cff.CharStringCommand.TYPE1_OPERAND_STACK_LIMIT;

import java.io.IOException;
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.fontbox.cff.CharStringCommand.CommandConsumer;
import org.apache.fontbox.cff.CharStringCommand.CommandProvider;
import org.apache.fontbox.cff.CharStringCommand.Type1Command;
import org.apache.fontbox.cff.CharStringCommand.Type1Operator;

/**
 * Parses a binary type1 charstring command stream
 * and sends individual commands to a consumer.
 * <p>
 * It also:
 * <ul>
 * <li>detects unknown commands,
 * <li>maintains the stack of numerical operands,
 * <li>verifies that stack sizes fit commands,
 * <li>handles "callsubr" subroutine calls,
 * <li>handles "callothersubr" PS stack and back to charstring stack popping,
 * <li>handles arithmetic commands.
 * </ul>
 * 
 * Note that certain commands are only sent for debugging purposes,
 * see {@link Type1Command} for details.
 *
 * @see <a href="https://adobe-type-tools.github.io/font-tech-notes/pdfs/T1_SPEC.pdf">Adobe Type 1 Font Format, Adobe Systems (1999)</a>
 *
 * @author Villu Ruusmann
 * @author John Hewson
 * @author Gunnar Brand
 */
public final class Type1CharStringParser implements CommandProvider<Type1Command>
{
    private static final Log LOG = LogFactory.getLog(Type1CharStringParser.class);


    private final String fontName, glyphName;
    private final byte[] bytes;
    private final byte[][] subrs;
    private final CharStringOperandStack stack = new CharStringOperandStack(TYPE1_OPERAND_STACK_LIMIT / 4);
    private final CharStringOperandStack psstack = new CharStringOperandStack();


    /**
     * Constructs a new Type1CharStringParser object.
     *
     * @param fontName font name
     * @param glyphName glyph name
     * @param bytes the given mapping as byte array
     * @param subrs list of local subroutines
     */
    public Type1CharStringParser(String fontName, String glyphName, byte[] bytes, byte[][] subrs)
    {
        this.fontName = fontName;
        this.glyphName = glyphName;
        this.bytes = bytes;
        this.subrs = subrs;
    }


    /**
     * The given byte array will be parsed and converted to a Type1 sequence.
     *
     * @throws IOException if an error occurs during reading
     */
    @Override
    public void stream(CommandConsumer<Type1Command> consumer) throws IOException
    {
        Objects.nonNull(consumer);
        parse(new DataInput(bytes), consumer, 0);
        consumer.end();
    }


    private void parse(DataInput input, CommandConsumer<Type1Command> consumer, int depth) throws IOException
    {
        // TODO: if depth!=0, only allow relative charstring commands

        if ( depth>TYPE1_CALL_LIMIT ) throw new IOException(message("Type1 call limit exceeded"));

        while (input.hasRemaining()) {
            if ( stack.size()>TYPE1_OPERAND_STACK_LIMIT * 2 ) throw new IOException(message("Type1 stack limit exceeded"));

            final int b0 = input.readUnsignedByte();
            
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
                    stack.push(input.readInt());
                }
                continue;
            }

            final int b1 = b0==ESCAPE ? input.readUnsignedByte() : -1; 
            final Type1Operator operator = Type1Operator.get(b0, b1);
            if ( operator==null ) {
                if ( b0==ESCAPE ) {
                    LOG.warn(message("Unknown escaped Type1 command 0x12 0x" + Integer.toHexString(b1)));
                } else {
                    LOG.warn(message("Unknown Type1 command 0x" + Integer.toHexString(b0)));
                }
                stack.clear();
                continue;
            }

//            System.out.println("type1: " + operator);
            
            if ( stack.size() < operator.getArgumentCount() ) {
                LOG.warn(message(operator, "Missing operands, have " + stack.size() + ", need " + operator.getArgumentCount()));
                if ( !operator.keepStack ) stack.clear();
                continue;
            }

            try {
                switch (operator) {
                    case DIV:
                        double divisor  = stack.pop();
                        double dividend = stack.pop();
                        stack.push(dividend / divisor);
                        break;
                    
                    case ENDCHAR:
                        if ( depth==0 ) {
                            if ( input.hasRemaining() ) LOG.warn(message(operator, "Dangling bytes at end of charstring"));
                            consumer.apply(Type1Command.ENDCHAR, stack);
                        } else {
                            if ( input.hasRemaining() ) LOG.warn(message(operator, "Dangling bytes at end of subroutine"));
                            LOG.warn(message(operator, "Invalid at depth " + depth));
                        }
                        break;
                        
                    case CALLSUBR:
                        final int subr = stack.popInt();
                        if ( subr<0 || subr >= subrs.length ) {
                            LOG.warn(message(operator, "Invalid subr# " + subr));
                        } else {
                            parse(new DataInput(subrs[subr]), consumer, depth++);
                        }
                        break;
                        
                    case RETURN:
                        if ( depth==0 ) {
                            if ( input.hasRemaining() ) LOG.warn(message(operator, "Dangling bytes at end of charstring"));
                            LOG.warn(message(operator, "Invalid at depth 0"));
                        } else {
                            if ( input.hasRemaining() ) LOG.warn(message(operator, "Dangling bytes at end of subroutine"));
                            return;
                        }
                        break;
                        
                    case CALLOTHERSUBR:
                        psstack.clear();

                        // callothersubr command (needed in order to expand Subrs)
                        final int othersubrNum = stack.popInt();
                        final int numArgs      = stack.popInt();
                        
                        if ( stack.size()<numArgs ) {
                            LOG.warn(message(operator, "Missing operands for #" + othersubrNum + ", have " + stack.size() + ", need " + numArgs));
                            stack.clear();
                            break;
                        }

                        psstack.push(othersubrNum);
                        for ( int i = numArgs; i>0; i-- ) psstack.push(stack.pop());
                        
                        // othersubrs 0-3 have well defined semantics, we can do checks and add safety nets. 
                        switch (othersubrNum) {
                            // end flex, 3 args pushed onto PS stack:
                            // arg1 = size of flex height, arg2 + arg3 = absolute coordinates (x, y)
                            // on return, PS stack must contain y and x for the 2 "pop"s and "setcurrentpoint" following.
                            case 0:
                                if ( numArgs!=3 ) LOG.warn(message(operator, "#0 must have 3 arguments only")); 
                                consumer.apply(Type1Command.CALLOTHERSUBR, psstack);
                                // very flimsy safety net: pop arg1 (this should be done a better way)
                                if ( numArgs==3 && psstack.size()==3 ) psstack.pop();
                                break;

                            // begin flex
                            case 1:
                                if ( numArgs!=0 ) LOG.warn(message(operator, "#1 must have no arguments"));
                                consumer.apply(Type1Command.CALLOTHERSUBR, psstack);
                                break;

                            // commit flex point
                            case 2:
                                if ( numArgs!=0 ) LOG.warn(message(operator, "#2 must have no arguments"));
                                consumer.apply(Type1Command.CALLOTHERSUBR, psstack);
                                break;
                                
                            // 8.1) changing hints within a character
                            case 3:
                                // push subr# on PS stack, POP, callsubr should follow
                                if ( numArgs!=1 ) LOG.warn(message(operator, "#3 must have one argument"));
                                consumer.apply(Type1Command.CALLOTHERSUBR, psstack);
                                break;

                            default:
                                consumer.apply(Type1Command.CALLOTHERSUBR, psstack);
                        }

                        // pops must follow
                        while ( input.peekUnsignedByte(0) == ESCAPE && input.peekUnsignedByte(1) == POP ) {
                            input.readByte();
                            input.readByte();
                            stack.push(psstack.pop()); // triggers indexoutofbounds if PS stack too small!
                        }

                        if (psstack.size()>0 && othersubrNum<4 ) {
                            LOG.warn(message(operator, "Values left on the PostScript stack"));
                        }
                        
                        psstack.clear();
                        break;
                        
                    case POP:
                        LOG.warn(message(operator, "Invalid at this position"));
                        break;
                        
                        
                    default:
                        if ( operator.command==null ) {
                            LOG.warn(message(operator, "Unhandled Type1 operator!"));
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

        if ( depth>0 ) LOG.warn(message(Type1Operator.RETURN, "Is missing for subroutine!"));
    }

    
    private String message(String prefix)
    {
        return message(fontName, glyphName, prefix);
    }

    
    private String message(Type1Operator operator, String prefix)
    {
        return message(fontName, glyphName, operator, prefix);
    }

    
    public static String message(String font, String glpyh, Enum<?> operator, String prefix)
    {
        return message(operator.name() + ": " + prefix, glpyh, font);
    }

    
    public static String message(String font, String glyph, String prefix)
    {
        return prefix + " in glyph '" + glyph + "' of font " + font;
    }
    
}
