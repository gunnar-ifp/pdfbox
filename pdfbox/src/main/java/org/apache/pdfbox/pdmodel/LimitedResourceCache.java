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
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map.Entry;

import org.apache.fontbox.FontBoxFont;
import org.apache.fontbox.util.Cleaner;
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
 * A resource cached based on size limited LRU maps and soft references.
 * <p>
 * Note: Soft references are buggy in many JVMs and will result in OOM errors when processing
 * a document sequentially and using an unbounded cache.
 * <p>
 * TODO: This implementation (like the orignal one) is not thread safe.
 *
 * @author Gunnar Brand
 */
public class LimitedResourceCache implements ResourceCache
{
    private enum Type {
        /** {@link PDFont } */
        USERFONT(512,  -1, 100),
        /** {@link FontBoxFont} */
        BASEFONT( 64,  -1, 100),
        /** {@link PDPropertyList} */
        PROPLIST(512,      100),
        /** {@link PDColorSpace } */
        COLSPACE( 64,      100),
        /** {@link PDAbstractPattern } */
        PATTERNS( 64,       10),
        /** {@link PDShading } */
        SHADINGS( 64,       10),
        /** {@link PDExtendedGraphicsState} */
        EXTGSTAT( 64, 512,  10),
        /** {@link PDXObject} */
        XOBJECTS( 16, 128,   1);

        public final int limit, boost, pages;

        private Type(int limit, int pages) {
            this(limit, limit, pages);
        }

        private Type(int size, int boost, int pages) {
            this.limit = size;
            this.boost = boost;
            this.pages = pages;
        }
    }

    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
    private final LRUCacheMap[] maps;
    private Object currentPage;
    private int page;
    {
        maps = new LRUCacheMap[Type.values().length];
        for ( Type t : Type.values() ) maps [t.ordinal()] = new LRUCacheMap(t);
    }


    @Override
    public void clear()
    {
        for ( LRUCacheMap map : maps ) map.clear();
        Cleaner.CleanableReference.cleanQueue(queue);
    }


    @Override
    public void startPage(Object pageId)
    {
        if ( currentPage != null && !currentPage.equals(pageId) ) {
            page++;
            for ( LRUCacheMap map : maps ) map.clean(map.type.limit, page);
        }
        currentPage = pageId;
    }


    @Override
    public void endPage()
    {
        // remove unused references as if we were on the next page already
        for ( LRUCacheMap map : maps ) map.clean(map.type.boost, page + 1);
        Cleaner.CleanableReference.cleanQueue(queue);
    }


    @Override
    public PDFont getFont(COSObject indirect)
    {
        return get(Type.USERFONT, indirect);
    }


    @Override
    public void put(COSObject indirect, PDFont font)
    {
        put(Type.USERFONT, indirect, font);
    }


    @Override
    public FontBoxFont getBaseFont(PDStream stream) throws IOException
    {
        return stream.getCOSObject().isDirect() ? null : get(Type.BASEFONT, stream.getCOSObject());
    }


    @Override
    public void put(PDStream stream, FontBoxFont basefont) throws IOException
    {
        if ( stream.getCOSObject().isDirect() ) return;
        put(Type.BASEFONT, stream.getCOSObject(), basefont);
    }


    @Override
    public PDColorSpace getColorSpace(COSObject indirect)
    {
        return get(Type.COLSPACE, indirect);
    }


    @Override
    public void put(COSObject indirect, PDColorSpace colorSpace)
    {
        put(Type.COLSPACE, indirect, colorSpace);
    }


    @Override
    public PDExtendedGraphicsState getExtGState(COSObject indirect)
    {
        return get(Type.EXTGSTAT, indirect);
    }


    @Override
    public void put(COSObject indirect, PDExtendedGraphicsState extGState)
    {
        put(Type.EXTGSTAT, indirect, extGState);
    }


    @Override
    public PDShading getShading(COSObject indirect) throws IOException
    {
        return get(Type.SHADINGS, indirect);
    }


    @Override
    public void put(COSObject indirect, PDShading shading) throws IOException
    {
        put(Type.SHADINGS, indirect, shading);
    }


