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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;


/**
 * This class contains constants for Type1 and Type2 charstring processing.
 * 
 * @author Gunnar Brand
 */
public class CharStringCommand
{
    public final static int TYPE1_OPERAND_STACK_LIMIT   = 24;
    public final static int TYPE1_CALL_LIMIT            = 10;
    public final static int TYPE2_OPERAND_STACK_LIMIT   = 48;
    public final static int TYPE2_CALL_LIMIT            = 10;
    public final static int TYPE2_STEM_HINT_LIMIT       = 96;
    public final static int TYPE2_TRANSIENT_ARRAY_LIMIT = 32;
    public final static int CFF2_OPERAND_STACK_LIMIT    = 513;
    public final static int CFF2_CALL_LIMIT             = 10;
    public final static int CFF2_STEM_HINT_LIMIT        = 96;


    @FunctionalInterface
    public interface CommandConsumer<T extends Enum<T>>
    {
        void apply(T command, CharStringOperandStack operands) throws IOException;
        
        default void end() throws IOException {
        }
    }

    
    @FunctionalInterface
    public interface CommandProvider<T extends Enum<T>>
    {
        void stream(CommandConsumer<T> consumer) throws IOException;
    }
    
    
    
    /** Single byte command */
    public final static int
        HSTEM           =  1,
        VSTEM           =  3,
        VMOVETO         =  4,
        RLINETO         =  5,
        HLINETO         =  6,
        VLINETO         =  7,
        RRCURVETO       =  8,
        CLOSEPATH       =  9,
        CALLSUBR        = 10,
        RETURN          = 11,
        ESCAPE          = 12,
        HSBW            = 13,
        ENDCHAR         = 14,
        VSINDEX         = 15,
        BLEND           = 16,
        HSTEMHM         = 18,
        HINTMASK        = 19,
        CNTRMASK        = 20,
        RMOVETO         = 21,
        HMOVETO         = 22,
        VSTEMHM         = 23,
        RCURVELINE      = 24,
        RLINECURVE      = 25,
        VVCURVETO       = 26,
        HHCURVETO       = 27,
        SHORTINT        = 28,
        CALLGSUBR       = 29,
        VHCURVETO       = 30,
        HVCURVETO       = 31,
        NUMBER_START    = 32,
        NUMBER_END      = 255;

    
    /** Escaped double byte command */
    public final static int
        DOTSECTION      =  0,
        VSTEM3          =  1,
        HSTEM3          =  2,
        AND             =  3,
        OR              =  4,
        NOT             =  5,
        SEAC            =  6,
        SBW             =  7,
        ABS             =  9,
        ADD             = 10,
        SUB             = 11,
        DIV             = 12,
        NEG             = 14,
        EQ              = 15,
        CALLOTHERSUBR   = 16,
        POP             = 17,
        DROP            = 18,
        PUT             = 20,
        GET             = 21,
        IFELSE          = 22,
        RANDOM          = 23,
        MUL             = 24,
        SQRT            = 26,
        DUP             = 27,
        EXCH            = 28,
        INDEX           = 29,
        ROLL            = 30,
        SETCURRENTPOINT = 33,
        HFLEX           = 34,
        FLEX            = 35,
        HFLEX1          = 36,
        FLEX1           = 37;

    
    public static <T extends Enum<T>> T getEnum(Class<T> enumType, String name)
    {
        try {
            return Enum.valueOf(enumType, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    
    /**
     * Known Type1 commands with any command handled by the parser omitted.
     */
    public enum Type1Command
    {
        // Commands for starting and finishing
        ENDCHAR,
        HSBW,
        SEAC,
        SBW,
        // Path construction commands
        CLOSEPATH,
        HLINETO,
        HMOVETO,
        HVCURVETO,
        RLINETO,
        RMOVETO,
        RRCURVETO,
        VHCURVETO,
        VLINETO,
        VMOVETO,
        // Hint commands
        DOTSECTION,
        HSTEM,
        HSTEM3,
        VSTEM,
        VSTEM3,
        // Subroutine commands
        /**
         * Special handling with extra PS operand stack:
         * bottom is othersubr# which must be {@link CharStringOperandStack#pull() pulled}!
         */
        CALLOTHERSUBR,
        SETCURRENTPOINT;
    }
    
    
    /**
     * Known Type2 commands with any command handled by the parser omitted.
     */
    public enum Type2Command
    {
        // Path construction operators
        /** If first stack clearing op, can contain odd number of arguments, with width argument first. */
        RMOVETO,
        /** If first stack clearing op, can contain odd number of arguments, with width argument first. */
        HMOVETO,
        /** If first stack clearing op, can contain odd number of arguments, with width argument first. */
        VMOVETO,
        RLINETO,
        HLINETO,
        VLINETO,
        RRCURVETO,
        HHCURVETO,
        HVCURVETO,
        RCURVELINE,
        RLINECURVE,
        VHCURVETO,
        VVCURVETO,
        FLEX,
        HFLEX,
        HFLEX1,
        FLEX1,

        // Operator for Finishing a Path
        /** If first stack clearing op, can contain odd number of arguments, with width argument first. */
        ENDCHAR,

        // Hint Operators
        /** If first stack clearing op, can contain odd number of arguments, with width argument first. */
        HSTEM,
        /** If first stack clearing op, can contain odd number of arguments, with width argument first. */
        VSTEM,
        /** If first stack clearing op, can contain odd number of arguments, with width argument first. */
        HSTEMHM,
        /** If first stack clearing op, can contain odd number of arguments, with width argument first. */
        VSTEMHM,
        /**
         * If first stack clearing op, can contain odd number of arguments, with width argument first.<br>
         * Note: Hintmask not supplied.<br>
         * Note: Streamer will send VSTEM/VSTEMHM command if omitted in charstring, but given on stack
         */
        HINTMASK,
        /**
         * If first stack clearing op, can contain odd number of arguments, with width argument first.<br>
         * Note: Contourmask not supplied.<br>
         * Note: Streamer will send VSTEM/VSTEMHM command if omitted in charstring, but given on stack
         */
        CNTRMASK,
        
        /** Deprecated */
        DOTSECTION;
    }    
    
    
    
    /**
     * Known CFF2 commands with any command handled by the parser omitted.
     */
    public enum CFF2Command
    {
        // Path construction operators
        RMOVETO,
        HMOVETO,
        VMOVETO,
        RLINETO,
        HLINETO,
        VLINETO,
        RRCURVETO,
        HHCURVETO,
        HVCURVETO,
        RCURVELINE,
        RLINECURVE,
        VHCURVETO,
        VVCURVETO,
        FLEX,
        HFLEX,
        HFLEX1,
        FLEX1,

        // Hint Operators
        HSTEM,
        VSTEM,
        HSTEMHM,
        VSTEMHM,
        /**
         * Note: Hintmask not supplied.<br>
         * Note: Streamer will send VSTEM/VSTEMHM command if omitted in charstring, but given on stack
         */
        HINTMASK,
        /**
         * Note: Contourmask not supplied.<br>
         * Note: Streamer will send VSTEM/VSTEMHM command if omitted in charstring, but given on stack
         */
        CNTRMASK,
        
        VSINDEX,
        BLEND;
    }    

    
    /**
     * Full (internal) Type1 command set.
     */
    enum Type1Operator
    {
        // Commands for starting and finishing
        ENDCHAR        (CharStringCommand.ENDCHAR,         0, false, false),
        HSBW           (CharStringCommand.HSBW,            2, false, false),
        SEAC           (CharStringCommand.SEAC,            5, false, true),
        SBW            (CharStringCommand.SBW,             4, false, true),
        // Path construction commands
        CLOSEPATH      (CharStringCommand.CLOSEPATH,       0, false, false),
        HLINETO        (CharStringCommand.HLINETO,         1, false, false),
        HMOVETO        (CharStringCommand.HMOVETO,         1, false, false),
        HVCURVETO      (CharStringCommand.HVCURVETO,       4, false, false),
        RLINETO        (CharStringCommand.RLINETO,         2, false, false),
        RMOVETO        (CharStringCommand.RMOVETO,         2, false, false),
        RRCURVETO      (CharStringCommand.RRCURVETO,       6, false, false),
        VHCURVETO      (CharStringCommand.VHCURVETO,       4, false, false),
        VLINETO        (CharStringCommand.VLINETO,         1, false, false),
        VMOVETO        (CharStringCommand.VMOVETO,         1, false, false),
        // hint commands
        DOTSECTION     (CharStringCommand.DOTSECTION,      0, false, true),
        HSTEM          (CharStringCommand.HSTEM,           2, false, false),
        HSTEM3         (CharStringCommand.HSTEM3,          6, false, true),
        VSTEM          (CharStringCommand.VSTEM,           2, false, false),
        VSTEM3         (CharStringCommand.VSTEM3,          6, false, true),
        // Arithmetic Command
        DIV            (CharStringCommand.DIV,             2, true,  true),
        // Subroutine Commands
        CALLOTHERSUBR  (CharStringCommand.CALLOTHERSUBR,  -2, true,  true),
        CALLSUBR       (CharStringCommand.CALLSUBR,       -1, true,  false),
        POP            (CharStringCommand.POP,             0, true,  true),
        RETURN         (CharStringCommand.RETURN,          0, true,  false),
        SETCURRENTPOINT(CharStringCommand.SETCURRENTPOINT, 2, false, true);
        
        private final int byte0, byte1, argCount;
        final boolean keepStack;
        final Type1Command command = getEnum(Type1Command.class, name());
        
        private Type1Operator(int b, int argCount, boolean keepStack, boolean escaped) {
            this.byte0     = escaped ? ESCAPE : b;
            this.byte1     = escaped ? b      : 0;
            this.argCount  = argCount;
            this.keepStack = keepStack;
        }
        
        public int getArgumentCount() {
            return Math.abs(argCount);
        }
        
        public boolean isTopArguments() {
            return argCount<0;
        }
        
        @Override
        public String toString() {
            return super.toString().toLowerCase(Locale.ROOT);
        }
        
        public static Type1Operator get(int byte0) {
            return byte0<0 || byte0>=TYPE1_SINGLE.length ? null : TYPE1_SINGLE[byte0];
        }
    
        public static Type1Operator get(int byte0, int byte1) {
            return byte0==ESCAPE
                ? (byte1<0 || byte1>=TYPE1_DOUBLE.length ? null : TYPE1_DOUBLE[byte1])
                : (byte0<0 || byte0>=TYPE1_SINGLE.length ? null : TYPE1_SINGLE[byte0]);
        }
        
    private final static Type1Operator[] TYPE1_SINGLE, TYPE1_DOUBLE;
    static {
        TYPE1_SINGLE = new Type1Operator[32];
        TYPE1_DOUBLE = new Type1Operator[38];
        for ( Type1Operator w : values() ) {
            if ( w.byte0!=ESCAPE ) TYPE1_SINGLE[w.byte0] = w; else TYPE1_DOUBLE[w.byte1] = w;
        }
    }
        
    }
        

    /**
     * Full (internal) Type2 command set.
     */
    enum Type2Operator
    {
        /*
         * The first stack-clearing operator, which must be one of
         * hstem, hstemhm, vstem, vstemhm, cntrmask, hintmask, hmoveto, vmoveto, rmoveto, or endchar,
         * takes an additional argument — the width, hich may be expressed as zero or one numeric argument.
         */

        // Path construction operators
        /** If first stack clearing op, can contain odd number of arguments, with width argument first. */
        RMOVETO        (CharStringCommand.RMOVETO,    false, "2"),
        /** If first stack clearing op, can contain odd number of arguments, with width argument first. */
        HMOVETO        (CharStringCommand.HMOVETO,    false, "1"),
        /** If first stack clearing op, can contain odd number of arguments, with width argument first. */
        VMOVETO        (CharStringCommand.VMOVETO,    false, "1"),
        RLINETO        (CharStringCommand.RLINETO,    false, "2+"),
        HLINETO        (CharStringCommand.HLINETO,    false, "1  2* | 2+"),
        VLINETO        (CharStringCommand.VLINETO,    false, "1  2* | 2+"),
        RRCURVETO      (CharStringCommand.RRCURVETO,  false, "6+"),
        HHCURVETO      (CharStringCommand.HHCURVETO,  false, "1? 4+"),
        HVCURVETO      (CharStringCommand.HVCURVETO,  false, "4  8* 1? | 8+ 1?"),
        RCURVELINE     (CharStringCommand.RCURVELINE, false, "6+ 2"),
        RLINECURVE     (CharStringCommand.RLINECURVE, false, "2+ 6"),
        VHCURVETO      (CharStringCommand.VHCURVETO,  false, "4  8* 1? | 8+ 1?"),
        VVCURVETO      (CharStringCommand.VVCURVETO,  false, "1? 4+"),
        FLEX           (CharStringCommand.FLEX,       true,  "13"),
        HFLEX          (CharStringCommand.HFLEX,      true,  "7"),
        HFLEX1         (CharStringCommand.HFLEX1,     true,  "9"),
        FLEX1          (CharStringCommand.FLEX1,      true,  "11"),

        // Operator for Finishing a Path
        ENDCHAR        (CharStringCommand.ENDCHAR,    false, "0"),

        // Hint Operators
        /** If first stack clearing op, can contain odd number of arguments, with width argument first. */
        HSTEM          (CharStringCommand.HSTEM,      false, "2  2*"),
        /** If first stack clearing op, can contain odd number of arguments, with width argument first. */
        VSTEM          (CharStringCommand.VSTEM,      false, "2  2*"),
        /** If first stack clearing op, can contain odd number of arguments, with width argument first. */
        HSTEMHM        (CharStringCommand.HSTEMHM,    false, "2  2*"),
        /** If first stack clearing op, can contain odd number of arguments, with width argument first. */
        VSTEMHM        (CharStringCommand.VSTEMHM,    false, "2  2*"),
        /** If first stack clearing op, can contain odd number of arguments, with width argument first.
         * Note: streamer will send VSTEM/VSTEMHM command if omitted in charstring, but given on stack */
        HINTMASK       (CharStringCommand.HINTMASK,   false, "0"),
        /** If first stack clearing op, can contain odd number of arguments, with width argument first.
         * Note: streamer will send VSTEM/VSTEMHM command if omitted in charstring, but given on stack */
        CNTRMASK       (CharStringCommand.CNTRMASK,   false, "0"),

        // Deprecated
        DOTSECTION     (CharStringCommand.DOTSECTION, true,  "0"),        
        
        // Arithmetic Operators (keep stack)
        ABS            (CharStringCommand.ABS,        true,  1),
        ADD            (CharStringCommand.ADD,        true,  2),
        SUB            (CharStringCommand.SUB,        true,  2),
        DIV            (CharStringCommand.DIV,        true,  2),
        NEG            (CharStringCommand.NEG,        true,  1),
        RANDOM         (CharStringCommand.RANDOM,     true,  0),
        MUL            (CharStringCommand.MUL,        true,  2),
        SQRT           (CharStringCommand.SQRT,       true,  1),
        DROP           (CharStringCommand.DROP,       true,  1),
        EXCH           (CharStringCommand.EXCH,       true,  2),
        INDEX          (CharStringCommand.INDEX,      true,  2),
        ROLL           (CharStringCommand.ROLL,       true,  2),
        DUP            (CharStringCommand.DUP,        true,  1),

        // Storage Operators (keep stack)
        PUT            (CharStringCommand.PUT,        true,  2),
        GET            (CharStringCommand.GET,        true,  1),

        // Conditional Operators (keep stack)
        AND            (CharStringCommand.AND,        true,  2),
        OR             (CharStringCommand.OR,         true,  2),
        NOT            (CharStringCommand.NOT,        true,  1),
        EQ             (CharStringCommand.EQ,         true,  2),
        IFELSE         (CharStringCommand.IFELSE,     true,  4),

        // Subroutine Operators (keep stack)
        CALLSUBR       (CharStringCommand.CALLSUBR,   false, 1),
        CALLGSUBR      (CharStringCommand.CALLGSUBR,  false, 1),
        RETURN         (CharStringCommand.RETURN,     false, 0);
 
        private final int byte0, byte1;
        private final int[][][] ruleSets;
        private final int argCount;
        final boolean keepStack;
        final Type2Command command;
        
        private Type2Operator(int b, boolean escaped, String rules) {
            this.byte0 = escaped ? ESCAPE : b;
            this.byte1 = escaped ? b      : 0;
            this.ruleSets = CharStringCommand.parse(rules);
            this.argCount = Arrays.stream(ruleSets).map(Arrays::stream).mapToInt(s -> s.mapToInt(r -> r[0] * r[1]).sum()).min().orElse(0);
            this.keepStack = false;
            this.command = Type2Command.valueOf(name());
        }

        private Type2Operator(int b, boolean escaped, int argCount) {
            this.byte0 = escaped ? ESCAPE : b;
            this.byte1 = escaped ? b      : 0;
            this.ruleSets = null;
            this.argCount = argCount;
            this.keepStack = true;
            this.command = getEnum(Type2Command.class, name());
            if ( command!=null ) throw new IllegalStateException(name() + " maps to Type2Command " + command);
        }
        
        public int getArgumentCount() {
            return argCount;
        }
        
        public boolean verify(int stackSize) {
            if ( ruleSets==null ) return stackSize>=argCount;
            for ( int[][] ruleSet : ruleSets ) {
                if ( CharStringCommand.verify(ruleSet, 0, stackSize) ) return true;
            }
            return false;
        }
        
        @Override
        public String toString() {
            return super.toString().toLowerCase(Locale.ROOT);
        }
        
        public static Type2Operator get(int b0) {
            return b0<0 || b0>=TYPE2_SINGLE.length ? null : TYPE2_SINGLE[b0];
        }
    
        public static Type2Operator get(int byte0, int byte1) {
            return byte0==ESCAPE
                ? (byte1<0 || byte1>=TYPE2_DOUBLE.length ? null : TYPE2_DOUBLE[byte1])
                : (byte0<0 || byte0>=TYPE2_SINGLE.length ? null : TYPE2_SINGLE[byte0]);
        }

        // Simple operator stack verification system
        
        private final static Type2Operator[] TYPE2_SINGLE, TYPE2_DOUBLE;
        static {
            TYPE2_SINGLE = new Type2Operator[32];
            TYPE2_DOUBLE = new Type2Operator[38];
            for ( Type2Operator w : values() ) {
                if ( w.byte0!=ESCAPE ) TYPE2_SINGLE[w.byte0] = w; else TYPE2_DOUBLE[w.byte1] = w;
            }
        }
    }

    
    /**
     * Full (internal) CFF2 command set.
     * 
     * @see "https://learn.microsoft.com/en-us/typography/opentype/spec/cff2charstr"
     */
    enum CFF2Operator
    {
        // Path construction operators
        RMOVETO        (CharStringCommand.RMOVETO,    false, "2"),
        HMOVETO        (CharStringCommand.HMOVETO,    false, "1"),
        VMOVETO        (CharStringCommand.VMOVETO,    false, "1"),
        RLINETO        (CharStringCommand.RLINETO,    false, "2+"),
        HLINETO        (CharStringCommand.HLINETO,    false, "1  2* | 2+"),
        VLINETO        (CharStringCommand.VLINETO,    false, "1  2* | 2+"),
        RRCURVETO      (CharStringCommand.RRCURVETO,  false, "6+"),
        HHCURVETO      (CharStringCommand.HHCURVETO,  false, "1? 4+"),
        HVCURVETO      (CharStringCommand.HVCURVETO,  false, "4  8* 1? | 8+ 1?"),
        RCURVELINE     (CharStringCommand.RCURVELINE, false, "6+ 2"),
        RLINECURVE     (CharStringCommand.RLINECURVE, false, "2+ 6"),
        VHCURVETO      (CharStringCommand.VHCURVETO,  false, "4  8* 1? | 8+ 1?"),
        VVCURVETO      (CharStringCommand.VVCURVETO,  false, "1? 4+"),
        FLEX           (CharStringCommand.FLEX,       true,  "13"),
        HFLEX          (CharStringCommand.HFLEX,      true,  "7"),
        HFLEX1         (CharStringCommand.HFLEX1,     true,  "9"),
        FLEX1          (CharStringCommand.FLEX1,      true,  "11"),

        // Hint Operators
        HSTEM          (CharStringCommand.HSTEM,      false, "2  2*"),
        VSTEM          (CharStringCommand.VSTEM,      false, "2  2*"),
        HSTEMHM        (CharStringCommand.HSTEMHM,    false, "2  2*"),
        VSTEMHM        (CharStringCommand.VSTEMHM,    false, "2  2*"),
        /** Note: Parser will send VSTEM/VSTEMHM command if omitted in charstring, but given on stack */
        HINTMASK       (CharStringCommand.HINTMASK,   false, "0"),
        /** Note: Parser will send VSTEM/VSTEMHM command if omitted in charstring, but given on stack */
        CNTRMASK       (CharStringCommand.CNTRMASK,   false, "0"),

        // Subroutine Operators (keep stack)
        CALLSUBR       (CharStringCommand.CALLSUBR,   false, 1),
        CALLGSUBR      (CharStringCommand.CALLGSUBR,  false, 1),

        // Blend
        VSINDEX        (CharStringCommand.VSINDEX,    false, "1"),
        BLEND          (CharStringCommand.BLEND,      false, 1);
        
        
        private final int byte0, byte1;
        private final int[][][] ruleSets;
        private final int argCount;
        final boolean keepStack;
        final CFF2Command command;
        
        private CFF2Operator(int b, boolean escaped, String rules) {
            this.byte0 = escaped ? ESCAPE : b;
            this.byte1 = escaped ? b      : 0;
            this.ruleSets = CharStringCommand.parse(rules);
            this.argCount = Arrays.stream(ruleSets).map(Arrays::stream).mapToInt(s -> s.mapToInt(r -> r[0] * r[1]).sum()).min().orElse(0);
            this.keepStack = false;
            this.command = CFF2Command.valueOf(name());
        }

        private CFF2Operator(int b, boolean escaped, int argCount) {
            this.byte0 = escaped ? ESCAPE : b;
            this.byte1 = escaped ? b      : 0;
            this.ruleSets = null;
            this.argCount = argCount;
            this.keepStack = true;
            this.command = getEnum(CFF2Command.class, name());
        }
        
        public int getArgumentCount() {
            return argCount;
        }
        
        public boolean verify(int stackSize) {
            if ( ruleSets==null ) return stackSize>=argCount;
            for ( int[][] ruleSet : ruleSets ) {
                if ( CharStringCommand.verify(ruleSet, 0, stackSize) ) return true;
            }
            return false;
        }
        
        @Override
        public String toString() {
            return super.toString().toLowerCase(Locale.ROOT);
        }
        
        public static CFF2Operator get(int b0) {
            return b0<0 || b0>=CFF2_SINGLE.length ? null : CFF2_SINGLE[b0];
        }
    
        public static CFF2Operator get(int byte0, int byte1) {
            return byte0==ESCAPE
                ? (byte1<0 || byte1>=CFF2_DOUBLE.length ? null : CFF2_DOUBLE[byte1])
                : (byte0<0 || byte0>=CFF2_SINGLE.length ? null : CFF2_SINGLE[byte0]);
        }

        // Simple operator stack verification system
        
        private final static CFF2Operator[] CFF2_SINGLE, CFF2_DOUBLE;
        static {
            CFF2_SINGLE = new CFF2Operator[32];
            CFF2_DOUBLE = new CFF2Operator[38];
            for ( CFF2Operator w : values() ) {
                if ( w.byte0!=ESCAPE ) CFF2_SINGLE[w.byte0] = w; else CFF2_DOUBLE[w.byte1] = w;
            }
        }
    }

    
    /** Verifies the operators on the stack by recursively permutating the rules against them. */ 
    private static boolean verify(int[][] rules, int index, int size)
    {
        final int[] rule = rules[index++];
        final boolean last = index==rules.length;
        final int num = rule[0];
        for ( int count = rule[1], max = rule[2]; count<=max; count++ ) {
            final int ns = size - count * num;
            if ( ns<0 ) break;
            if ( last && ns==0 || !last && ns>=0 && verify(rules, index, ns) ) return true;
        }
        return false;
    }
    
    
    private static int[][][] parse(String ruleSets)
    {
        ruleSets = ruleSets.trim();
        if ( ruleSets.isEmpty() ) return null;
        final Pattern RULE = Pattern.compile("(\\d+)([+*?]?)([, \t\n]|$)");
        return Arrays.stream(ruleSets.split("\\s*|\\s*"))
            .map(RULE::matcher).map(CharStringCommand::results).map(s -> s.map(CharStringCommand::parse)
            .toArray(int[][]::new)).toArray(int[][][]::new);
    }
    
    
    private static int[] parse(MatchResult rule)
    {
        int num = Integer.parseInt(rule.group(1));
        switch (rule.group(2)) {
            case "?": return new int[] { num, 0, 1 };
            case "":  return new int[] { num, 1, 1 };
            case "*": return new int[] { num, 0, Integer.MAX_VALUE };
            case "+": return new int[] { num, 1, Integer.MAX_VALUE };
        }
        throw new IllegalArgumentException("Unknown rule: " + rule.group());            
    }
    
    
    private static Stream<MatchResult> results(Matcher matcher)
    {
        List<MatchResult> results = new ArrayList<>();
        while ( matcher.find() ) results.add(matcher.toMatchResult());
        return results.stream();
    }
    
    
}
