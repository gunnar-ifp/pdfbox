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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.fontbox.util.Bytes;

/**
 * Parses a CMap stream.
 *
 * @author Ben Litchfield
 */
public class CMapParser
{
    private static final String MARK_END_OF_DICTIONARY = ">>";
    private static final String MARK_END_OF_ARRAY = "]";

    private final byte[] tokenParserByteBuffer = new byte[512];

    /**
     * Creates a new instance of CMapParser.
     */
    public CMapParser()
    {
    }

    /**
     * Creates a new instance of CMapParser.
     * 
     * @param strictMode activates the strict mode used for inline CMaps (unused)
     * @deprecated
     */
    @Deprecated
    public CMapParser(boolean strictMode)
    {
    }

    /**
     * Parse a CMAP file on the file system.
     * 
     * @param file The file to parse.
     * @return A parsed CMAP file.
     * @throws IOException If there is an issue while parsing the CMAP.
     */
    public CMap parse(File file) throws IOException
    {
        FileInputStream input = null;
        try
        {
            input = new FileInputStream(file);
            return parse(input);
        }
        finally
        {
            if (input != null)
            {
                input.close();
            }
        }
    }

    /**
     * Parses a predefined CMap.
     *
     * @param name CMap name.
     * @return The parsed predefined CMap as a java object, never null.
     * @throws IOException If the CMap could not be parsed.
     */
    public CMap parsePredefined(String name) throws IOException
    {
        InputStream input = null;
        try
        {
            input = getExternalCMap(name);
            // deactivate strict mode
            return parse(input);
        }
        finally
        {
            if (input != null)
            {
                input.close();
            }
        }
    }

    /**
     * This will parse the stream and create a cmap object.
     *
     * @param input The CMAP stream to parse.
     * @return The parsed stream as a java object, never null.
     * @throws IOException If there is an error parsing the stream.
     */
    public CMap parse(InputStream input) throws IOException
    {
        PushbackInputStream cmapStream = new PushbackInputStream(input);
        CMap result = new CMap();
        Object previousToken = null;
        Object token;
        while ((token = parseNextToken(cmapStream)) != null)
        {
            if (token instanceof Operator)
            {
                Operator op = (Operator) token;
                if (op.op.equals("endcmap"))
                {
                    // end of CMap reached, stop reading as there isn't any interesting info anymore
                    break;
                }

                if (previousToken != null)
                {
                    if (op.op.equals("usecmap") && previousToken instanceof LiteralName)
                    {
                        parseUsecmap((LiteralName) previousToken, result);
                    }
                    else if (previousToken instanceof Integer)
                    {
                        int count = ((Number)previousToken).intValue();
                        if (op.op.equals("begincodespacerange"))
                        {
                            parseBegincodespacerange(count, cmapStream, result);
                        }
                        else if (op.op.equals("beginbfchar"))
                        {
                            parseBeginbfchar(count, cmapStream, result);
                        }
                        else if (op.op.equals("beginbfrange"))
                        {
                            parseBeginbfrange(count, cmapStream, result);
                        }
                        else if (op.op.equals("begincidchar"))
                        {
                            parseBegincidchar(count, cmapStream, result);
                        }
                        else if (op.op.equals("begincidrange"))
                        {
                            parseBegincidrange(count, cmapStream, result);
                        }
                    }
                }
            }
            else if (token instanceof LiteralName)
            {
                parseLiteralName((LiteralName) token, cmapStream, result);
            }
            previousToken = token;
        }
        return result;
    }

    private void parseUsecmap(LiteralName useCmapName, CMap result) throws IOException
    {
        InputStream useStream = getExternalCMap(useCmapName.name);
        CMap useCMap = parse(useStream);
        result.useCmap(useCMap);
    }

