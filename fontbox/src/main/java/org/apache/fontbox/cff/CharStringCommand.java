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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class represents a CharStringCommand.
 * 
 * @author Villu Ruusmann
 */
public class CharStringCommand
{
    private static final Map<IntArrayKey, CharStringCommand> INTERN = new HashMap<>(80);
    
    private final int[] keyValues;

    /**
     * Constructor with one value.
     * 
     * @param b0 value
     */
    public static CharStringCommand get(int b0)
    {
        return get(new int[] { b0 });
    }

    
    /**
     * Constructor with two values.
     * 
     * @param b0 value1
     * @param b1 value2
     */
    public static CharStringCommand get(int b0, int b1)
    {
        return get(new int[] { b0, b1 });
    }

    
    /**
     * Constructor with an array as values.
     * 
     * @param values array of values
     */
    public static CharStringCommand get(int[] values)
    {
        CharStringCommand cached = INTERN.get(new IntArrayKey(values));
        return cached==null ? new CharStringCommand(values) : cached;
    }

    
    /**
     * Constructor with an array as values.
     * 
     * @param values array of values
     */
    private CharStringCommand(int[] values)
    {
        this.keyValues = values;
    }
    
    
    public int getValue(int index) throws ArrayIndexOutOfBoundsException
    {
        return keyValues[index];
    }
    
    
    @Override
    public String toString()
    {
        String str = TYPE2_VOCABULARY.get(this);
        if (str == null)
        {
            str = TYPE1_VOCABULARY.get(this);
        }
        if (str == null)
        {
            return Arrays.toString(keyValues) + '|';
        }
        return str + '|';
    }


    @Override
    public int hashCode()
    {
        if (keyValues[0] == 12 && keyValues.length > 1)
        {
            return keyValues[0] ^ keyValues[1];
        }
        return keyValues[0];        
    }


    @Override
    public boolean equals(Object object)
    {
        if (object instanceof CharStringCommand)
        {
            CharStringCommand that = (CharStringCommand) object;
            if (keyValues[0] == 12 && that.keyValues[0] == 12)
            {
                if (keyValues.length > 1 && that.keyValues.length > 1)
                {
                    return keyValues[1] == that.keyValues[1];
                }
                return keyValues.length == that.keyValues.length;
            }
            return keyValues[0] == that.keyValues[0];
        }
        return false;        
    }
    
    
    private CharStringCommand intern()
    {
        IntArrayKey key = new IntArrayKey(keyValues);
        CharStringCommand cached = INTERN.putIfAbsent(key, this);
        return cached==null ? this : cached;
    }
    

    /**
     * A static class to hold one or more int values as key for a hashmap. 
     */
    private static class IntArrayKey
    {
        private final int[] values;
        private final int hashcode;

        public IntArrayKey(int[] values)
        {
            this.values = values;
            this.hashcode = Arrays.hashCode(values);
        }

        @Override
        public int hashCode()
        {
            return hashcode;
        }

        @Override
        public boolean equals(Object object)
        {
            return object instanceof IntArrayKey && Arrays.equals(values, ((IntArrayKey)object).values);
        }
    }

    /**
     * A map with the Type1 vocabulary.
     */
    public static final Map<CharStringCommand, String> TYPE1_VOCABULARY;

    static
    {
        Map<CharStringCommand, String> map = new LinkedHashMap<>(26);
        map.put(get(1).intern(), "hstem");
        map.put(get(3).intern(), "vstem");
        map.put(get(4).intern(), "vmoveto");
        map.put(get(5).intern(), "rlineto");
        map.put(get(6).intern(), "hlineto");
        map.put(get(7).intern(), "vlineto");
        map.put(get(8).intern(), "rrcurveto");
        map.put(get(9).intern(), "closepath");
        map.put(get(10).intern(), "callsubr");
        map.put(get(11).intern(), "return");
        map.put(get(12).intern(), "escape");
        map.put(get(12, 0).intern(), "dotsection");
        map.put(get(12, 1).intern(), "vstem3");
        map.put(get(12, 2).intern(), "hstem3");
        map.put(get(12, 6).intern(), "seac");
        map.put(get(12, 7).intern(), "sbw");
        map.put(get(12, 12).intern(), "div");
        map.put(get(12, 16).intern(), "callothersubr");
        map.put(get(12, 17).intern(), "pop");
        map.put(get(12, 33).intern(), "setcurrentpoint");
        map.put(get(13).intern(), "hsbw");
        map.put(get(14).intern(), "endchar");
        map.put(get(21).intern(), "rmoveto");
        map.put(get(22).intern(), "hmoveto");
        map.put(get(30).intern(), "vhcurveto");
        map.put(get(31).intern(), "hvcurveto");

        TYPE1_VOCABULARY = Collections.unmodifiableMap(map);
    }

    /**
     * A map with the Type2 vocabulary.
     */
    public static final Map<CharStringCommand, String> TYPE2_VOCABULARY;

    static
    {
        Map<CharStringCommand, String> map = new LinkedHashMap<>(48);
        map.put(get(1).intern(), "hstem");
        map.put(get(3).intern(), "vstem");
        map.put(get(4).intern(), "vmoveto");
        map.put(get(5).intern(), "rlineto");
        map.put(get(6).intern(), "hlineto");
        map.put(get(7).intern(), "vlineto");
        map.put(get(8).intern(), "rrcurveto");
        map.put(get(10).intern(), "callsubr");
        map.put(get(11).intern(), "return");
        map.put(get(12).intern(), "escape");
        map.put(get(12, 3).intern(), "and");
        map.put(get(12, 4).intern(), "or");
        map.put(get(12, 5).intern(), "not");
        map.put(get(12, 9).intern(), "abs");
        map.put(get(12, 10).intern(), "add");
        map.put(get(12, 11).intern(), "sub");
        map.put(get(12, 12).intern(), "div");
        map.put(get(12, 14).intern(), "neg");
        map.put(get(12, 15).intern(), "eq");
        map.put(get(12, 18).intern(), "drop");
        map.put(get(12, 20).intern(), "put");
        map.put(get(12, 21).intern(), "get");
        map.put(get(12, 22).intern(), "ifelse");
        map.put(get(12, 23).intern(), "random");
        map.put(get(12, 24).intern(), "mul");
        map.put(get(12, 26).intern(), "sqrt");
        map.put(get(12, 27).intern(), "dup");
        map.put(get(12, 28).intern(), "exch");
        map.put(get(12, 29).intern(), "index");
        map.put(get(12, 30).intern(), "roll");
        map.put(get(12, 34).intern(), "hflex");
        map.put(get(12, 35).intern(), "flex");
        map.put(get(12, 36).intern(), "hflex1");
        map.put(get(12, 37).intern(), "flex1");
        map.put(get(14).intern(), "endchar");
        map.put(get(18).intern(), "hstemhm");
        map.put(get(19).intern(), "hintmask");
        map.put(get(20).intern(), "cntrmask");
        map.put(get(21).intern(), "rmoveto");
        map.put(get(22).intern(), "hmoveto");
        map.put(get(23).intern(), "vstemhm");
        map.put(get(24).intern(), "rcurveline");
        map.put(get(25).intern(), "rlinecurve");
        map.put(get(26).intern(), "vvcurveto");
        map.put(get(27).intern(), "hhcurveto");
        map.put(get(28).intern(), "shortint");
        map.put(get(29).intern(), "callgsubr");
        map.put(get(30).intern(), "vhcurveto");
        map.put(get(31).intern(), "hvcurveto");

        TYPE2_VOCABULARY = Collections.unmodifiableMap(map);
    }
}
