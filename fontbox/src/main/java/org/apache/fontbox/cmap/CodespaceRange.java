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
package org.apache.fontbox.cmap;

import java.util.Arrays;

/**
 * This represents a single entry in the codespace range.
 *
 * @author Ben Litchfield
 */
class CodespaceRange implements Comparable<CodespaceRange>
{
    public final static int LIMIT = 4;
    
    private final int[] start;
    private final int[] end;
    private final int length;
    
    /**
     * Creates a new instance of CodespaceRange. The length of both arrays has to be the same.<br>
     * For one byte ranges startBytes and endBytes define a linear range of values. Double byte values define a
     * rectangular range not a linear range. Examples: <br>
     * &lt;00&gt; &lt;20&gt; defines a linear range from 0x00 up to 0x20.<br>
     * &lt;8140&gt; to &lt;9FFC&gt; defines a rectangular range. The high byte has to be within 0x81 and 0x9F and the
     * low byte has to be within 0x40 and 0xFC
     * <p>
     * Up to 4 dimensions are supported.
     */
    public CodespaceRange(byte[] startBytes, byte[] endBytes) throws IllegalArgumentException
    {
        final int len = endBytes.length, sl = startBytes.length;
        // start and end lengths must match except if start is <00> and end is like <ffff>
        if ( len == 0 || len > 4 || sl != len && (sl != 1 || startBytes[0] != 0) ) {
            throw new IllegalArgumentException(String.format("start (%d) and end (%d) value length invalid", sl, len));
        }
        
        this.length = len;
        this.start  = new int[len];
        this.end    = new int[len];
        for ( int i = 0; i < len; i++) {
            start[i] = i<sl ? startBytes[i] & 0xFF : 0;
            end[i]   = endBytes[i] & 0xFF;
        }
    }

    
    /**
     * Returns the length of the codes of the codespace.
     */
    public int length()
    {
        return length;
    }
    
    
    /**
     * Returns the length of the codes of the codespace.
     */
    public int getCodeLength()
    {
        return length;
    }


    /**
     * Returns true if the given code bytes match this codespace range.
     */
    public boolean matches(byte[] code)
    {
        return matches(code, 0, code.length);
    }
    

    /**
     * Returns true if the given code bytes match this codespace range.
     */
    public boolean matches(byte[] code, int offset, int len)
    {
        if (length != len) return false;
        for (int i = 0; i < length; i++) {
            int c = code[offset + i] & 0xFF;
            if ( c < start[i] || c > end[i] ) return false;
        }
        return true;
    }
    
    
    public boolean matches(int b0)
    {
        return length == 1
            && b0 >= start[0] && b0 <= end[0];
    }
    

    public boolean matches(int b0, int b1)
    {
        return length == 2
            && b0 >= start[0] && b0 <= end[0]
            && b1 >= start[1] && b0 <= end[1];
    }

    
    public boolean matches(int b0, int b1, int b2)
    {
        return length == 3
            && b0 >= start[0] && b0 <= end[0]
            && b1 >= start[1] && b0 <= end[1]
            && b2 >= start[2] && b2 <= end[2];
    }

    
    public boolean matches(int b0, int b1, int b2, int b3)
    {
        return length == 4
            && b0 >= start[0] && b0 <= end[0]
            && b1 >= start[1] && b0 <= end[1]
            && b2 >= start[2] && b2 <= end[2]
            && b3 >= start[3] && b3 <= end[3];
    }

    
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + length;
        result = prime * result + Arrays.hashCode(start);
        result = prime * result + Arrays.hashCode(end);
        return result;
    }

    
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof CodespaceRange)) return false;
        CodespaceRange other = (CodespaceRange)obj;
        if (length != other.length) return false;
        if (!Arrays.equals(start, other.start)) return false;
        if (!Arrays.equals(end, other.end)) return false;
        return true;
    }

    
    @Override
    public int compareTo(CodespaceRange o)
    {
        int c = Integer.compare(length, o.length);
        // compare the same as matches, so that its return value can be used for short circuiting (planned feature)
        for ( int i = 0; c == 0 && i < length; i++ ) {
            c = Integer.compare(start[i], o.start[i]);
            if ( c == 0 ) c = Integer.compare(end[i], o.end[i]);
        }
        return c;
    }

    
    @Override
    public String toString()
    {
        return "CodespaceRange [length=" + length + ", start=" + Arrays.toString(start) + ", end=" + Arrays.toString(end) + "]";
    }
    
}