    private void parseLiteralName(LiteralName literal, PushbackInputStream cmapStream, CMap result) throws IOException
    {
        if ("WMode".equals(literal.name))
        {
            Object next = parseNextToken(cmapStream);
            if (next instanceof Integer)
            {
                result.setWMode((Integer) next);
            }
        }
        else if ("CMapName".equals(literal.name))
        {
            Object next = parseNextToken(cmapStream);
            if (next instanceof LiteralName)
            {
                result.setName(((LiteralName) next).name);
            }
        }
        else if ("CMapVersion".equals(literal.name))
        {
            Object next = parseNextToken(cmapStream);
            if (next instanceof Number)
            {
                result.setVersion(next.toString());
            }
            else if (next instanceof String)
            {
                result.setVersion((String) next);
            }
        }
        else if ("CMapType".equals(literal.name))
        {
            Object next = parseNextToken(cmapStream);
            if (next instanceof Integer)
            {
                result.setType((Integer) next);
            }
        }
        else if ("Registry".equals(literal.name))
        {
            Object next = parseNextToken(cmapStream);
            if (next instanceof String)
            {
                result.setRegistry((String) next);
            }
        }
        else if ("Ordering".equals(literal.name))
        {
            Object next = parseNextToken(cmapStream);
            if (next instanceof String)
            {
                result.setOrdering((String) next);
            }
        }
        else if ("Supplement".equals(literal.name))
        {
            Object next = parseNextToken(cmapStream);
            if (next instanceof Integer)
            {
                result.setSupplement((Integer) next);
            }
        }
    }

    /**
     * Throws an IOException if expectedOperatorName not equals operator.op
     *
     * @param operator Instance of operator
     * @param expectedOperatorName Expected name of operator
     * @param rangeName The name of the range in which the operator is expected (without a tilde
     * character), to be used in the exception message.
     * 
     * @throws IOException if expectedOperatorName not equals operator.op
     */
    private void checkExpectedOperator(Operator operator, String expectedOperatorName, String rangeName) throws IOException
    {
        if (!operator.op.equals(expectedOperatorName))
        {
            throw new IOException("Error : ~" + rangeName + " contains an unexpected operator : "
                    + operator.op);
        }
    }

    private void parseBegincodespacerange(int cosCount, PushbackInputStream cmapStream, CMap result) throws IOException
    {
        for (int j = 0; j < cosCount; j++)
        {
            Object nextToken = parseNextToken(cmapStream);
            if (nextToken instanceof Operator)
            {
                checkExpectedOperator((Operator) nextToken, "endcodespacerange", "codespacerange");
                break;
            }
            if (!(nextToken instanceof byte[]))
            {
                throw new IOException("start range missing");
            }
            
            // no check since different array lengths are supported
            final byte[] startRange = (byte[]) nextToken;
            final byte[] endRange = (byte[]) parseNextToken(cmapStream);
            try
            {
                result.addCodespaceRange(new CodespaceRange(startRange, endRange));
            }
            catch (IllegalArgumentException ex)
            {
                throw new IOException(ex);
            }
        }
    }

    private void parseBegincidchar(int cosCount, PushbackInputStream cmapStream, CMap result) throws IOException
    {
        for (int j = 0; j < cosCount; j++)
        {
            Object nextToken = parseNextToken(cmapStream);
            if (nextToken instanceof Operator)
            {
                checkExpectedOperator((Operator) nextToken, "endcidchar", "cidchar");
                break;
            }
            if (!(nextToken instanceof byte[]))
            {
                throw new IOException("input code missing");
            }
            
            final byte[] code = (byte[]) nextToken;
            final int cid  = (Integer) parseNextToken(cmapStream);
            result.addCIDMapping(code, cid);
        }
    }


