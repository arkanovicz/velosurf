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

package velosurf.validation;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;

import velosurf.web.VelosurfTool;
import velosurf.web.l10n.Localizer;
import velosurf.util.ToolFinder;
import velosurf.util.Logger;
import velosurf.util.UserContext;
//import velosurf.util.UserContext;
import velosurf.context.EntityReference;
import velosurf.context.DBReference;

/**
 * <p>This class is an optional filter that will validate query data according to the <code>velosurf.entity</code> query parameter
 * (may be multivalued).
 * If data pass all validation constraints, the filter will let the request pass towards the form action. Otherwise,
 * it will redirect back the user to the original form (using the referer query header). In this case, the filter will
 * populate the session with the given values (escaped) so that they can be put back in the form.</p>
 *
 */
public class ValidationFilter implements Filter {
    /** filter config */
    private FilterConfig config;

    /** key used to identity an entity to be validated in HTTP parameters */
    private static final String ENTITY_KEY = "velosurf.entity";

    /**
     * initialization.
     * @param filterConfig filter config
     * @throws ServletException
     */
    public void init(FilterConfig filterConfig) throws ServletException {
        config = filterConfig;
    }

    /**
     * Filtering.
     * @param servletRequest request
     * @param servletResponse response
     * @param filterChain filter chain
     * @throws IOException
     * @throws ServletException
     */
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpServletResponse response = (HttpServletResponse)servletResponse;
        HttpSession session = null;
        Map map = null;
        String[] entities = null;

        boolean filter =  (request.getParameter(ENTITY_KEY) != null);

        boolean accept = true;

        if (filter) {
            session = request.getSession(true);
            /* FIXME: the next call won't work when using a non-standard Velosurf config file
               defined in toolbox.xml */
            DBReference db = VelosurfTool.getDefaultInstance(config.getServletContext());
            map = new HashMap();
            UserContext userContext = (UserContext)session.getAttribute(UserContext.USER_CONTEXT_KEY);
            if(userContext == null) {
                userContext = db.getUserContext();
                Localizer localizer = ToolFinder.findSessionTool(session,Localizer.class);
                if (localizer != null) {
                    userContext.setLocalizer(localizer);
                } else {
                    userContext.setLocale(request.getLocale());
                }
                session.setAttribute(UserContext.USER_CONTEXT_KEY,userContext);
            }

            if (db != null) {
                Map<String,Object> params = request.getParameterMap();
                Object[] array;
                for(Map.Entry entry:(Set<Map.Entry<String,Object>>)params.entrySet()) {
                    array = (Object[])entry.getValue();
                    map.put(entry.getKey(), array.length == 1 ? array[0] : array);
                }
                entities = (String[])params.get(ENTITY_KEY);
                if (entities != null) {
                    for(String entity:entities) {
                        EntityReference entityRef = (EntityReference)db.get(entity);
                        if (entityRef == null) {
                            Logger.error("validation: entity '"+entity+"' not found!");
                            response.sendError(HttpServletResponse.SC_BAD_REQUEST,"validation: entity '"+entity+"' does not exist!");
                            return;
                        } else {
                            accept &= entityRef.validate(map);
                        }
                    }
                }
            } else {
                Logger.error("validation: could not get a database connexion!");
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Velosurf tool not found in session!");
                return;
            }
        }

        if (filter && !accept) {
            Logger.trace("validation: values did not pass checking");
            String referer = request.getHeader("Referer");
            if (referer == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Referer header needed!");
            } else {
                for(String entity:entities) {
                    session.setAttribute(entity,map);
                }
                Logger.trace("validation: redirecting request towards "+referer);
                response.sendRedirect(referer);
            }
        } else {
            if (filter/* && acept*/) {
                Logger.trace("validation: values did pass checking");
            }
            filterChain.doFilter(servletRequest,servletResponse);
        }
    }

    /**
     * Destroy the filter.
     */
    public void destroy() {
    }
}
