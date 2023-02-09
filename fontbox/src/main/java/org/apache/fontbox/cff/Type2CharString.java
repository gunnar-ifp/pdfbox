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

import org.apache.fontbox.cff.CharStringCommand.CommandProvider;
import org.apache.fontbox.cff.CharStringCommand.Type1Command;
import org.apache.fontbox.cff.CharStringCommand.Type2Command;
import org.apache.fontbox.type1.Type1CharStringReader;

/**
 * Represents a Type 2 CharString by converting it into an equivalent Type 1 CharString.
 * 
 * @author Villu Ruusmann
 * @author John Hewson
 */
public class Type2CharString extends Type1CharString
{
    private final int gid;
    private final byte[][] gsubrs;
    private final int defaultWidthX, nominalWidthX;

    /**
     * Constructor.
     * @param font Parent CFF font
     * @param fontName font name
     * @param glyphName glyph name (or CID as hex string)
     * @param gid GID
     * @param defaultWidthX default width
     * @param nominalWidthX nominal width
     */
    public Type2CharString(Type1CharStringReader font, String fontName, String glyphName, int gid,
        byte[] bytes, byte[][] subrs, byte[][] gsubrs, int defaultWidthX, int nominalWidthX)
    {
        super(font, fontName, glyphName, bytes, subrs);
        this.gid = gid;
        this.gsubrs = gsubrs;
        this.defaultWidthX = defaultWidthX;
        this.nominalWidthX = nominalWidthX;
    }

    /**
     * Return the GID (glyph id) of this charstring.
     */
    public int getGID()
    {
        return gid;
    }

    @Override
    public CommandProvider<Type1Command> getType1Stream()
    {
        return consumer -> getType2Stream().stream(new Type2ToType1Converter(defaultWidthX, nominalWidthX, consumer));
    }

    
    public CommandProvider<Type2Command> getType2Stream()
    {
        return new Type2CharStringParser(fontName, glyphName, bytes, subrs, gsubrs);
    }
    
}
