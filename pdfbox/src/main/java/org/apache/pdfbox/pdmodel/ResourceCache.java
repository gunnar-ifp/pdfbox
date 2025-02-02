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

package org.apache.pdfbox.pdmodel;

import java.io.IOException;

import org.apache.fontbox.FontBoxFont;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.pattern.PDAbstractPattern;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShading;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

/**
 * A document-wide cache for page resources.
 *
 * @author John Hewson
 */
public interface ResourceCache
{
    default void clear()
    {
    }
    
    
    default void startPage(Object pageId)
    {
    }
    
    default void endPage()
    {
    }
    
    
    /**
     * Returns the font resource for the given indirect object, if it is in the cache.
     * 
     * @param indirect the indirect object
     * 
     * @return the font resource of the given indirect object.
     * @throws IOException if something went wrong.
     */
    PDFont getFont(COSObject indirect) throws IOException;

    default FontBoxFont getBaseFont(PDStream stream) throws IOException
    {
        return null;
    }

    /**
     * Returns the color space resource for the given indirect object, if it is in the cache.
     * 
     * @param indirect the indirect object
     * 
     * @return the color space resource of the given indirect object.
     * @throws IOException if something went wrong.
     */
    PDColorSpace getColorSpace(COSObject indirect) throws IOException;

    /**
     * Returns the extended graphics state resource for the given indirect object, if it is in the cache.
     * 
     * @param indirect the indirect object
     * 
     * @return the extended graphics resource of the given indirect object.
     */
    PDExtendedGraphicsState getExtGState(COSObject indirect);
        
    /**
     * Returns the shading resource for the given indirect object, if it is in the cache.
     * 
     * @param indirect the indirect object
     * 
     * @return the shading resource of the given indirect object.
     * @throws IOException if something went wrong.
     */
    PDShading getShading(COSObject indirect) throws IOException;
    
    /**
     * Returns the pattern resource for the given indirect object, if it is in the cache.
     *
     * @param indirect the indirect object
     * 
     * @return the pattern resource of the given indirect object.
     * @throws IOException if something went wrong.
     */
    PDAbstractPattern getPattern(COSObject indirect) throws IOException;
        
    /**
     * Returns the property list resource for the given indirect object, if it is in the cache.
     * 
     * @param indirect the indirect object
     * 
     * @return the property list resource of the given indirect object.
     */
    PDPropertyList getProperties(COSObject indirect);
        
    /**
     * Returns the XObject resource for the given indirect object, if it is in the cache.
     * 
     * @param indirect the indirect object
     * 
     * @return the XObject resource of the given indirect object.
     * @throws IOException if something went wrong.
     */
    PDXObject getXObject(COSObject indirect) throws IOException;

    /**
     * Puts the given indirect font resource in the cache.
     * 
     * @param indirect the indirect object of the resource.
     * @param font the font resource.
     * 
     * @throws IOException if something went wrong.
     */
    void put(COSObject indirect, PDFont font) throws IOException;

    default void put(PDStream stream, FontBoxFont basefont) throws IOException
    {
    }
    
    /**
     * Puts the given indirect color space resource in the cache.
     * 
     * @param indirect the indirect object of the resource.
     * @param colorSpace the color space resource.
     * 
     * @throws IOException if something went wrong.
     */
    void put(COSObject indirect, PDColorSpace colorSpace) throws IOException;

    /**
     * Puts the given indirect extended graphics state resource in the cache.
     * 
     * @param indirect the indirect object of the resource.
     * @param extGState the extended graphics state resource.
     */
    void put(COSObject indirect, PDExtendedGraphicsState extGState);

    /**
     * Puts the given indirect shading resource in the cache.
     * 
     * @param indirect the indirect object of the resource.
     * @param shading the shading resource.
     * 
     * @throws IOException if something went wrong.
     */
    void put(COSObject indirect, PDShading shading) throws IOException;

    /**
     * Puts the given indirect pattern resource in the cache.
     * 
     * @param indirect the indirect object of the resource.
     * @param pattern the pattern resource.
     * 
     * @throws IOException if something went wrong.
     */
    void put(COSObject indirect, PDAbstractPattern pattern) throws IOException;

    /**
     * Puts the given indirect property list resource in the cache.
     * 
     * @param indirect the indirect object of the resource.
     * @param propertyList the property list resource.
     */
    void put(COSObject indirect, PDPropertyList propertyList);
    
    /**
     * Puts the given indirect XObject resource in the cache.
     * 
     * @param indirect the indirect object of the resource.
     * @param xobject the XObject resource.
     * 
     * @throws IOException if something went wrong.
     */
    void put(COSObject indirect, PDXObject xobject) throws IOException;
    
}
