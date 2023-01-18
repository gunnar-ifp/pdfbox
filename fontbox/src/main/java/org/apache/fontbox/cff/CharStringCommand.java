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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * This class represents a CharStringCommand.
 * <p>
 * Instance of this class are static and can be compared using {code}=={code}.
 * 
 * @author Villu Ruusmann
 */
public class CharStringCommand
{
    private static final CharStringCommand UNKNOWN = new CharStringCommand(null, null);
    
    private static final CharStringCommand[] SINGLE;
    private static final CharStringCommand[] DOUBLE;
    static {
        Map<Integer, CharStringCommand> all = new HashMap<>(80);
        Map<Integer, Type2KeyWord> temp = new HashMap<>(128);
        for ( Type2KeyWord t : Type2KeyWord.values() ) temp.put(code(t), t);
        for ( Type1KeyWord t : Type1KeyWord.values() ) {
            all.put(code(t), new CharStringCommand(t, temp.get(code(t))));
        }
        for ( Type2KeyWord t : Type2KeyWord.values() ) {
            if ( !all.containsKey(code(t)) )  {
                all.put(code(t), new CharStringCommand(null, temp.get(code(t))));
            }
        }
        
        // Note: this code assumes all double byte commands have the same b0.
        // If there is a perfect 1:1 hash function to map b0 and b1 into 6 bit,
        // then we could maybe use a single array (b0 is 27 values 5 bits and b1 is 32 values in 6 bits).
        int max0 = -1, max1 = -1;
        for ( CharStringCommand c : all.values() ) {
            max0 = Math.max(max0, c.getByte0());
            if ( c.length()==2 ) max1 = Math.max(max1, c.getByte1());
        }
        SINGLE = new CharStringCommand[max0 + 1];
        DOUBLE = new CharStringCommand[max1 + 1];
        Arrays.fill(SINGLE, UNKNOWN);
        Arrays.fill(DOUBLE, UNKNOWN);
        for ( CharStringCommand c : all.values() ) {
            if ( c.length()==1 ) SINGLE[c.getByte0()] = c; else DOUBLE[c.getByte1()] = c;
        }
    }
    
    private final Type1KeyWord type1;
    private final Type2KeyWord type2;

    /**
     * Returns the command for the given single byte command code.
     * 
     * @param b0 value
     */
    public static CharStringCommand getInstance(int b0)
    {
        return b0<0 || b0>=SINGLE.length ? UNKNOWN : SINGLE[b0]; 
    }

    
    /**
     * Returns the command for the given double byte command code.
     * 
     * @param b0 value1
     * @param b1 value2
     */
    public static CharStringCommand getInstance(int b0, int b1)
    {
        return b0!=12 || b1<0 || b1>=DOUBLE.length ? UNKNOWN : DOUBLE[b1]; 
    }

    
    /**
     * @param b0 value1
     * @param b1 value2
     * @param values additional values
     */
    private CharStringCommand(Type1KeyWord type1, Type2KeyWord type2)
    {
        this.type1 = type1;
        this.type2 = type2;
    }
    

    public boolean isType1()
    {
        return type1!=null;
    }
    
    
    public boolean isType2()
    {
        return type2!=null;
    }
    
    
    public Type1KeyWord getType1KeyWord()
    {
        return type1;
    }
    
    
    public Type2KeyWord getType2KeyWord()
    {
        return type2;
    }

    
    public int getByte0()
    {
        return getKey().getByte0();
    }
    
    
    public int getByte1() throws ArrayIndexOutOfBoundsException
    {
        return getKey().getByte1();
    }
    

    public int length()
    {
        return getKey().length();
    }
    
    
    @Override
    public String toString()
    {
        return getKey().toString();
    }


    @Override
    public int hashCode()
    {
        //return super.hashCode();
        return getKey().hashCode();
    }


