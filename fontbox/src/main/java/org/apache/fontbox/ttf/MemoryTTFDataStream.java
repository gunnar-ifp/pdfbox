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
package org.apache.fontbox.ttf;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.fontbox.util.OpenByteArrayOutputStream;

/**
 * An interface into a data stream.
 * 
 * @author Ben Litchfield
 * 
 */
class MemoryTTFDataStream extends TTFDataStream 
{
    private final byte[] data;
    private int currentPosition = 0;
    
    /**
     * Constructor from a byte array stream. 
     * @throws IOException If an error occurs while reading from the stream.
     */
    MemoryTTFDataStream(byte[] data) throws IOException
    {
        this.data = data;
    }
    
    /**
     * Constructor from a stream. 
     * @param is The stream to read from. It will be closed by this method.
     * @throws IOException If an error occurs while reading from the stream.
     */
    MemoryTTFDataStream( InputStream is ) throws IOException
    {
        try (InputStream in = is; OpenByteArrayOutputStream output = OpenByteArrayOutputStream.estimate(in.available())) {
            output.readFully(in);
            this.data = output.finished();
        }
    }
    
    /**
     * Doesn't do anything. The in memory buffer is retained forever due to
     * later access when drawing glyphs.
     */
    @Override
    public void close()
    {
    }
    
    /**
     * Read an unsigned byte.
     * @return An unsigned byte or {@code -1} on end of stream.
     * @throws IOException If there is an error reading the data.
     */
    @Override
    public int read() throws IOException
    {
        return currentPosition >= data.length ? -1 : data[currentPosition++] & 0xff;
    }
    
    /**
     * Seek into the datasource.
     *
     * @param pos The position to seek to.
     * @throws IOException If the seek position is negative or larger than MAXINT.
     */
    @Override
    public void seek(long pos) throws IOException
    {
        if (pos < 0 || pos > Integer.MAX_VALUE)
        {
            throw new IOException("Illegal seek position: " + pos);
        }
        currentPosition = (int) pos;
    }
    
    /**
     * @see java.io.InputStream#read( byte[], int, int )
     * 
     * @param b The buffer to write to.
     * @param off The offset into the buffer.
     * @param len The length into the buffer.
     * 
     * @return The number of bytes read, or -1 at the end of the stream
     * 
     * @throws IOException If there is an error reading from the stream.
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        if (currentPosition >= data.length) return -1;
        
        int r = Math.min(len, data.length - currentPosition);
        System.arraycopy(data, currentPosition, b, off, r);
        currentPosition += r;
        return r;
    }
    
    @Override
    public byte[] read(int numberOfBytes) throws IOException, EOFException
    {
        if ( currentPosition + numberOfBytes > data.length )
        {
            throw new EOFException("Unexpected end of TTF stream reached");
        }
        return Arrays.copyOfRange(data, currentPosition, currentPosition += numberOfBytes);
    }
    
    @Override
    public ByteBuffer readBuffer(int numberOfBytes) throws IOException, EOFException
    {
        if ( currentPosition + numberOfBytes > data.length )
        {
            throw new EOFException("Unexpected end of TTF stream reached");
        }
        int pos = currentPosition;
        currentPosition += numberOfBytes;
        return ByteBuffer.wrap(data, pos, numberOfBytes).asReadOnlyBuffer().slice();
    }
    
    /**
     * Get the current position in the stream.
     * @return The current position in the stream.
     * @throws IOException If an error occurs while reading the stream.
     */
    @Override
    public long getCurrentPosition() throws IOException
    {
        return currentPosition;
    }
    
    @Override
    public InputStream getOriginalData() throws IOException
    {
        return new ByteArrayInputStream( data );
    }

    @Override
    public long getOriginalDataSize()
    {
        return data.length;
    }
}