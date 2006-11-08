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

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A null servlet, provided here for convenience, that will redirect the user to the forbidden uri (if provided
 * in the init parameter "forbidden-uri") or respond with the FORBIDDEN error code.
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 */

public class NullServlet extends HttpServlet
{
    private String forbiddenUri = null;

    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        forbiddenUri = config.getInitParameter("forbidden-uri");
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        doRequest(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        doRequest(request, response);
    }

    protected void doRequest(HttpServletRequest request,
                             HttpServletResponse response)
         throws ServletException, IOException
    {
        Logger.log("null servlet got hit: "+request.getRequestURI());
        if (forbiddenUri == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        } else {
            response.sendRedirect(forbiddenUri);
            /* other option...
		        RequestDispatcher dispatcher = request.getRequestDispatcher(forbiddenUri);
		        dispatcher.forward(request,response);
             */
        }
    }
}