    private void parseBeginbfchar(int cosCount, PushbackInputStream cmapStream, CMap result) throws IOException
    {
        for (int j = 0; j < cosCount; j++)
        {
            Object nextToken = parseNextToken(cmapStream);
            if (nextToken instanceof Operator)
            {
                checkExpectedOperator((Operator) nextToken, "endbfchar", "bfchar");
                break;
            }
            if (!(nextToken instanceof byte[]))
            {
                throw new IOException("input code missing");
            }
            final byte[] code = (byte[]) nextToken;
            nextToken = parseNextToken(cmapStream);
            if (nextToken instanceof byte[])
            {
                result.addBasefontMapping(code, Bytes.toString((byte[]) nextToken));
            }
            else if (nextToken instanceof LiteralName)
            {
                result.addBasefontMapping(code, ((LiteralName) nextToken).name);
            }
            else
            {
                throw new IOException("Error parsing CMap beginbfchar, expected{COSString "
                        + "or COSName} and not " + nextToken);
            }
        }
    }

    
    private void parseBegincidrange(int numberOfLines, PushbackInputStream cmapStream, CMap result) throws IOException
    {
        for (int n = 0; n < numberOfLines; n++)
        {
            Object nextToken = parseNextToken(cmapStream);
            if (nextToken instanceof Operator)
            {
                checkExpectedOperator((Operator) nextToken, "endcidrange", "cidrange");
                break;
            }

            if (!(nextToken instanceof byte[]))
            {
                throw new IOException("cidrange start code missing");
            }
            final byte[] startCode = (byte[]) nextToken;
            final byte[] endCode = (byte[]) parseNextToken(cmapStream);
            final int start = Bytes.getUnsigned(startCode);
            final int end = Bytes.getUnsigned(endCode);

            if ( startCode.length > 4 ) {
                throw new IOException("cidrange start code length too big");
            }
            if ( startCode.length != endCode.length ) {
                throw new IOException("cidrange start / end code length mismatch");
            }
            if ( start > end ) {
                // TODO: why no exception
                break; // PDFBOX-4550: likely corrupt stream
            }

            final int cid = (Integer) parseNextToken(cmapStream);
            
            // some CMaps are using CID ranges to map single values
            if ( start == end )
            {
                result.addCIDMapping(startCode, cid);
            }
            else
            {
                result.addCIDRange(startCode, endCode, cid);
            }
        }
    }


    private void parseBeginbfrange(int cosCount, PushbackInputStream cmapStream, CMap result) throws IOException
    {
        for (int j = 0; j < cosCount; j++)
        {
            Object nextToken = parseNextToken(cmapStream);
            if (nextToken instanceof Operator)
            {
                checkExpectedOperator((Operator) nextToken, "endbfrange", "bfrange");
                break;
            }
            
            if (!(nextToken instanceof byte[]))
            {
                throw new IOException("fbrange start code missing");
            }
            final byte[] startCode = (byte[]) nextToken;
            final byte[] endCode = (byte[]) parseNextToken(cmapStream);
            final int start = Bytes.getUnsigned(startCode);
            final int length = Bytes.getUnsigned(endCode) - start + 1;
            
            if ( startCode.length > 2 ) {
                throw new IOException("fbrange start code length too big");
            }
            if ( startCode.length != endCode.length ) {
                throw new IOException("cidrange start / end code length mismatch");
            }
            if ( length <= 0 ) {
                // TODO: why no exception
                break; // PDFBOX-4550: likely corrupt stream
            }
            
            nextToken = parseNextToken(cmapStream);
            if (nextToken instanceof List<?>)
            {
                @SuppressWarnings("unchecked")
                List<byte[]> array = (List<byte[]>) nextToken;
                if ( array.size() < length ) continue;
                
                for ( int i = 0; i < length; i++ )
                {
                    if ( i > 0 ) Bytes.increment(startCode);
                    
                    byte[] utf16 = array.get(i);
                    if ( utf16.length > 0 ) {
                        result.addBasefontMapping(Bytes.copy(startCode), Bytes.toString(utf16));
                    }
                }
            }
            else if (nextToken instanceof byte[])
            {
                byte[] utf16 = (byte[]) nextToken;
                // PDFBOX-3450: ignore <>
                if ( utf16.length == 0 ) continue;
                
                // PDFBOX-4720:
                // some pdfs use the malformed bfrange <0000> <FFFF> <0000>. Add support by adding a identity
                // mapping for the whole range instead of cutting it after 255 entries
                if ( length == 0x10000 && utf16.length == 2 && Bytes.getChar(utf16) == 0 )
                {
                    for ( int i = 0; i < length; i += 256 )
                    {
                        startCode[0] = utf16[0] = utf16[1] = (byte) (i >> 8);
                        startCode[1] = utf16[1] = (byte) 0x00;
                        endCode  [1] = (byte)0xff;
                        result.addBasefontRange(startCode, endCode, utf16);
                    }
                }
                else
                {
                    result.addBasefontRange(startCode, endCode, utf16);
                }
            }
            else
            {
                // PDFBOX-3807: ignore null
            }
        }
    }

