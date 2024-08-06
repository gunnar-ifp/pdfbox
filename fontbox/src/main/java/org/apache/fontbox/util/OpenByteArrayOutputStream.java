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
package org.apache.fontbox.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Extends {@link ByteArrayOutputStream} and enables access to the internal byte array.
 * 
 * @author Gunnar Brand
 * @since 13.02.2023
 */
public class OpenByteArrayOutputStream extends ByteArrayOutputStream
{
    public static OpenByteArrayOutputStream estimate(int size)
    {
        return new OpenByteArrayOutputStream(size < 32 ? 32 : size);
    }
    

    public static OpenByteArrayOutputStream exact(int size)
    {
        return new OpenByteArrayOutputStream(size);
    }
    
    
    public OpenByteArrayOutputStream()
    {
    }

    
    public OpenByteArrayOutputStream(int size)
    {
        super(size <= 0 ? 32 : size);
    }


    public synchronized long readFully(InputStream in) throws IOException
    {
        long total = 0;
        while ( buf.length - count > 0 ) {
            int read = in.read(buf, count, buf.length - count);
            if ( read==-1 ) return total;
            count += read;
            total += read;
        }
        
        // check end of intput stream
        int b = in.read();
        if ( b == -1 ) return total;
        
        // since we can't call ensureCapacity, we fall back to an extra buffer.
        // could probably trick it with transfering a byte... 
        write(b);
        return transferTo(in, this) + 1 + total;
    }
    
    
    /**
     * Set the current size to any size less than or equal the current size.
     * 
     * @throws IllegalArgumentException if the new size is smaller than 0 or larger than the current size.
     */
    public void setSize(int size) throws IllegalArgumentException
    {
        if ( size < 0 ) throw new IllegalArgumentException(size + " < 0");
        if ( size > count ) throw new IllegalArgumentException(size + " > " + count);
        count = size;
    }

    /**
     * Returns the current buffer's length.
     */
    public int capacity()
    {
        return buf.length;
    }
    

    /**
     * Returns the current internal buffer.
     */
    public byte[] array()
    {
        return buf;
    }

    
    /**
     * Can be used at the end of writing to this stream to aquire the contents. Similar to {@link #toByteArray()}
     * but doesn't necessarily clone the contents of the internal buffer.
     * <p>
     * Equivalent to {@code (stream.capacity() == stream.size() ? stream.array() : stream.toByteArray())}
     * 
     * @return the internal array if {@link #capacity()} == {@link #size()}, or a properly sized byte array otherwise.
     */
    public byte[] finished()
    {
        return capacity() == size() ? array() : toByteArray();
    }


    public static long transferTo(InputStream in, OutputStream out) throws IOException
    {
        long transferred = 0;
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer, 0, 8192)) >= 0) {
            out.write(buffer, 0, read);
            transferred += read;
        }
        return transferred;
    }

}
