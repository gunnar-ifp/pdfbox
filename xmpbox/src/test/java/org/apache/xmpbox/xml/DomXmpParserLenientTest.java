/*****************************************************************************
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 ****************************************************************************/
package org.apache.xmpbox.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;

import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.type.ArrayProperty;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:gunnar.brand@interface-projects.de">Gunnar Brand</a>
 * @since 30.05.2022
 */
public class DomXmpParserLenientTest
{

    @Test
    public void test0() throws IOException, XmpParsingException
    {
        try (
            InputStream is = DomXmpParserLenientTest.class.getResourceAsStream("/invalidxmp/noxxmpmeta0.xml");
        ) {
            DomXmpParser me = new DomXmpParser();
            me.setStrictParsing(false);
            XMPMetadata meta = me.parse(is);
            assertTrue(meta.getDublinCoreSchema().getCreators().size()>0);
            assertEquals("viola", meta.getDublinCoreSchema().getCreators().get(0));
            assertEquals("In welchen Situationen kann der pdfbox Sie im Unternehmen unterst\u00FCtzen?", meta.getDublinCoreSchema().getTitle());
        } catch (XmpParsingException e) {
            fail(e.getMessage());
        }
    }

    
    @Test
    public void test0Strict() throws IOException, XmpParsingException
    {
        boolean failed = false;
        try (
            InputStream is = DomXmpParserLenientTest.class.getResourceAsStream("/invalidxmp/noxxmpmeta0.xml");
        ) {
            DomXmpParser me = new DomXmpParser();
            me.setStrictParsing(true);
            XMPMetadata meta = me.parse(is);
            assertTrue(meta.getDublinCoreSchema().getCreators().size()>0);
            assertEquals("viola", meta.getDublinCoreSchema().getCreators().get(0));
            assertEquals("In welchen Situationen kann der pdfbox Sie im Unternehmen unterst\u00FCtzen?", meta.getDublinCoreSchema().getTitle());
        } catch (XmpParsingException e) {
            failed = true;
        }
        assertTrue("Parsing succeeded in strict mode", failed);
    }

    
    @Test
    public void test1() throws IOException, XmpParsingException
    {
        try (
            InputStream is = DomXmpParserLenientTest.class.getResourceAsStream("/invalidxmp/noxxmpmeta1.xml");
        ) {
            DomXmpParser me = new DomXmpParser();
            me.setStrictParsing(false);
            XMPMetadata meta = me.parse(is);
            assertTrue(meta.getDublinCoreSchema().getCreators().size()>0);
            assertEquals("Andreas", meta.getDublinCoreSchema().getCreators().get(0));
            assertEquals("Dokument2", meta.getDublinCoreSchema().getTitle());
        } catch (XmpParsingException e) {
            fail(e.getMessage());
        }
    }


    @Test
    public void testAttributes() throws IOException, XmpParsingException
    {
        try (
            InputStream is = DomXmpParserLenientTest.class.getResourceAsStream("/invalidxmp/noxxmpmeta2.xml");
        ) {
            DomXmpParser me = new DomXmpParser();
            me.setStrictParsing(false);
            XMPMetadata meta = me.parse(is);
//            dumpMeta(meta);
            assertTrue(meta.getDublinCoreSchema().getCreators().size()>0);
            assertEquals("viola", meta.getDublinCoreSchema().getCreators().get(0));
            assertEquals("Microsoft Word - Anforderungen an ein Web-CMS.doc", meta.getDublinCoreSchema().getTitle());
        } catch (XmpParsingException e) {
            fail(e.getMessage());
        }
    }
    
    
    private static void dumpMeta(XMPMetadata meta)
    {
          System.out.println(
              meta.getAllSchemas().stream()
              .flatMap(s -> s.getAllProperties().stream())
              //.flatMap(af -> af.getAllAttributes().stream())
              .map(a -> a instanceof ArrayProperty ? ((ArrayProperty)a).getAllProperties().toString() : a.toString() )
              .collect(Collectors.joining("\n"))
          );
    }



}
