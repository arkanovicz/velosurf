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

package velosurf.web.auth;

import java.io.IOException;
import java.util.Locale;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import velosurf.util.*;
import velosurf.web.i18n.Localizer;
import velosurf.web.i18n.Localizer;

/**
 * <p>This class is a servlet filter used to protect web pages behind an authentication mechanism. When a
 * non-authenticated user requests a protected page, (s)he is redirected towards the login page and thereafter,
 * if (s)he loggued in successfully, towards his(her) initially requested page.</p>
 *
 * <p>Authentication is performed via a CRAM (challenge-response authentication mechanism).
 * Passwords are never transmitted in clear.</p>
 *
 * <p>This filter works in conjunction with an Authenticator object that must be present in the session scope
 * of the toolbox and with a javascript password encryption function.</p>
 *
 * <p>To use it, you just have to map protected urls (and especially, the target of the login form, this is
 * very important for the authentication to work properly!) to go through this filter, as in :</p>
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
 * <p>The password is encrypted in an irreversible manner into an <i>answer</i>, and to check the login,
 * the answer that the client sends back to the server is compared to the correct awaited answer.</p>
 *
 * <p>The javascript file <i>login.js.vtl</i> contains the necessary encryption functions. It uses
 * the <i>bignum.js</i> library file. You will find those files in <code>/src/resources/auth</code>
 * or in the auth-i18n sample webapp.</p>
 *
 * <p>The filter expect the login to be present in the HTTP 'login' form field, and the answer in
 * the 'answer' form field (which should be all right if you use the login.js.vtl as is). The action of the form
 * is never used (since the filter will redirect the user towards the page asked before the login), but <b>it must
 * be catched by an url-pattern of this filter</b>. You can for instance define a mapping towards "/process_login".</p>
 *
 * <p>The loggued state is materialized by the presence of a user Object in the session under
 * the <i>user</i> key. This user object in the one returned by the abstract method Authenticator.getUser(login).</p>
 *
 * <p>This filter will search for an occurrence of a localizer tool in the session toolbox to resolve some values.
 * The presence of this localizer is optional.</p>
 *
 * <p>Optional configuration parameters:
 * <ul><li>max-inactive: delay upon which an inactive user is disconnected in seconds.
 * The default value is one hour.</li>
 * <li>login-page: the login page URI. The "<code>@</code>" pattern applies as well. Default is '/login.html'.</li>
 * <li>authenticated-index-page: the default page once authenticated. The "<code>@</code>" pattern applies as well.
 * Default is '/loggued.html'.</li>
 * <li>bad-login-message: the message to be displayed in case of bad login. If this parameter is not
 * specified, the filter will try to get a reference from the localizer tool and ask it for a "badLogin"
 * message, and if this fails, it will simply use "Bad login or password.".</li>
 * <li>disconnected-message: the message to be displayed when the user is disconnected after a period
 * of inactivity on the site. Same remark if this parameter is not supplied: the filter will search
 * for a "disconnected" message in the localizer tool if present, and otherwise display "You have been disconnected."</li>
 * </ul>
 * </p>
 *
 *
 * @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 *
 */


// FIXME: check we are thread-safe

public class AuthenticationFilter implements Filter {

    protected FilterConfig _config = null;

    protected int _maxInactive = 3600;

    protected String _loginPage = "/login.html.vtl";
    protected String _authenticatedIndexPage = "/index.html.vtl";

    protected String _badLoginMessage = null;
    protected String _badLoginMsgKey = "badLogin";
    protected static final String _defaultBadLoginMessage = "Bad login or password.";

    protected String _disconnectedMessage = null;
    protected String _disconnectedMsgKey = "disconnected";
    protected static final String _defaultDisconnectedMessage = "You have been disconnected.";

    /**
     * Whether _indexPage, _loginPage or _authenticatedIndexPage contains a @ to be resolved.
     */
    protected boolean _resolveLocale = false;

