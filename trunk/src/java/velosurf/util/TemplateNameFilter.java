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

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Set;
import java.util.Iterator;
import java.util.HashSet;

/**
 * <p>This class is a forwarding filter which allows to hide ".vtl" from resource URLs.
 * It works by building a cache of all template names in the webapp, and adding </p>
 *
 * <p>The purpose of this feature is to allow URLs to be independant of the status of the resource:
 * regular file or template file, allowing this status to transparently change over time.
 *  You can store all resources in the same directory tree, templates having
 * an additional ".vtl" like in "foo.html.vtl" or "bar.js.vtl".</p>
 *
 * <p>In development mode, you can choose either to reset the cache periodically,
 * or manually with the "reset-cache" URI, or both.</p>
 *
 * <p>Initialization parameters:
 * <ul>
 * <li>template-extension: the targeted template extension (default: ".vtl").</li>
 * <li>reset-method: "periodic" or "manual" or "both" or "none" (default: "none").<li>
 * <li>reset-uri: the rest uri, for manual resets (default: "/reset-cache").
 * <li>reset-period: the period, in seconds, between two resets, for periodic resets (default: 120s).</li>
 * </ul>
 * </p>
 *
 * @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 *
 */

public class TemplateNameFilter implements Filter {

    /** the servlet context. */
    private ServletContext servletContext;

    /** targeted template extension. */
    private String templateExtension = ".vtl";

    /** NONE reset method. */
    private static final int RESET_NONE = 0;
    /** MANUAL reset method. */
    private static final int RESET_MANUAL = 1;
    /** PERIODIC reset method. */
    private static final int RESET_PERIODIC = 2;
    /** reset method. */
    private int resetMethod = RESET_NONE; /* bit-masked */

    /** reset uri. */
    private String resetUri = "/reset-cache";

    /** reset period. */
    private long resetPeriod = 120000; /* millisec */

    /* the set of template names. */
    private Set<String> templates = null;

    /* the time of the last reset. */
    private long lastReset;

    /**
     * init the filter.
     * @param filterConfig filter configuration
     * @throws ServletException
     */
    public void init(FilterConfig filterConfig) throws ServletException {
        servletContext = filterConfig.getServletContext();

        /* init parameters */
        String param,value;
        Enumeration params = filterConfig.getInitParameterNames();
        while(params.hasMoreElements()) {
            param = (String)params.nextElement();
            value = filterConfig.getInitParameter(param);
            if ("template-extension".equals(param)) {
                if (!value.startsWith(".")) {
                    value = "." + value;
                }
                templateExtension = value;
            } else if ("reset-method".equals(param)) {
                if ("manual".equals(value)) {
                  resetMethod = RESET_MANUAL;
                } else if ("periodic".equals(value)) {
                    resetMethod = RESET_PERIODIC;
                } else if ("both".equals(value)) {
                    resetMethod = RESET_MANUAL | RESET_PERIODIC;
                } else if (!"none".equals(value)) {
                    servletContext.log("[warn] TemplateNameFilter: reset-method should be one of 'none', 'manual', 'pediodic' or 'both'.");
                }
            } else if ("request-uri".equals(param)) {
                resetUri = value;
            } else if ("reset-period".equals(param)) {
                try {
                    resetPeriod = Integer.parseInt(value) * 1000;
                } catch (NumberFormatException nfe) {
                    servletContext.log("[warn] TemplateNameFilter: reset-period should be a number!");
                }
            } else {
                servletContext.log("[warn] TemplateNameFilter: unknown parameter '"+param+"' ignored.");
            }
        }

        /* builds the cache */
        buildsTemplateNamesList(null);
    }

    /**
     * Build the cache, which consists of a hash set containing all template names.
     *
     */
    private synchronized void buildsTemplateNamesList(HttpServletResponse response) {
        /* check again if the reset is necessary, the current thread may have been
        waiting to enter this method during the last reset */
        if ((resetMethod & RESET_PERIODIC) != 0 && System.currentTimeMillis() - lastReset < resetPeriod && templates != null) {
            return;
        }

        Set<String> result = new HashSet<String>();

        String path,entry;
        Set entries;
        LinkedList<String> paths = new LinkedList<String>();
        paths.add("/");
        while(paths.size() > 0) {
            path = (String)paths.removeFirst();
            entries = servletContext.getResourcePaths(path);
            for (Iterator i = entries.iterator();i.hasNext();) {
                entry = (String)i.next();
                /* ignore some entries... */
                if (entry.endsWith("/WEB-INF/") || entry.endsWith("/.svn/")) {
                    continue;
                }
                if (entry.endsWith("/")) {
                    paths.add(entry);
                }
                else if (entry.endsWith(templateExtension)) {
                    result.add(entry.substring(0,entry.length()-templateExtension.length()));
                }
            }
        }
        templates = result;
        lastReset = System.currentTimeMillis();

        if (response != null) {
            try {
                PrintWriter writer = response.getWriter();
                writer.println("<html><body>Cache reseted.</body></html>");
            } catch(IOException ioe) {

            }
        }
    }

    /**
     * doFilter method.
     * @param servletRequest request
     * @param servletResponse response
     * @param filterChain filter chain
     * @throws ServletException
     * @throws IOException
     */
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws ServletException, IOException {

        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpServletResponse response = (HttpServletResponse)servletResponse;

        String path = request.getRequestURI();

        /* I've been said some buggy containers where leaving the query string in the uri */
        int i;
        if ((i = path.indexOf("?")) != -1) {
            path = path.substring(0,i);
        }

        /* if the extension is already present, let it go... */
        if (path.endsWith(templateExtension)) {
            filterChain.doFilter(servletRequest,servletResponse);
            return;
        }

        /* is it time for a reset ? */
        long now = System.currentTimeMillis();
        if ((resetMethod & RESET_MANUAL) != 0 && path.equals(resetUri)) {
            lastReset = now - 2*resetPeriod;
            buildsTemplateNamesList(response);
        } else if ((resetMethod & RESET_PERIODIC) != 0 && now - lastReset > resetPeriod) {
            buildsTemplateNamesList(response);
        } else {
            if(templates.contains(path)) {
                /* forward the request with extension added */
                Logger.trace("vtl: forwarding request towards "+path+templateExtension);
                RequestDispatcher dispatcher = servletContext.getRequestDispatcher(path+templateExtension);
                dispatcher.forward(request,servletResponse);
            } else {
                /* normal processing */
                filterChain.doFilter(servletRequest,servletResponse);
            }
        }
    }

    /** Destroy the filter.
     *
     */
    public void destroy() {
    }
}
