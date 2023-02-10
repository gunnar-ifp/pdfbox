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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This class provides a glyph to GeneralPath conversion for true type fonts.
 * Based on code from Apache Batik, a subproject of Apache XMLGraphics.
 *
 * @see
 * <a href="http://xmlgraphics.apache.org/batik">http://xmlgraphics.apache.org/batik</a>
 * 
 * Contour rendering ported from PDF.js, viewed on 14.2.2015, rev 2e97c0d
 *
 * @see
 * <a href="https://github.com/mozilla/pdf.js/blob/c0d17013a28ee7aa048831560b6494a26c52360c/src/core/font_renderer.js">pdf.js/src/core/font_renderer.js</a>
 *
 */
final class GlyphRenderer
{
    private static final Log LOG = LogFactory.getLog(GlyphRenderer.class);

    
    /**
     * Returns the path of the glyph.
     */
    public static GeneralPath getPath(GlyphDescription glyphDescription)
    {
        final Point[] points = new Point[glyphDescription.getPointCount()];
        final int max = describe(points, glyphDescription);
        return calculatePath(points, max);
    }

    
    /**
     * Set the points of a glyph from the GlyphDescription.
     */
    private static int describe(Point[] points, GlyphDescription gd)
    {
        int max = 0, contour = 0, contourEndIndex = -1;
        for (int i = 0, len = points.length; i < len; i++)
        {
            if (i > contourEndIndex)
            {
                contourEndIndex = gd.getEndPtOfContours(contour++);
                max = Math.max(max, contourEndIndex - i + 1);
            }
            points[i] = new Point(gd.getXCoordinate(i), gd.getYCoordinate(i),
                (gd.getFlags(i) & GlyfDescript.ON_CURVE) != 0, contourEndIndex == i);
        }
        return max;
    }

    /**
     * Use the given points to calculate a GeneralPath.
     *
     * @param points the points to be used to generate the GeneralPath
     *
     * @return the calculated GeneralPath
     */
    private static GeneralPath calculatePath(Point[] points, int max)
    {
        final boolean isLog = LOG.isTraceEnabled();
        final GeneralPath path = new GeneralPath();
        final List<Point> contour = new ArrayList<Point>(max + 2);
        int start = 0;
        for (int i = 0, len = points.length; i < len; ++i)
        {
            if (points[i].endOfContour)
            {
                final Point firstPoint = points[start];
                final Point lastPoint = points[i];
                contour.clear();
                if (firstPoint.onCurve)
                {
                    // using start point at the contour end
                    for (int j = start; j <= i; ++j) contour.add(points[j]);
                    contour.add(firstPoint);
                }
                else if (lastPoint.onCurve)
                {
                    // first is off-curve point, trying to use one from the end
                    contour.add(lastPoint);
                    for (int j = start; j <= i; ++j) contour.add(points[j]);
                }
                else
                {
                    // start and end are off-curve points, creating implicit one
                    Point pmid = midValue(firstPoint, lastPoint);
                    contour.add(pmid);
                    for (int j = start; j <= i; ++j) contour.add(points[j]);
                    contour.add(pmid);
                }
                
                moveTo(isLog, path, contour.get(0));
                for (int j = 1, clen = contour.size(); j < clen; j++)
                {
                    final Point pnow = contour.get(j);
                    if (pnow.onCurve)
                    {
                        lineTo(isLog, path, pnow);
                        continue;
                    }
                    final Point pnext = contour.get(j + 1);
                    if (pnext.onCurve)
                    {
                        quadTo(isLog, path, pnow, pnext);
                        ++j;
                    }
                    else
                    {
                        quadTo(isLog, path, pnow, midValue(pnow, pnext));
                    }
                }
                path.closePath();            
                start = i + 1;
            }
        }
        return path;
    }

    
    private static void moveTo(boolean isLog, GeneralPath path, Point point)
    {
        if (isLog) LOG.trace("moveTo: " + String.format(Locale.US, "%d, %d", point.x, point.y));
        path.moveTo(point.x, point.y);
    }

    
    private static void lineTo(boolean isLog, GeneralPath path, Point point)
    {
        if (isLog) LOG.trace("lineTo: " + String.format(Locale.US, "%d, %d", point.x, point.y));
        path.lineTo(point.x, point.y);
    }


    private static void quadTo(boolean isLog, GeneralPath path, Point ctrlPoint, Point point)
    {
        if (isLog) {
            LOG.trace("quadTo: " + String.format(Locale.US, "%d, %d %d, %d", ctrlPoint.x, ctrlPoint.y, point.x, point.y));
        }
        path.quadTo(ctrlPoint.x, ctrlPoint.y, point.x, point.y);
    }


    // this creates an onCurve point that is between point1 and point2
    private static Point midValue(Point point1, Point point2)
    {
        // this constructs an on-curve, non-endofcountour point
        // Math doesn't lie: a + (b - a) / 2 = (2a + b - a) / 2 = (a + b) / 2 
        return new Point((point1.x + point2.x) / 2, (point1.y + point2.y) / 2, true, false);
    }

    
    /**
     * This class represents one point of a glyph.
     */
    private static final class Point
    {
        final int x, y;
        final boolean onCurve, endOfContour;

        Point(int x, int y, boolean onCurve, boolean endOfContour)
        {
            this.x = x;
            this.y = y;
            this.onCurve = onCurve;
            this.endOfContour = endOfContour;
        }

        @Override
        public String toString()
        {
            return String.format(Locale.US, "Point(%d, %d, %s, %s)",
                x, y, onCurve ? "onCurve" : "", endOfContour ? "endOfContour" : "");
        }
    }
    
}
