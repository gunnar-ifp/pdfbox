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
package org.apache.pdfbox.pdmodel.graphics.color;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.IOException;

import org.apache.pdfbox.cos.COSName;

/**
 * A color space with black, white, and intermediate shades of gray.
 *
 * @author Ben Litchfield
 * @author John Hewson
 */
public final class PDDeviceGray extends PDDeviceColorSpace
{
    /** The single instance of this class. */
    public static final PDDeviceGray INSTANCE = new PDDeviceGray();
    
    private final PDColor initialColor = new PDColor(new float[] { 0 }, this);

    private PDDeviceGray()
    {
    }

    @Override
    public String getName()
    {
        return COSName.DEVICEGRAY.getName();
    }

    @Override
    public int getNumberOfComponents()
    {
        return 1;
    }

    @Override
    public float[] getDefaultDecode(int bitsPerComponent)
    {
        return new float[] { 0, 1 };
    }

    @Override
    public PDColor getInitialColor()
    {
        return initialColor;
    }

    @Override
    public float[] toRGB(float[] value)
    {
        return new float[] { value[0], value[0], value[0] };
    }

    @Override
    public BufferedImage toRawImage(WritableRaster raster) throws IOException
    {
        // DeviceGray is whatever the output device chooses. We have no Java colorspace
        // for this.
        return null;
    }

    @Override
    public BufferedImage toRGBImage(WritableRaster raster) throws IOException
    {
        // Cast the linear gray value into an sRGB value w/o gamma correction (seems everybody is doing so).

        final int width = raster.getWidth();
        final int height = raster.getHeight();

        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final DataBuffer db = image.getRaster().getDataBuffer();
        final int[] gray = new int[width];
        int offset = 0;
        for (int y = 0; y < height; y++)
        {
            raster.getSamples(0, y, width, 1, 0, gray);
            for ( int g : gray ) db.setElem(0, offset++, g * 0x010101);
        }

        return image;
    }
}
