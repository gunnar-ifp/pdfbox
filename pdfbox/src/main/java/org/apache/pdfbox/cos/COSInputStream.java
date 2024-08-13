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

package org.apache.pdfbox.cos;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.filter.DecodeOptions;
import org.apache.pdfbox.filter.DecodeResult;
import org.apache.pdfbox.filter.Filter;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccess;
import org.apache.pdfbox.io.RandomAccessInputStream;
import org.apache.pdfbox.io.RandomAccessOutputStream;
import org.apache.pdfbox.io.ScratchFile;

/**
 * An InputStream which reads from an encoded COS stream.
 *
 * @author John Hewson
 */
public final class COSInputStream extends FilterInputStream
{
    private static final Log LOG = LogFactory.getLog(COSInputStream.class);
    
    /**
     * Creates a new COSInputStream from an encoded input stream.
     *
     * @param filters Filters to be applied.
     * @param parameters Filter parameters.
     * @param in Encoded input stream.
     * @param scratchFile Scratch file to use, or null.
     * @return Decoded stream.
     * @throws IOException If the stream could not be read.
     */
    static COSInputStream create(List<Filter> filters, COSDictionary parameters, InputStream in,
                                 ScratchFile scratchFile) throws IOException
    {
        return create(filters, parameters, in, scratchFile, DecodeOptions.DEFAULT);
    }

    static COSInputStream create(List<Filter> filters, COSDictionary parameters, InputStream in,
                                 ScratchFile scratchFile, DecodeOptions options) throws IOException
    {
        if (filters.isEmpty())
        {
            return new COSInputStream(in, Collections.<DecodeResult>emptyList());
        }

        List<DecodeResult> results = new ArrayList<DecodeResult>(filters.size());
        if (filters.size() > 1)
        {
            Set<Filter> filterSet = new HashSet<Filter>(filters);
            if (filterSet.size() != filters.size())
            {
                List<Filter> reducedFilterList = new ArrayList<Filter>();
                for (Filter filter : filters)
                {
                    if (!reducedFilterList.contains(filter))
                    {
                        reducedFilterList.add(filter);
                    }
                }
                // replace origin list with the reduced one
                filters = reducedFilterList;
                LOG.warn("Removed duplicated filter entries");
            }
        }
        // apply filters
        InputStream input = in;
        if (scratchFile != null)
        {
            for (int i = 0; i < filters.size(); i++)
            {
                // initial input stream must only be closed by us if we have a replacement.
                // all other ones must  closed by us in case of errors.
                InputStream previous = i == 0 ? null : input;
                try {
                    // scratch file
                    RandomAccess buffer = scratchFile.createBuffer();
                    try {
                        results.add(filters.get(i).decode(input, new RandomAccessOutputStream(buffer), parameters, i, options));
                        input = new RandomAccessInputStream(buffer, true);
                        buffer = null;
                    }
                    finally {
                        IOUtils.closeQuietly(buffer);
                    }
                }
                finally {
                    IOUtils.closeQuietly(previous);
                }
            }
        }
        else
        {
            // in-memory
            for (int i = 0; i < filters.size(); i++)
            {
                OpenByteArrayOutputStream output = OpenByteArrayOutputStream.estimate(in.available());
                results.add(filters.get(i).decode(input, output, parameters, i, options));
                input = new ByteArrayInputStream(output.array(), 0, output.size());
            }
        }
        
        // Note: the initial input stream has a no-op close() method (see COSStream),
        // but for the sake of clean code we assume the caller it in case of exceptions happening in this method.
        // That means we need to close input in case of successfully decoding it.
        IOUtils.closeQuietly(in);
        
        return new COSInputStream(input, results);
    }

    private final List<DecodeResult> decodeResults;

    /**
     * Constructor.
     * 
     * @param input decoded stream
     * @param decodeResults results of decoding
     */
    private COSInputStream(InputStream input, List<DecodeResult> decodeResults)
    {
        super(input);
        this.decodeResults = decodeResults;
    }
    
    /**
     * Returns the result of the last filter, for use by repair mechanisms.
     * 
     * @return the result of the decoding.
     */
    public DecodeResult getDecodeResult()
    {
        if (decodeResults.isEmpty())
        {
            return DecodeResult.DEFAULT;
        }
        else
        {
            return decodeResults.get(decodeResults.size() - 1);
        }
    }
}
