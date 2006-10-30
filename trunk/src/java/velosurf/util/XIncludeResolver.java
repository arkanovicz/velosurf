/*
 * Copyright 2003 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package velosurf.util;

import java.util.List;
import java.util.ArrayList;
import java.net.URL;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;

import javax.servlet.ServletContext;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Content;
import org.jdom.Text;
import org.jdom.output.XMLOutputter;
import org.jdom.input.SAXBuilder;

/** <p>A basic JDOM XInclude resolver that will also work with a document base inside WEB-INF
 * and with war archives</p>
 *
 * @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */

public class XIncludeResolver {

    private String base = null;
    private ServletContext context = null;

    public XIncludeResolver(String base,ServletContext ctx) {
        this.base = base;
        this.context = ctx;
    }

    public XIncludeResolver(String base) {
        this.base = base;
    }

    public Document resolve(Document doc) throws Exception {
        Element root = doc.getRootElement();
        /* special case where the root element is an XInclude element */
        if (isXIncludeElement(root)) {
            int pos = doc.indexOf(root);
            List<Content> resolved = include(root);
            /* doc.detachRootElement(); */
            doc.setContent(pos,resolved);
        } else {
            resolveChildren(root);
        }
        return doc;
    }

    private boolean isXIncludeElement(Element element) {
        return element.getName().equals("include") && element.getNamespacePrefix().equals("xi");
    }

    private void resolveChildren(Element parent) throws Exception {
        List<Element> children = parent.getChildren();
        for(int i=0;i<children.size();i++) {
            Element child = (Element)children.get(i);
            if (isXIncludeElement((Element)child)) {
                int pos = parent.indexOf(child);
                List<Content> resolved = include((Element)child);
                parent.setContent(pos,resolved);
            } else {
                resolveChildren(child);
            }
        }
    }

    private List<Content> include(Element xinclude) throws Exception {
        List<Content> result = null;
        String href = xinclude.getAttributeValue("href");
        String base = xinclude.getAttributeValue("base",this.base);
        boolean parse = true;
        String p = xinclude.getAttributeValue("parse");
        if (p!=null) {
            parse = "xml".equals(p);
        }
        assert(href != null && base != null);
        String content = null;
        int i = href.indexOf(':');
        if(i != -1) {
            /* absolute URL... */
            content = readStream(new URL(href).openStream());
        } else {
            if (!href.startsWith("/")) {
                if(base.length() == 0) {
                    base = "./";
                } else if (!base.endsWith("/")) {
                    base += "/";
                }
                href = base + href;
            }
            content = (context == null ? readStream(new FileInputStream(href)) : readStream(context.getResourceAsStream(href)));
        }
        if (parse) {
            content = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><root xmlns:xi=\"http://www.w3.org/2001/XInclude\">"+content+"</root>";
            Document parsed = resolve(new SAXBuilder().build(new StringReader(content))); /* TODO yet to prevent cyclic inclusions...*/
            result = (List<Content>)parsed.getRootElement().removeContent();
        } else {
            result = new ArrayList<Content>();
            result.add(new Text(content));
        }
        return result;
    }

    private String readStream(InputStream stream) throws Exception {
        StringBuilder result = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
            result.append('\n');
        }
        return result.toString();
    }
}