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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.fontbox.cff.CharStringCommand.Type1KeyWord;
import org.apache.fontbox.cff.CharStringCommand.Type2KeyWord;
import org.apache.fontbox.type1.Type1CharStringReader;

/**
 * Represents a Type 2 CharString by converting it into an equivalent Type 1 CharString.
 * 
 * @author Villu Ruusmann
 * @author John Hewson
 */
public class Type2CharString extends Type1CharString
{
    private float defWidthX = 0;
    private float nominalWidthX = 0;
    private int pathCount = 0;
    private final List<Object> type2sequence;
    private final int gid;

    /**
     * Constructor.
     * @param font Parent CFF font
     * @param fontName font name
     * @param glyphName glyph name (or CID as hex string)
     * @param gid GID
     * @param sequence Type 2 char string sequence
     * @param defaultWidthX default width
     * @param nomWidthX nominal width
     */
    public Type2CharString(Type1CharStringReader font, String fontName, String glyphName, int gid, List<Object> sequence,
                           int defaultWidthX, int nomWidthX)
    {
        super(font, fontName, glyphName);
        this.gid = gid;
        this.type2sequence = sequence;
        this.defWidthX = defaultWidthX;
        this.nominalWidthX = nomWidthX;
        convertType2ToType1(sequence);
    }

    /**
     * Return the GID (glyph id) of this charstring.
     */
    public int getGID()
    {
        return gid;
    }

    /**
     * Returns the Type 2 charstring sequence.
     */
    public List<Object> getType2Sequence()
    {
        return type2sequence;
    }

    /**
     * Converts a sequence of Type 2 commands into a sequence of Type 1 commands.
     * @param sequence the Type 2 char string sequence
     */
    private void convertType2ToType1(List<Object> sequence)
    {
        type1Sequence = new ArrayList<Object>();
        pathCount = 0;
        CharStringHandler handler = new CharStringHandler() {
            @Override
            public List<Number> handleCommand(List<Number> numbers, CharStringCommand command)
            {
                return Type2CharString.this.handleCommand(numbers, command);
            }
        };
        handler.handleSequence(sequence);
    }

    @SuppressWarnings(value = { "unchecked" })
    private List<Number> handleCommand(List<Number> numbers, CharStringCommand command)
    {
        commandCount++;
        
        final Type2KeyWord type2 = command.getType2KeyWord();
        if ( type2==null ) {
            addCommand(numbers, command);
            return null;
        }
        
        switch (type2) {
        case HSTEM:
            numbers = clearStack(numbers, numbers.size() % 2 != 0);
            expandStemHints(numbers, true);
            break;

        case VSTEM:
            numbers = clearStack(numbers, numbers.size() % 2 != 0);
            expandStemHints(numbers, false);
            break;

        case VMOVETO:
            numbers = clearStack(numbers, numbers.size() > 1);
            markPath();
            addCommand(numbers, command);
            break;

        case RLINETO:
            addCommandList(split(numbers, 2), command);
            break;

        case HLINETO:
            drawAlternatingLine(numbers, true);
            break;

        case VLINETO:
            drawAlternatingLine(numbers, false);
            break;

        case RRCURVETO:
            addCommandList(split(numbers, 6), command);
            break;

        case ENDCHAR:
            numbers = clearStack(numbers, numbers.size() == 5 || numbers.size() == 1);
            closeCharString2Path();
            if (numbers.size() == 4)
            {
                // deprecated "seac" operator
                numbers.add(0, 0);
                addCommand(numbers, CharStringCommand.getInstance(12, 6));
            }
            else
            {
                addCommand(numbers, command);
            }
            break;

        case RMOVETO:
            numbers = clearStack(numbers, numbers.size() > 2);
            markPath();
            addCommand(numbers, command);
            break;

        case HMOVETO:
            numbers = clearStack(numbers, numbers.size() > 1);
            markPath();
            addCommand(numbers, command);
            break;

        case VHCURVETO:
            drawAlternatingCurve(numbers, false);
            break;

        case HVCURVETO:
            drawAlternatingCurve(numbers, true);
            break;

        case HFLEX:
            if (numbers.size() >= 7)
            {
                List<Number> first = Arrays.asList(numbers.get(0), 0,
                        numbers.get(1), numbers.get(2), numbers.get(3), 0);
                List<Number> second = Arrays.asList(numbers.get(4), 0,
                        numbers.get(5), -(numbers.get(2).floatValue()),
                        numbers.get(6), 0);
                addCommandList(Arrays.asList(first, second), CharStringCommand.getInstance(8));
            }
            break; 

        case FLEX: {
            List<Number> first = numbers.subList(0, 6);
            List<Number> second = numbers.subList(6, 12);
            addCommandList(Arrays.asList(first, second), CharStringCommand.getInstance(8));
            break;
        }

        case HFLEX1:
            if (numbers.size() >= 9)
            {
                List<Number> first = Arrays.asList(numbers.get(0), numbers.get(1), 
                        numbers.get(2), numbers.get(3), numbers.get(4), 0);
                List<Number> second = Arrays.asList(numbers.get(5), 0,
                        numbers.get(6), numbers.get(7), numbers.get(8), 0);
                addCommandList(Arrays.asList(first, second), CharStringCommand.getInstance(8));
            }
            break;

        case FLEX1: {
            int dx = 0;
            int dy = 0;
            for(int i = 0; i < 5; i++)
            {
                dx += numbers.get(i * 2).intValue();
                dy += numbers.get(i * 2 + 1).intValue();
            }
            List<Number> first = numbers.subList(0, 6);
            List<Number> second = Arrays.asList(numbers.get(6), numbers.get(7), numbers.get(8), 
                    numbers.get(9), (Math.abs(dx) > Math.abs(dy) ? numbers.get(10) : -dx), 
                    (Math.abs(dx) > Math.abs(dy) ? -dy : numbers.get(10)));
            addCommandList(Arrays.asList(first, second), CharStringCommand.getInstance(8));
            break;
        }

        case HSTEMHM:
            numbers = clearStack(numbers, numbers.size() % 2 != 0);
            expandStemHints(numbers, true);
            break; 

        case HINTMASK:
        case CNTRMASK:
            numbers = clearStack(numbers, numbers.size() % 2 != 0);
            if (!numbers.isEmpty())
            {
                expandStemHints(numbers, false);
            }
            break; 

        case VSTEMHM:
            numbers = clearStack(numbers, numbers.size() % 2 != 0);
            expandStemHints(numbers, false);
            break; 

        case RCURVELINE:
            if (numbers.size() >= 2)
            {
                addCommandList(split(numbers.subList(0, numbers.size() - 2), 6),
                        CharStringCommand.getInstance(8));
                addCommand(numbers.subList(numbers.size() - 2, numbers.size()),
                        CharStringCommand.getInstance(5));
            }
            break; 

        case RLINECURVE:
            if (numbers.size() >= 6)
            {
                addCommandList(split(numbers.subList(0, numbers.size() - 6), 2),
                        CharStringCommand.getInstance(5));
                addCommand(numbers.subList(numbers.size() - 6, numbers.size()),
                        CharStringCommand.getInstance(8));
            }
            break; 

        case VVCURVETO:
            drawCurve(numbers, false);
            break; 

        case HHCURVETO:
            drawCurve(numbers, true);
            break;

        default:
            addCommand(numbers, command);
        }
        
        return null;
    }

