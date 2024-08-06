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

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.TimeZone;

import org.apache.fontbox.util.Charsets;

/**
 * An interface into a data stream.
 * 
 * @author Ben Litchfield
 */
abstract class TTFDataStream implements Closeable
{
    public final static int[] EMPTY = {};
    
    TTFDataStream()
    {
    }
    
    /**
     * Read a 16.16 fixed value, where the first 16 bits are the decimal and the last 16 bits are the fraction.
     * 
     * @return A 32 bit value.
     * @throws IOException If there is an error reading the data.
     */
    public float read32Fixed() throws IOException
    {
        float retval = readSignedShort();
        retval += (readUnsignedShort() / 65536f);
        return retval;
    }

    /**
     * Read a fixed length ascii string.
     * 
     * @param length The length of the string to read.
     * @return A string of the desired length.
     * @throws IOException If there is an error reading the data.
     */
    public String readString(int length) throws IOException
    {
        return readString(length, Charsets.ISO_8859_1);
    }

    /**
     * Read a fixed length string.
     * 
     * @param length The length of the string to read in bytes.
     * @param charset The expected character set of the string.
     * @return A string of the desired length.
     * @throws IOException If there is an error reading the data.
     */
    public String readString(int length, String charset) throws IOException
    {
        byte[] buffer = read(length);
        return new String(buffer, charset);
    }

    /**
     * Read a fixed length string.
     * 
     * @param length The length of the string to read in bytes.
     * @param charset The expected character set of the string.
     * @return A string of the desired length.
     * @throws IOException If there is an error reading the data.
     */
    public String readString(int length, Charset charset) throws IOException
    {
        byte[] buffer = read(length);
        return new String(buffer, charset);
    }
    
    
    /**
     * Read an unsigned byte.
     * 
     * @return An unsigned byte or -1 if end of input.
     * @throws IOException If there is an error reading the data.
     */
    public abstract int read() throws IOException;

    
    /**
     * Read a signed byte.
     * 
     * @return A signed byte.
     * @throws IOException If there is an error reading the data.
     */
    public final byte readByte() throws IOException, EOFException
    {
        return (byte)readUnsignedByte();
    }

    
    /**
     * @deprecated Use {@link #readByte()} instead.
     */
    @Deprecated
    public final byte readSignedByte() throws IOException, EOFException
    {
        return (byte)readUnsignedByte();
    }

    
    /**
     * Read a unsigned byte. Similar to {@link #read()}, but throws an exception if EOF is unexpectedly reached.
     * 
     * @return A unsigned byte.
     * @throws IOException If there is an error reading the data.
     */
    public final int readUnsignedByte() throws IOException, EOFException
    {
        int b = read();
        if (b == -1) throw new EOFException("premature EOF");
        return b;
    }

    
    /**
     * Read an signed short.
     * 
     * @return An signed short.
     * @throws IOException If there is an error reading the data.
     */
    public short readShort() throws IOException, EOFException
    {
        int r = read() << 8 | read();
        if ( r < 0 ) throw new EOFException();
        return (short)r;
    }

    
    /**
     * @deprecated Use {@link #readShort()} instead.
     */
    @Deprecated
    public final short readSignedShort() throws IOException, EOFException
    {
        return readShort();
    }


    /**
     * Read an unsigned short.
     * 
     * @return An unsigned short.
     * @throws IOException If there is an error reading the data.
     */
    public final int readUnsignedShort() throws IOException, EOFException
    {
        return Short.toUnsignedInt(readShort());
    }


