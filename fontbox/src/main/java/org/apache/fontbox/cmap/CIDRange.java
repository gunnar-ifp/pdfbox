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


/**
 * Range of continuous CIDs between a character code range.
 */
class CIDRange
{
    private final int codeLength;
    private final int codeStart;
    private int codeEnd;
    private final int cidStart;


    /**
     * @param from start character code
     * @param to end character code
     * @param cid start CID
     */
    public CIDRange(int from, int to, int cid, int codeLength)
    {
        this.codeLength = codeLength;
        this.codeStart = from;
        this.codeEnd = to;
        this.cidStart = cid;
    }

    

    public int length()
    {
        return codeLength;
    }
    
    
    /**
     * Check if the given values represent a consecutive range of the given range. If so, extend the given range instead
     * of creating a new one.
     * 
     * @param from start character code of the new range
     * @param to end character code of the new range
     * @param cid start CID value of the range
     * @param length code length
     * @return true if the given range was extended
     */
    public boolean extend(int from, int to, int cid, int length)
    {
        if ( this.codeLength == length && (from == codeEnd + 1) && (cid == cidStart + codeEnd - codeStart + 1) )
        {
            codeEnd = to;
            return true;
        }
        return false;
    }

    /**
     * Maps the given character code to the corresponding CID in this range.
     *
     * @param code character code
     * @return corresponding CID, or {@code -1} if the character code is out of range
     */
    public int map(int code)
    {
        return codeStart <= code && code <= codeEnd ? (cidStart + code - codeStart) : -1;
    }
    

    /**
     * Maps the given CID to the corresponding character code in this range.
     *
     * @param cid CID
     * @return corresponding character code, or {@code -1} if the CID is out of range
     */
    public int unmap(int cid)
    {
        return cidStart <= cid && cid <= (cidStart + codeEnd - codeStart) ? (codeStart + cid - cidStart) : -1;
    }

}