    @Override
    public PDAbstractPattern getPattern(COSObject indirect) throws IOException
    {
        return get(Type.PATTERNS, indirect);
    }


    @Override
    public void put(COSObject indirect, PDAbstractPattern pattern) throws IOException
    {
        put(Type.PATTERNS, indirect, pattern);
    }


    @Override
    public PDPropertyList getProperties(COSObject indirect)
    {
        return get(Type.PROPLIST, indirect);
    }


    @Override
    public void put(COSObject indirect, PDPropertyList propertyList)
    {
        put(Type.PROPLIST, indirect, propertyList);
    }


    @Override
    public PDXObject getXObject(COSObject indirect) throws IOException
    {
        return get(Type.XOBJECTS, indirect);
    }


    @Override
    public void put(COSObject indirect, PDXObject xobject) throws IOException
    {
//      if ( xobject instanceof PDImageXObject ) {
//          PDImageXObject img = (PDImageXObject)xobject;
//          // TODO: filter by assumed bitmap size (w*h*bpp)
//      }
        put(Type.XOBJECTS, indirect, xobject);
    }


    public void clearGraphics()
    {
        maps[Type.COLSPACE.ordinal()].clear();
        maps[Type.PATTERNS.ordinal()].clear();
        maps[Type.SHADINGS.ordinal()].clear();
        maps[Type.EXTGSTAT.ordinal()].clear();
        maps[Type.XOBJECTS.ordinal()].clear();
    }


    public void clearFonts()
    {
        maps[Type.USERFONT.ordinal()].clear();
        maps[Type.BASEFONT.ordinal()].clear();
    }


    public void clearProperties()
    {
        maps[Type.PROPLIST.ordinal()].clear();
    }


    public void clearXObjects()
    {
        maps[Type.XOBJECTS.ordinal()].clear();
    }


    private void put(Type type, Object key, Object value)
    {
        LRUCacheMap map = maps[type.ordinal()];
        map.put(key, new PagedSoftReference(value, queue, page));
        map.put++;
    }


    @SuppressWarnings("unchecked")
    private <T> T get(Type type, Object indirect)
    {
        LRUCacheMap map = maps[type.ordinal()];
        PagedSoftReference ref = map.get(indirect);
        if ( ref==null ) {
            map.miss++;
            return null;
        }
        Object obj = ref.get();
        if ( obj==null ) {
            map.empty++;
            return null;
        }
        ref.page = page;
        map.hit++;
        return (T)obj;
    }


    private final static class LRUCacheMap extends LinkedHashMap<Object, PagedSoftReference>
    {
        private static final long serialVersionUID = 1L;

        final Type type;
        int hit, miss, put, empty;

        public LRUCacheMap(Type type) throws IllegalArgumentException {
            super(16, 0.75f, true);
            this.type = type;
        }

        @Override
        public void clear() {
            values().forEach(Reference::clear);
            super.clear();
            clearStats();
        }

        public void clearStats() {
            hit = miss = put = empty = 0;
        }

        public void clean(int limit, int page) {
            final int oldest = page - type.pages;
            // in theory a two pass approach is necessary: remove cleaned references first, then limit to size.
            for ( Iterator<PagedSoftReference> it = values().iterator(); it.hasNext(); ) {
                PagedSoftReference r = it.next();
                if ( limit >= 0 && size() > limit || r.get() == null || r.page < oldest ) {
                    r.clear();
                    it.remove();
                }
            }
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s: hits=%d, empty=%d, miss=%d, puts=%d, %d", type.name(), hit, empty, miss, put, size());
        }

        @Override
        protected boolean removeEldestEntry(Entry<Object, PagedSoftReference> entry) {
            if ( type.boost >= 0 && size() > type.boost ) {
                entry.getValue().clear();
                return true;
            }
            return false;
        }
    }


    private final static class PagedSoftReference extends Cleaner.CleanableSoftReference<Object>
    {
        int page;

        public PagedSoftReference(Object referent, ReferenceQueue<? super Object> queue, int page) {
            super(referent, queue);
            this.page = page;
        }

        @Override
        public String toString() {
            return page + "=" + get();
        }
    }

}