    /**
     * Returns an input stream containing the given "use" CMap.
     *
     * @param name Name of the given "use" CMap resource.
     * @throws IOException if the CMap resource doesn't exist or if there is an error opening its
     * stream.
     */
    protected static InputStream getExternalCMap(String name) throws IOException
    {
        InputStream resourceAsStream = CMapParser.class.getResourceAsStream(name);
        if (resourceAsStream == null)
        {
            throw new IOException("Error: Could not find referenced cmap stream " + name);
        }
        return new BufferedInputStream(resourceAsStream);
    }

    private Object parseNextToken(PushbackInputStream is) throws IOException
    {
        Object retval = null;
        int nextByte = is.read();
        // skip whitespace
        while (nextByte == 0x09 || nextByte == 0x20 || nextByte == 0x0D || nextByte == 0x0A)
        {
            nextByte = is.read();
        }
        switch (nextByte)
        {
        case '%':
        {
            // header operations, for now return the entire line
            // may need to smarter in the future
            StringBuilder buffer = new StringBuilder();
            buffer.append((char) nextByte);
            readUntilEndOfLine(is, buffer);
            retval = buffer.toString();
            break;
        }
        case '(':
        {
            StringBuilder buffer = new StringBuilder();
            int stringByte = is.read();

            while (stringByte != -1 && stringByte != ')')
            {
                buffer.append((char) stringByte);
                stringByte = is.read();
            }
            retval = buffer.toString();
            break;
        }
        case '>':
        {
            int secondCloseBrace = is.read();
            if (secondCloseBrace == '>')
            {
                retval = MARK_END_OF_DICTIONARY;
            }
            else
            {
                throw new IOException("Error: expected the end of a dictionary.");
            }
            break;
        }
        case ']':
        {
            retval = MARK_END_OF_ARRAY;
            break;
        }
        case '[':
        {
            List<Object> list = new ArrayList<Object>();

            Object nextToken = parseNextToken(is);
            while (nextToken != null && !MARK_END_OF_ARRAY.equals(nextToken))
            {
                list.add(nextToken);
                nextToken = parseNextToken(is);
            }
            retval = list;
            break;
        }
        case '<':
        {
            int theNextByte = is.read();
            if (theNextByte == '<')
            {
                Map<String, Object> result = new HashMap<String, Object>();
                // we are reading a dictionary
                Object key = parseNextToken(is);
                while (key instanceof LiteralName && !MARK_END_OF_DICTIONARY.equals(key))
                {
                    Object value = parseNextToken(is);
                    result.put(((LiteralName) key).name, value);
                    key = parseNextToken(is);
                }
                retval = result;
            }
            else
            {
                // won't read more than 512 bytes

                int multiplyer = 16;
                int bufferIndex = -1;
                while (theNextByte != -1 && theNextByte != '>')
                {
                    int intValue = 0;
                    if (theNextByte >= '0' && theNextByte <= '9')
                    {
                        intValue = theNextByte - '0';
                    }
                    else if (theNextByte >= 'A' && theNextByte <= 'F')
                    {
                        intValue = 10 + theNextByte - 'A';
                    }
                    else if (theNextByte >= 'a' && theNextByte <= 'f')
                    {
                        intValue = 10 + theNextByte - 'a';
                    }
                    // all kind of whitespaces may occur in malformed CMap files
                    // see PDFBOX-2035
                    else if (isWhitespaceOrEOF(theNextByte))
                    {
                        // skipping whitespaces
                        theNextByte = is.read();
                        continue;
                    }
                    else
                    {
                        throw new IOException("Error: expected hex character and not " + (char) theNextByte + ":"
                                + theNextByte);
                    }
                    intValue *= multiplyer;
                    if (multiplyer == 16)
                    {
                        bufferIndex++;
                        if (bufferIndex >= tokenParserByteBuffer.length)
                        {
                            throw new IOException("cmap token ist larger than buffer size " +
                                    tokenParserByteBuffer.length);
                        }
                        tokenParserByteBuffer[bufferIndex] = 0;
                        multiplyer = 1;
                    }
                    else
                    {
                        multiplyer = 16;
                    }
                    tokenParserByteBuffer[bufferIndex] += intValue;
                    theNextByte = is.read();
                }
                retval = Arrays.copyOf(tokenParserByteBuffer, bufferIndex + 1);
            }
            break;
        }
        case '/':
        {
            StringBuilder buffer = new StringBuilder();
            int stringByte = is.read();

            while (!isWhitespaceOrEOF(stringByte) && !isDelimiter(stringByte))
            {
                buffer.append((char) stringByte);
                stringByte = is.read();
            }
            if (isDelimiter( stringByte)) 
            {
                is.unread(stringByte);
            }
            retval = new LiteralName(buffer.toString());
            break;
        }
        case -1:
        {
            // EOF returning null
            break;
        }
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
        {
            StringBuilder buffer = new StringBuilder();
            buffer.append((char) nextByte);
            nextByte = is.read();

            while (!isWhitespaceOrEOF(nextByte) && (Character.isDigit((char) nextByte) || nextByte == '.'))
            {
                buffer.append((char) nextByte);
                nextByte = is.read();
            }
            is.unread(nextByte);
            String value = buffer.toString();
            try
            {
                if (value.indexOf('.') >= 0)
                {
                    retval = Double.valueOf(value);
                }
                else
                {
                    retval = Integer.valueOf(value);
                }
            }
            catch (NumberFormatException ex)
            {
                throw new IOException("Invalid number '" + value + "'", ex);
            }
            break;
        }
        default:
        {
            StringBuilder buffer = new StringBuilder();
            buffer.append((char) nextByte);
            nextByte = is.read();

            // newline separator may be missing in malformed CMap files
            // see PDFBOX-2035
            while (!isWhitespaceOrEOF(nextByte) && !isDelimiter(nextByte) && !Character.isDigit(nextByte))
            {
                buffer.append((char) nextByte);
                nextByte = is.read();
            }
            if (isDelimiter(nextByte) || Character.isDigit(nextByte))
            {
                is.unread(nextByte);
            }
            retval = new Operator(buffer.toString());

            break;
        }
        }
        return retval;
    }

    private static void readUntilEndOfLine(InputStream is, StringBuilder buf) throws IOException
    {
        int nextByte = is.read();
        while (nextByte != -1 && nextByte != 0x0D && nextByte != 0x0A)
        {
            buf.append((char) nextByte);
            nextByte = is.read();
        }
    }

    private static boolean isWhitespaceOrEOF(int aByte)
    {
        return aByte == -1 || aByte == 0x20 || aByte == 0x0D || aByte == 0x0A;
    }

    /** Is this a standard PDF delimiter character? */
    private static boolean isDelimiter(int aByte) 
    {
        switch (aByte) 
        {
            case '(':
            case ')':
            case '<':
            case '>':
            case '[':
            case ']':
            case '{':
            case '}':
            case '/':
            case '%':
                return true;
            default:
                return false;
        }
    }

    /**
     * Internal class.
     */
    private static final class LiteralName
    {
        private String name;

        private LiteralName(String theName)
        {
            name = theName;
        }
    }

    /**
     * Internal class.
     */
    private static final class Operator
    {
        private String op;

        private Operator(String theOp)
        {
            op = theOp;
        }
    }
}