    /**
     * Read a signed integer.
     * 
     * @return A signed integer.
     * @throws IOException If there is a problem reading the file.
     */
    public int readInt() throws IOException, EOFException
    {
        int byte1 = read();
        int byte2 = read();
        int byte3 = read();
        int byte4 = read();
        if (byte4 < 0) throw new EOFException();
        return byte1 << 24 | byte2 << 16 | byte3 << 8 | byte4;
    }
    
    
    /**
     * Read an unsigned integer.
     * 
     * @return An unsigned integer.
     * @throws IOException If there is an error reading the data.
     */
    public final long readUnsignedInt() throws IOException, EOFException
    {
        return Integer.toUnsignedLong(readInt());
    }

    
    /**
     * Reads a long value.
     * 
     * @return A long.
     * @throws IOException If there is an error reading the data.
     */
    public long readLong() throws IOException, EOFException
    {
        return ((long)(readInt()) << 32) | readUnsignedInt();
    }

    
    /**
     * Read an unsigned byte array.
     * 
     * @param length the length of the array to be read
     * @return An unsigned byte array.
     * @throws IOException If there is an error reading the data.
     */
    public int[] readUnsignedByteArray(int length) throws IOException, EOFException
    {
        if ( length==0 ) return EMPTY;
        
        int[] array = new int[length];
        for (int i = 0; i < length; i++)
        {
            array[i] = readUnsignedByte();
        }
        return array;
    }

    /**
     * Read an unsigned short array.
     * 
     * @param length The length of the array to read.
     * @return An unsigned short array.
     * @throws IOException If there is an error reading the data.
     */
    public int[] readUnsignedShortArray(int length) throws IOException, EOFException
    {
        if ( length==0 ) return EMPTY;
        
        int[] array = new int[length];
        for (int i = 0; i < length; i++)
        {
            array[i] = readUnsignedShort();
        }
        return array;
    }

    /**
     * Read an eight byte international date.
     * 
     * @return An signed short.
     * @throws IOException If there is an error reading the data.
     */
    public Calendar readInternationalDate() throws IOException, EOFException
    {
        long secondsSince1904 = readLong();
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(1904, 0, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long millisFor1904 = cal.getTimeInMillis();
        millisFor1904 += (secondsSince1904 * 1000);
        cal.setTimeInMillis(millisFor1904);
        return cal;
    }

    /**
     * Reads a tag, an arrau of four uint8s used to identify a script, language system, feature,
     * or baseline.
     */
    public String readTag() throws IOException, EOFException
    {
        return new String(read(4), Charsets.US_ASCII);
    }

    /**
     * Read a specific number of bytes from the stream.
     * 
     * @param numberOfBytes The number of bytes to read.
     * @return The byte buffer.
     * @throws IOException If there is an error while reading.
     */
    public byte[] read(int numberOfBytes) throws IOException, EOFException
    {
        byte[] data = new byte[numberOfBytes];
        int amountRead = 0;
        int totalAmountRead = 0;
        // read at most numberOfBytes bytes from the stream.
        while (totalAmountRead < numberOfBytes
                && (amountRead = read(data, totalAmountRead, numberOfBytes - totalAmountRead)) != -1)
        {
            totalAmountRead += amountRead;
        }
        if (totalAmountRead != numberOfBytes)
        {
            throw new EOFException("Unexpected end of TTF stream reached");
        }
        return data;
    }

    /**
     * Seek into the datasource.
     * 
     * @param pos The position to seek to.
     * @throws IOException If there is an error seeking to that position.
     */
    public abstract void seek(long pos) throws IOException;

    /**
     * @see java.io.InputStream#read(byte[], int, int )
     * 
     * @param b The buffer to write to.
     * @param off The offset into the buffer.
     * @param len The length into the buffer.
     * 
     * @return The number of bytes read, or -1 at the end of the stream
     * 
     * @throws IOException If there is an error reading from the stream.
     */
    public abstract int read(byte[] b, int off, int len) throws IOException;

    /**
     * Get the current position in the stream.
     * 
     * @return The current position in the stream.
     * @throws IOException If an error occurs while reading the stream.
     */
    public abstract long getCurrentPosition() throws IOException;

    /**
     * This will get the original data file that was used for this stream.
     * 
     * @return The data that was read from.
     * @throws IOException If there is an issue reading the data.
     */
    public abstract InputStream getOriginalData() throws IOException;

    /**
     * This will get the original data size that was used for this stream.
     * 
     * @return The size of the original data.
     */
    public abstract long getOriginalDataSize();
}
