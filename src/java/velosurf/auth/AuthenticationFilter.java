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

package velosurf.auth;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import velosurf.util.Logger;
import velosurf.util.SavedRequest;
import velosurf.util.SavedRequestWrapper;
import velosurf.util.ToolFinder;

/**
 * <p>This class is a servlet filter used to protect web pages behind an authentication mechanism.
 * It works in conjunction with an Authenticator object that must be present in the session scope
 * of the toolbox.</p>
 *
 * <p>To use it, you just have to map protected urls to go through this filter, as in :</p>
 * <pre>
 *   <filter>
 *     <filter-name>authentication</filter-name>
 *     <filter-class>auth.AuthenticationFilter</filter-class>
 *   </filter>
 *   <filter-mapping>
 *     <filter-name>authentication</filter-name>
 *     <url-pattern>/auth/*</url-pattern>
 *   </filter-mapping>
 * </pre>
 *
 * <p>The password is never transmitted in clear between the client page and the server. It is
 * encrypted in an irreversible manner into an <i>answer</i>, and to check the login,
 * the answer that the client sends back to the server is compared to the correct awaited answer.</p>
 *
 * <p>The javascript file <i>login.js.vtl</i> contains the necessary encryption functions. It uses
 * the <i>bignum.js</i> library file. You will find those files in <code>/src/resources/auth</code>
 * or in the authentication sample webapp. 
 *
 *
 * <p>The filter expect the login to be present in the HTTP 'login' form field, and the </p>
 *
 * <p>The loggued state is materialized by the presence of a User object in the session. This User
 * object in the one returned by the method Authenticator.getUser(login).</p>
 *
 *
 *
 *
 * @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 *
 */


// FIXME: check we are thread-safe

public class AuthenticationFilter implements Filter {

    private FilterConfig _config = null;

    public void init(FilterConfig config) throws ServletException {
        _config = config;
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpSession session = request.getSession(false);
        HttpServletResponse response = (HttpServletResponse)servletResponse;

        String login,challenge,answer = null;

        if (session != null
                && session.getId().equals(request.getRequestedSessionId()) // ?! When would this happen??? Does it help hacking?
                && session.getAttribute("user") != null) {
            // already loggued
            // if the request is still pointing on /login.html, redirect to /auth/index.html
            if (request.getRequestURI().equals(""))
            chain.doFilter(servletRequest,servletResponse);
        } else {
            if (session == null) {
                // not loggued
                session = request.getSession(true);
                session.setMaxInactiveInterval(1);
            }
            session.removeAttribute("user");

            if ( (login = request.getParameter("login")) != null
                    && (answer = request.getParameter("answer")) != null
                    && session.getId().equals(request.getRequestedSessionId())) { // ?! When would this happen??? Does it help hacking?
                // a user is trying to log in
                // get a reference to the authenticator tool
                Authenticator auth = ToolFinder.findTool(session,Authenticator.class);

                if (auth == null) {
                    Logger.fatal("AuthenticationFilter: cannot find any reference to the authenticator tool in the session!");
                    session.setAttribute("loginMessage","Erreur interne.");
                    response.sendRedirect("/login.html");
                    return;
                }
                // check answer
                if (auth.checkLogin(login,answer)) {
                    // login ok
                    Logger.info("User "+login+" successfully loggued in.");
                    session.setAttribute("user",auth.getUser(login));
                    session.removeAttribute("challenge");
                    session.removeAttribute("authenticator");
                    // then handle the former request if not null
                    if (session.getAttribute("saved-request") == null) {
                        // redirect to /auth/index.html
                        response.sendRedirect("/auth/index.html");
                    } else {
                        chain.doFilter(new SavedRequestWrapper(request),servletResponse);
                    }
                } else {
                    Logger.warn("User "+login+" made an unsuccessfull login attempt.");
                    session.setAttribute("loginMessage","Mauvais login ou mot de passe.");
                    // redirect to login page
                    response.sendRedirect("/login.html");
                }
            } else {
            // not loggued...
            // save the original request
                session.setAttribute("unauth_request",SavedRequest.saveRequest(request));
                // redirect to login page
                response.sendRedirect("/login.html");
            }
        }
    }

  public void destroy() {
  }

}