    private List<Number> clearStack(List<Number> numbers, boolean flag)
    {
        if (type1Sequence.isEmpty())
        {
            if (flag)
            {
                addCommand(Arrays.asList((Number) 0f, numbers.get(0).floatValue() + nominalWidthX),
                        CharStringCommand.getInstance(13));
                numbers = numbers.subList(1, numbers.size());
            }
            else
            {
                addCommand(Arrays.asList((Number) 0f, defWidthX),
                        CharStringCommand.getInstance(13));
            }
        }
        return numbers;
    }

    /**
     * @param numbers  
     * @param horizontal 
     */
    private void expandStemHints(List<Number> numbers, boolean horizontal)
    {
        // TODO
    }

    private void markPath()
    {
        if (pathCount > 0)
        {
            closeCharString2Path();
        }
        pathCount++;
    }

    private void closeCharString2Path()
    {
        CharStringCommand command = pathCount > 0 ? (CharStringCommand) type1Sequence
                .get(type1Sequence.size() - 1)
                : null;

        if (command != null && command.getType1KeyWord() != Type1KeyWord.CLOSEPATH)
        {
            addCommand(Collections.<Number> emptyList(), Type1KeyWord.CLOSEPATH.getInstance());
        }
    }

    private void drawAlternatingLine(List<Number> numbers, boolean horizontal)
    {
        while (!numbers.isEmpty())
        {
            addCommand(numbers.subList(0, 1), CharStringCommand.getInstance(
                    horizontal ? 6 : 7));
            numbers = numbers.subList(1, numbers.size());
            horizontal = !horizontal;
        }
    }

    private void drawAlternatingCurve(List<Number> numbers, boolean horizontal)
    {
        while (numbers.size() >= 4)
        {
            boolean last = numbers.size() == 5;
            if (horizontal)
            {
                addCommand(Arrays.asList(numbers.get(0), 0,
                        numbers.get(1), numbers.get(2), last ? numbers.get(4)
                                : 0, numbers.get(3)),
                        CharStringCommand.getInstance(8));
            } 
            else
            {
                addCommand(Arrays.asList(0, numbers.get(0),
                        numbers.get(1), numbers.get(2), numbers.get(3),
                        last ? numbers.get(4) : 0),
                        CharStringCommand.getInstance(8));
            }
            numbers = numbers.subList(last ? 5 : 4, numbers.size());
            horizontal = !horizontal;
        }
    }

    private void drawCurve(List<Number> numbers, boolean horizontal)
    {
        while (numbers.size() >= 4)
        {
            boolean first = numbers.size() % 4 == 1;

            if (horizontal)
            {
                addCommand(Arrays.asList(numbers.get(first ? 1 : 0),
                        first ? numbers.get(0) : 0, numbers
                                .get(first ? 2 : 1),
                        numbers.get(first ? 3 : 2), numbers.get(first ? 4 : 3),
                        0), CharStringCommand.getInstance(8));
            } 
            else
            {
                addCommand(Arrays.asList(first ? numbers.get(0) : 0, numbers.get(first ? 1 : 0), numbers
                        .get(first ? 2 : 1), numbers.get(first ? 3 : 2),
                        0, numbers.get(first ? 4 : 3)),
                        CharStringCommand.getInstance(8));
            }
            numbers = numbers.subList(first ? 5 : 4, numbers.size());
        }
    }

    private void addCommandList(List<List<Number>> numbers, CharStringCommand command)
    {
        for (List<Number> ns : numbers)
        {
            addCommand(ns, command);
        }
    }

    private void addCommand(List<Number> numbers, CharStringCommand command)
    {
        type1Sequence.addAll(numbers);
        type1Sequence.add(command);
    }

    private static <E> List<List<E>> split(List<E> list, int size)
    {
        int listSize = list.size() / size;
        List<List<E>> result = new ArrayList<List<E>>(listSize);
        for (int i = 0; i < listSize; i++)
        {
            result.add(list.subList(i * size, (i + 1) * size));
        }
        return result;
    }
}
