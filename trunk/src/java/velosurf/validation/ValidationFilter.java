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

package velosurf.web.validation;

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
import velosurf.util.ToolFinder;
import velosurf.util.Logger;
import velosurf.context.EntityReference;

/**
 * Filter that will validate query data according to the "velosurf.entity" query parameter.
 */
public class ValidationFilter implements Filter {

    private static final String ENTITY_KEY = "velosurf.entity";

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpServletResponse response = (HttpServletResponse)servletResponse;
        HttpSession session = request.getSession(false);

        boolean accept = true;

        if (session != null) {
            VelosurfTool db = ToolFinder.findTool(session,VelosurfTool.class);
            if (db != null) {
                Map params = request.getParameterMap();
                Map map = new HashMap();
                Object[] array;
                for(Map.Entry entry:(Set<Map.Entry>)params.entrySet()) {
                    array = (Object[])entry.getValue();
                    map.put(entry.getKey(), array.length == 1 ? array[0] : array);
                }
                String[] entities = (String[])params.get(ENTITY_KEY);
                if (entities != null) {
                    for(String entity:entities) {
                        EntityReference entityRef = (EntityReference)db.get(entity);
                        if (entityRef == null) {
                            Logger.warn("validation: entity '"+entity+"' not found!");
                        } else {
                            accept &= entityRef.validate(map);
                        }
                    }
                }
            }
        }

        if(accept) {
            filterChain.doFilter(servletRequest,servletResponse);
        } else {
            String referer = request.getHeader("Referer");
            if (referer == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Referer header needed!");
            } else {
                response.sendRedirect(referer);
            }
        }
    }

    public void destroy() {
    }
}
