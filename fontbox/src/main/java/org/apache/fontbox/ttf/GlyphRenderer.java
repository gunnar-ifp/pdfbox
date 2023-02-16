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

import java.awt.geom.GeneralPath;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This class provides a glyph to GeneralPath conversion for true type fonts.
 * <p>
 * Originally based on code from <a href="http://xmlgraphics.apache.org/batik">Apache Batik</a>, a subproject of Apache XMLGraphics.<br>
 * Contour rendering ported from 
 * <a href="https://github.com/mozilla/pdf.js/blob/c0d17013a28ee7aa048831560b6494a26c52360c/src/core/font_renderer.js">
 * PDF.js project, viewed on 14.2.2015, rev 2e97c0d, pdf.js/src/core/font_renderer.js</a><br>
 * <p>
 * 
 */
class GlyphRenderer
{
    private static final Log LOG = LogFactory.getLog(GlyphRenderer.class);
    
    final static GlyphRenderer DEFAULT = new GlyphRenderer();
    
    
    /**
     * Returns the path of the glyph.
     */
    GeneralPath render(GlyphDescription glyphDescription, GeneralPath path)
    {
        final boolean isLog = LOG.isTraceEnabled();
        final int pointCount = glyphDescription.getPointCount();
        int contour = 0;
        for ( int contourStart = 0; contourStart<pointCount; ) {
            final int contourEnd = glyphDescription.getEndPtOfContours(contour++);
            // make sure start and end are on curve:
            // use variable start and end points (end is inclusive), end can be larger than contourEnd.
            // extra joining point at start and end of curve possible (if idx==start and/or end > contourEnd).
            final int start, end;
            final int joinX, joinY;
            if (  glyphDescription.isOnCurve(contourStart) ) {
                // first point is on curve: start normal, end with joiner = first point
                joinX = glyphDescription.getXCoordinate(contourStart);
                joinY = glyphDescription.getYCoordinate(contourStart);
                start = contourStart + 1;
                end   = contourEnd   + 1;
            }
            else if ( glyphDescription.isOnCurve(contourEnd) ) {
                // first is off-curve point, try using last point
                // start with joiner = last point, end normal
                joinX = glyphDescription.getXCoordinate(contourEnd);
                joinY = glyphDescription.getYCoordinate(contourEnd);
                start = contourStart;
                end   = contourEnd;
            }
            else {
                // start and end are off-curve points, creating implicit one as extra start and end point
                // start with joiner = middle between start and end, end with the same
                // Math: a + (b - a) / 2 = (2a + b - a) / 2 = (a + b) / 2 
                joinX = (glyphDescription.getXCoordinate(contourStart) + glyphDescription.getXCoordinate(contourEnd)) / 2;
                joinY = (glyphDescription.getYCoordinate(contourStart) + glyphDescription.getYCoordinate(contourEnd)) / 2;
                start = contourStart;
                end   = contourEnd + 1;
            }
            
            // render contour, if idx reaches end, we always use the join point.
            moveTo(isLog, path, joinX, joinY);
            ONCURVE:
            for ( int idx = start; idx<=end; idx++ ) {
                if ( idx==end ) {
                    lineTo(isLog, path, joinX, joinY);
                    continue;
                }

                int x1 = glyphDescription.getXCoordinate(idx);
                int y1 = glyphDescription.getYCoordinate(idx);
                
                if ( glyphDescription.isOnCurve(idx) ) {
                    lineTo(isLog, path, x1, y1);
                    continue;
                }

                // see: http://www.fifi.org/doc/libttf2/docs/glyphs.htm
                // off curve "loop": two neighboring off curve control points points max 
                while ( ++idx<=end ) {
                    if ( idx==end ) {
                        quadTo(isLog, path, x1, y1, joinX, joinY);
                        continue ONCURVE;
                    }
                    else if ( glyphDescription.isOnCurve(idx) ) {
                        int x2 = glyphDescription.getXCoordinate(idx);
                        int y2 = glyphDescription.getYCoordinate(idx);
                        quadTo(isLog, path, x1, y1, x2, y2);
                        continue ONCURVE;
                    }
                    else {
                        int x2 = glyphDescription.getXCoordinate(idx);
                        int y2 = glyphDescription.getYCoordinate(idx);
                        quadTo(isLog, path, x1, y1, (x1 + x2) / 2, (y1 + y2) / 2);
                        x1 = x2;
                        y1 = y2;
                    }
                }
            }
            path.closePath();            
            contourStart = contourEnd + 1;
        }
        return path;
    }

    
    void moveTo(boolean isLog, GeneralPath path, int x, int y)
    {
        if (isLog) LOG.trace("moveTo: " + x + ", " + y);
        path.moveTo(x, y);
    }

    
    void lineTo(boolean isLog, GeneralPath path, int x, int y)
    {
        if (isLog) LOG.trace("lineTo: " + x + ", " + y);
        path.lineTo(x, y);
    }


    void quadTo(boolean isLog, GeneralPath path, int x1, int y1, int x2, int y2)
    {
        if (isLog) LOG.trace("quadTo: " + x1 + ", " + y2 + "; " + x2 + ", " + y2);
        path.quadTo(x1, y1, x2, y2);
    }

}