    public void init(FilterConfig config) throws ServletException {
        _config = config;

        /* logger initialization */
        if (!Logger.isInitialized()) {
            Logger.setWriter(new ServletLogWriter(config.getServletContext()));
        }

        /* max-inactive */
        String param = _config.getInitParameter("max-inactive");
        if (param != null) {
            try {
                _maxInactive = Integer.parseInt(param);
            } catch (NumberFormatException nfe) {
                Logger.error("AuthenticationFilter: bad format for the max-inactive parameter: "+param);
            }
        }
        /* login page */
        param = _config.getInitParameter("login-page");
        if (param != null) {
            _loginPage = param;
            _resolveLocale |= _loginPage.indexOf("@") != -1;
        }
        /* authenticated index page */
        param = _config.getInitParameter("authenticated-index-page");
        if (param != null) {
            _authenticatedIndexPage = param;
            _resolveLocale |= _authenticatedIndexPage.indexOf("@") != -1;
        }
        /* bad login message */
        _badLoginMessage = _config.getInitParameter("bad-login-message");
        /* disconnected message */
        _disconnectedMessage = _config.getInitParameter("disconnected-message");
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpSession session = request.getSession(false);
        HttpServletResponse response = (HttpServletResponse)servletResponse;

        String uri = request.getRequestURI();

        /* check to see if the current user has been disconnected
           note that this test will fail when the servlet container
           reuses session ids */
        boolean disconnected = false;
        String reqId = request.getRequestedSessionId();
        if (reqId != null && (session == null || !session.getId().equals(reqId))) {
            disconnected = true;
        }

        String login=null,password = null;
        Localizer localizer = null;
        String loginPage;
        String authenticatedIndexPage;

        if (_resolveLocale) {
            /* means the pages uris need the current locale */
            Locale locale = null;

            if (session != null) {
                locale = (Locale)session.getAttribute("velosurf.i18n.active-locale"); /* TODO: gather 'active-locale' handling in HTTPLocalizerTool */
            }

            if (locale == null)
            {
                Logger.error("auth: cannot find the active locale in the session!");
                Logger.error("auth: the LocalizationFilter must reside before this filter in the filters chain.");
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }
            loginPage = _loginPage.replaceAll("@",locale.toString());
            authenticatedIndexPage = _authenticatedIndexPage.replaceAll("@",locale.toString());
        } else {
            loginPage = _loginPage;
            authenticatedIndexPage = _authenticatedIndexPage;
        }

        if (session != null
                && session.getId().equals(request.getRequestedSessionId()) /* not needed in theory */
                && session.getAttribute("velosurf.auth.user") != null) {
            /* already loggued*/

            /* if asked to logout, well, logout! */
            if (uri.endsWith("/logout.do")) {
                Logger.trace("auth: user logged out");
                session.removeAttribute("velosurf.auth.user");
                response.sendRedirect(loginPage);
            } else {
                /* if the request is still pointing on /login.html, redirect to /auth/index.html */
                if (uri.equals(loginPage)) {
                    Logger.trace("auth: redirecting loggued user to "+authenticatedIndexPage);
                    response.sendRedirect(authenticatedIndexPage);
                } else {
                    Logger.trace("auth: user is authenticated.");
                    chain.doFilter(new SavedRequestWrapper(request),response);
                }
            }
        } else {
            /* never protect the login page itself */
            if (uri.equals(loginPage)) {
                chain.doFilter(request,response);
                return;
            }
            if (session == null) {
                session = request.getSession(true);
            } else {
                /* clear any previous loginMessage */
                session.removeAttribute("loginMessage");

                /* try to find a localizer tool for login messages*/
                localizer = ToolFinder.findTool(session,Localizer.class);
                Logger.trace("auth: "+(localizer==null?"localizer not found.":" found a localizer with locale: "+localizer.getLocale()));
            }
            session.removeAttribute("velosurf.auth.user");

            if ( uri.endsWith("/login.do")
                    && (login = request.getParameter("login")) != null
                    && (password = request.getParameter("password")) != null
                    && session.getId().equals(request.getRequestedSessionId())) {
                // a user is trying to log in

                // get a reference to the authenticator tool
                BaseAuthenticator auth = ToolFinder.findTool(session,BaseAuthenticator.class);

                if (auth == null) {
                    Logger.error("auth: cannot find any reference to the authenticator tool in the session!");
                    /* Maybe the current user tried to validate an expired login form... well... ask him again... */
                    response.sendRedirect(loginPage);
                    return;
                }
                // check answer
                if (auth.checkLogin(login,password)) {
                    // login ok
                    Logger.info("auth: user '"+login+"' successfully loggued in.");
                    session.setAttribute("velosurf.auth.user",auth.getUser(login));
                    if (_maxInactive > 0) {
                        Logger.trace("auth: setting session max inactive interval to "+_maxInactive);
                        session.setMaxInactiveInterval(_maxInactive);
                    }
                    session.removeAttribute("challenge");
                    session.removeAttribute("authenticator");
                    // then handle the former request if not null
                    SavedRequest savedRequest = (SavedRequest)session.getAttribute("velosurf.auth.saved-request");
                    if (savedRequest == null) {
                        // redirect to /auth/index.html
                        Logger.trace("auth: redirecting newly loggued user to "+authenticatedIndexPage);
                        response.sendRedirect(authenticatedIndexPage);
                    } else {
                        String formerUri = savedRequest.getRequestURI();
                        Logger.trace("auth: redirecting newly loggued user to "+formerUri);
                        response.sendRedirect(formerUri);
                    }
                } else {
                    Logger.warn("auth: user "+login+" made an unsuccessfull login attempt.");
                    String message = _badLoginMessage != null ?
                            _badLoginMessage :
                            getMessage(localizer,_badLoginMsgKey,_defaultBadLoginMessage);
                    session.setAttribute("loginMessage",message);
                    // redirect to login page
                    Logger.trace("auth: redirecting unauthenticated user to "+loginPage);
                    response.sendRedirect(loginPage);
                }
            } else {
                /* save the original request */
                Logger.trace("auth: saving request towards "+uri);
                session.setAttribute("velosurf.auth.saved-request",SavedRequest.saveRequest(request));
                if(disconnected) {
                    String message = _disconnectedMessage != null ?
                            _disconnectedMessage :
                            getMessage(localizer,_disconnectedMsgKey,_defaultDisconnectedMessage);
                    session.setAttribute("loginMessage",message);
                }
                // redirect to login page
                Logger.trace("auth: redirecting unauthenticated user to "+loginPage);
                response.sendRedirect(loginPage);
            }
        }
    }

    protected String getMessage(Localizer localizer,String key,String defaultMessage) {
        String message = null;
        if (localizer != null) {
            message = localizer.get(key);
        }
        return message == null ? defaultMessage : message;
    }

    public void destroy() {
    }

}
