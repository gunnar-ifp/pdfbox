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

import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.io.IOException;

import org.apache.fontbox.cff.CharStringCommand.CommandProvider;
import org.apache.fontbox.cff.CharStringCommand.Type1Command;
import org.apache.fontbox.type1.Type1CharStringReader;

/**
 * This class represents and renders a Type 1 CharString.
 *
 * @author Villu Ruusmann
 * @author John Hewson
 */
public class Type1CharString
{
    protected Type1CharStringReader font;
    protected final String fontName;
    protected final String glyphName;
    protected final byte[] bytes;
    protected final byte[][] subrs;
    
    private volatile GeneralPath path = null;
    private int width;

    /**
     * Constructs a new Type1CharString object.
     *
     * @param font Parent Type 1 CharString font.
     * @param fontName Name of the font.
     * @param glyphName Name of the glyph.
     */
    public Type1CharString(Type1CharStringReader font, String fontName, String glyphName, byte[] bytes, byte[][] subrs)
    {
        this.font = font;
        this.fontName = fontName;
        this.glyphName = glyphName;
        this.bytes = bytes;
        this.subrs = subrs;
    }

    
    // todo: NEW name (or CID as hex)
    public String getName()
    {
        return glyphName;
    }

    
    public CommandProvider<Type1Command> getType1Stream()
    {
        return new Type1CharStringParser(fontName, glyphName, bytes, subrs);
    }
    
    
    /**
     * Returns the bounds of the renderer path.
     * @return the bounds as Rectangle2D
     */
    public Rectangle2D getBounds()
    {
    	return getPath().getBounds2D();
    }

    
    /**
     * Returns the advance width of the glyph.
     * @return the width
     */
    public int getWidth()
    {
        // TODO: if no path, we could just parse bytes up to the HSBW/SBW command...
        getPath();
        return width;
    }

    /**
     * Returns the path of the character.
     * @return the path
     */
    public GeneralPath getPath()
    {
        if ( path == null ) {
        	synchronized (this) {
        		if ( path==null ) {
        		    Type1CharStringDrawer drawer = new Type1CharStringDrawer(font, fontName, glyphName);
                    GeneralPath p = drawer.getPath();
        		    try {
                        getType1Stream().stream(drawer);
//                        p.trimToSize();
                    }
        		    catch (RuntimeException e) {
                        p.closePath();
                        throw e;
        		    }
                    catch (IOException e) {
                        p.closePath();
                        throw new RuntimeException(e);
                    } finally {
                        width = drawer.getWidth();
                    }
        		    path = p;
        		}
        	}
		}
        return path;
    }

}