    @Override
    public boolean equals(Object object)
    {
        return super.equals(object);
        
    }
    
    
    private Key getKey()
    {
        return type2==null ? type1==null ? Key.UNKNOWN : type1 : type2;
    }

    
    private static int code(Key c)
    {
        return c.length()==1 ? c.getByte0() : (c.getByte1() + 1 << 8 | c.getByte0());
    }
    
    
    /**
     * Type1 vocabulary.
     */
    public enum Type1KeyWord implements Key
    {
        HSTEM          (1),
        VSTEM          (3),
        VMOVETO        (4),
        RLINETO        (5),
        HLINETO        (6),
        VLINETO        (7),
        RRCURVETO      (8),
        CLOSEPATH      (9),
        CALLSUBR       (10),
        RETURN         (11),
        ESCAPE         (12),
        DOTSECTION     (12, 0),
        VSTEM3         (12, 1),
        HSTEM3         (12, 2),
        SEAC           (12, 6),
        SBW            (12, 7),
        DIV            (12, 12),
        CALLOTHERSUBR  (12, 16),
        POP            (12, 17),
        SETCURRENTPOINT(12, 33),
        HSBW           (13),
        ENDCHAR        (14),
        RMOVETO        (21),
        HMOVETO        (22),
        VHCURVETO      (30),
        HVCURVETO      (31);
        
        final int b0, b1;
        
        private Type1KeyWord(int b0) {
            this(b0, -1);
        }
        
        private Type1KeyWord(int b0, int b1) {
            this.b0 = b0;
            this.b1 = b1;
        }
        
        @Override
        public int getByte0() {
            return b0;
        }
        
        @Override
        public int getByte1() throws IndexOutOfBoundsException {
            if ( b1==-1 ) throw new ArrayIndexOutOfBoundsException("1");
            return b1;
        }
        
        @Override
        public int length() {
            return b1==-1 ? 1 : 2;
        }
        
        @Override
        public String toString() {
            return super.toString().toLowerCase(Locale.ROOT);
        }
    }
        

    /**
     * Type2 vocabulary.
     */
    public enum Type2KeyWord implements Key
    {
        HSTEM          (1),
        VSTEM          (3),
        VMOVETO        (4),
        RLINETO        (5),
        HLINETO        (6),
        VLINETO        (7),
        RRCURVETO      (8),
        CALLSUBR       (10),
        RETURN         (11),
        ESCAPE         (12),
        AND            (12, 3),
        OR             (12, 4),
        NOT            (12, 5),
        ABS            (12, 9),
        ADD            (12, 10),
        SUB            (12, 11),
        DIV            (12, 12),
        NEG            (12, 14),
        EQ             (12, 15),
        DROP           (12, 18),
        PUT            (12, 20),
        GET            (12, 21),
        IFELSE         (12, 22),
        RANDOM         (12, 23),
        MUL            (12, 24),
        SQRT           (12, 26),
        DUP            (12, 27),
        EXCH           (12, 28),
        INDEX          (12, 29),
        ROLL           (12, 30),
        HFLEX          (12, 34),
        FLEX           (12, 35),
        HFLEX1         (12, 36),
        FLEX1          (12, 37),
        ENDCHAR        (14),
        HSTEMHM        (18),
        HINTMASK       (19),
        CNTRMASK       (20),
        RMOVETO        (21),
        HMOVETO        (22),
        VSTEMHM        (23),
        RCURVELINE     (24),
        RLINECURVE     (25),
        VVCURVETO      (26),
        HHCURVETO      (27),
        SHORTINT       (28),
        CALLGSUBR      (29),
        VHCURVETO      (30),
        HVCURVETO      (31);
        
        final int b0, b1;
        
        private Type2KeyWord(int b0) {
            this(b0, -1);
        }
        
        private Type2KeyWord(int b0, int b1) {
            this.b0 = b0;
            this.b1 = b1;
        }
        
        @Override
        public int getByte0() {
            return b0;
        }
        
        @Override
        public int getByte1() throws IndexOutOfBoundsException {
            if ( b1==-1 ) throw new ArrayIndexOutOfBoundsException("1");
            return b1;
        }
        
        @Override
        public int length() {
            return b1==-1 ? 1 : 2;
        }
        
        @Override
        public String toString() {
            return super.toString().toLowerCase(Locale.ROOT);
        }
        
    }

    
    private interface Key
    {
        public static final Key UNKNOWN = new Key() {
            @Override
            public int length() {
                return 2;
            }
            
            @Override
            public int getByte1() {
                return 99;
            }
            
            @Override
            public int getByte0() {
                return 99;
            }
            
            @Override
            public String toString() {
                return "unknown command";
            }
        };
        
        int getByte0();
        
        int getByte1() throws ArrayIndexOutOfBoundsException;
        
        int length();
        
        default CharStringCommand getInstance() {
            return length()==1 ? CharStringCommand.getInstance(getByte0()) : CharStringCommand.getInstance(getByte0(), getByte1());
        }
    }
}
