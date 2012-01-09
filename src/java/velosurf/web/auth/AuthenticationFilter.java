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
import java.net.URL;
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
import velosurf.web.l10n.Localizer;

/**
 * <p>This class is a servlet filter used to protect web pages behind an authentication mechanism. When a
 * non-authenticated user requests a private page, (s)he is redirected towards the login page and thereafter,
 * if (s)he logged in successfully, towards his(her) initially requested page.</p>
 *
 * <p>Authentication is performed via a CRAM (challenge-response authentication mechanism).
 * Passwords are encrypted using the method given as parameter to the Authenticator tool in toolbox.xml. The provided
 *  Javascript file /src/javascript/md5.js implements the HmacMD5 method on the client side.</p>
 *
 * <p>This filter works in conjunction with an Authenticator object that must be present in the session scope
 * of the toolbox and with a javascript password encryption function.</p>
 *
 * <p>To use it, you just have to map private urls (and especially, the target of the login form, this is
 * very important for the authentication to work properly!) to go through this filter, as in :</p>
 * <xmp>
 *   <filter>
 *     <filter-name>authentication</filter-name>
 *     <filter-class>auth.AuthenticationFilter</filter-class>
 *   </filter>
 *   <filter-mapping>
 *     <filter-name>authentication</filter-name>
 *     <url-pattern>/auth/*</url-pattern>
 *   </filter-mapping>
 * </xmp>
 *
 * <p>The password is encrypted in an irreversible manner into an <i>answer</i>, and to check the login,
 * the answer that the client sends back to the server is compared to the correct awaited answer.</p>
 *
 * <p>The filter expect the login to be present in the HTTP 'login' form field, and the answer in
 * the 'answer' form field (which should be all right if you use the login.vjs as is). The action of the form
 * is never used (since the filter will redirect the user towards the page asked before the login), but <b>it must
 * be catched by an url-pattern of this filter</b>. You can for instance define a mapping towards "/process_login".</p>
 *
 * <p>The logged state is materialized by the presence of a user Object in the session under
 * the <i>user</i> key. This user object in the one returned by the abstract method Authenticator.getUser(login).</p>
 *
 * <p>This filter will search for an occurrence of a localizer tool in the session toolbox to resolve some values.
 * The presence of this localizer is optional.</p>
 *
 * <p>Optional configuration parameters:
 * <ul>
 * <li><code>login-field</code>: name of the login form field (default: login).</li>
 * <li><code>password-field</code>: name of the password field (default: password).</li>
 * <li><code>login-key</code>: name of the session key used to store the login of the logged user.</li>
 * <li><code>user-key</code>: name of the session key used to store a reference to the logged user object.</li>
 * <li><code>max-inactive</code>: delay upon which an inactive user is disconnected in seconds.
 * The default value is one hour.</li>
 * <li><code>login-page</code>: the login page URI. The "<code>@</code>" pattern applies as well. Default is '/login.html'.</li>
 * <li><code>authenticated-index-page</code>: the default page once authenticated. The "<code>@</code>" pattern applies as well.
 * Default is '/logged.html'.</li>
 * <li><code>bad-login-message</code>: the message to be displayed in case of bad login. If this parameter is not
 * specified, the filter will try to get a reference from the localizer tool and ask it for a "badLogin"
 * message, and if this fails, it will simply use "Bad login or password.".</li>
 * <li><code>disconnected-message</code>: the message to be displayed when the user is disconnected after a period
 * of inactivity on the site. Same remark if this parameter is not supplied: the filter will search
 * for a "disconnected" message in the localizer tool if present, and otherwise display "You have been disconnected."</li>
 * <li><code>allow-guest</code>: allow the login "guest" (false by default) - the password is not checked, the "guest" user must exist in the database.
 * The only allowed uri is the login page, and it can be used from VTL:<br/> <code>#macro (redirect $url) $response.sendRedirect($url) #end #redirect("/login.do?login=guest&password=whatever")</code><br/>
 * you can also include a "redirect=" parameter</li>
 * <li><code>use-login-referer</code>: (sorry for reproducing the bugguy syntax from the HTTP RFC, it should be 'referrer' :-) ) use the HTTP request referrer after login if there is no saved request (default: no).</li>
 * </ul>
 * </p>
 *
 *
 * @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 *
 */

public class AuthenticationFilter implements Filter
{

    /**
     * Filter config.
     */
    private FilterConfig config = null;

    /**
     * Max inactive interval.
     */
    private int maxInactive = 3600;

    /**
     * Login field.
     */
    private String loginField = "login";

    /**
     * Password field.
     */
    private String passwordField = "password";

    /**
     * Login page.
     */
    private String loginPage = "/login.vhtml";

    /**
     * Index of the authenticated zone.
     */
    private String authenticatedIndexPage = "/index.vhtml";

    /**
     * Message in case of bad login.
     */
    private String badLoginMessage = null;

    /**
     * Message key in case of bad login.
     */
    private String badLoginMsgKey = "badLogin";

    /**
     * Default bad login message.
     */
    private static final String defaultBadLoginMessage = "Bad login or password.";

    /**
     * Message in case of disconnection.
     */
    private String disconnectedMessage = null;

    /**
     * Message key in case of disconnection.
     */
    private String disconnectedMsgKey = "disconnected";

    /**
     * Default message in case of disconnection.
     */
    private static final String defaultDisconnectedMessage = "You have been disconnected.";

    /**
     * Session key used to store logged user login
     */
    private String LOGIN = "velosurf.auth.login";

    /**
     * Session key used to store logged user object
     */
    private String USER = "velosurf.auth.user";

    /**
     * Session key used to store original pre-login request
     */
    public static final String REQUEST = "velosurf.auth.saved-request";

    /**
     * Should we use the referer to login.do?
     */
    private boolean useLoginReferer = false;

    /**
     * Should we use the referer to login.do?
     */
    private boolean allowGuest = false;

    /**
     * Initialization.
     * @param config filter config
     * @throws ServletException
     */
    public void init(FilterConfig config) throws ServletException
    {
        this.config = config;

        /* logger initialization */
        if (!Logger.isInitialized())
        {
            Logger.setWriter(new ServletLogWriter(config.getServletContext()));
        }

        /* max-inactive */
        String param = this.config.getInitParameter("max-inactive");
        if (param != null)
        {
            try
            {
                maxInactive = Integer.parseInt(param);
            }
            catch (NumberFormatException nfe)
            {
                Logger.error("AuthenticationFilter: bad format for the max-inactive parameter: "+param);
            }
        }

        /* login field */
        param = this.config.getInitParameter("login-field");
        if (param != null)
        {
            loginField = param;
        }

        /* password field */
        param = this.config.getInitParameter("password-field");
        if (param != null)
        {
            passwordField = param;
        }

        /* login session key */
        param = this.config.getInitParameter("login-key");
        if (param != null)
        {
            LOGIN = param;
        }

        /* user session key */
        param = this.config.getInitParameter("user-key");
        if (param != null)
        {
            USER = param;
        }

        /* login page */
        param = this.config.getInitParameter("login-page");
        if (param != null)
        {
            loginPage = param;
        }

        /* authenticated index page */
        param = this.config.getInitParameter("authenticated-index-page");
        if (param != null)
        {
            authenticatedIndexPage = param;
        }

        /* bad login message */
        badLoginMessage = this.config.getInitParameter("bad-login-message");

        /* disconnected message */
        disconnectedMessage = this.config.getInitParameter("disconnected-message");

        /* use login referer */
        param = this.config.getInitParameter("use-login-referer");
        useLoginReferer = (param != null && Boolean.parseBoolean(param));

	/* allow guests */
        param = this.config.getInitParameter("allow-guest");
        allowGuest = (param != null && Boolean.parseBoolean(param));

    }

    /**
     * Filtering.
     * @param servletRequest request
     * @param servletResponse response
     * @param chain filter chain
     * @throws IOException
     * @throws ServletException
     */
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpSession session = request.getSession(false);
        HttpServletResponse response = (HttpServletResponse)servletResponse;

        String uri = request.getRequestURI();

        String login = null, password = null;

        if (session != null
                && session.getId().equals(request.getRequestedSessionId()) /* not needed in theory */
                && session.getAttribute(USER) != null)
        {
            /* already logged*/

            /* need to refresh cached user instance in case it changed */
            refreshUserInstance(session);
            
            /* if asked to logout, well, logout! */
            if (uri.endsWith("/logout.do"))
            {
				doLogout(request,response,chain);
            }
            else
            {
                doProcessAuthentified(request,response,chain);
            }
        }
        else
        {
            /* never protect the login page itself */
            if (uri.equals(resolveLocalizedUri(request,loginPage)))
            {
                chain.doFilter(request,response);
                return;
            }
            if (session == null)
            {
                session = request.getSession(true);
            }
            else
            {
                /* clear any previous loginMessage */
                session.removeAttribute("loginMessage");
            }
            session.removeAttribute(USER);
            session.removeAttribute(LOGIN);
            if ( uri.endsWith("/login.do")
                    && (login = request.getParameter(loginField)) != null
                    && (password = request.getParameter(passwordField)) != null
                    && session.getId().equals(request.getRequestedSessionId()))
            {
                // a user is trying to log in

                if(allowGuest && login.equals("guest"))
                { // TODO parametrize "guest" -> GUEST
                    Logger.trace("[auth] logging in guest user");
                    doLogin(request,response,chain);
                }
                else
                {
                    // get a reference to the authenticator tool
                    BaseAuthenticator auth = ToolFinder.findSessionTool(session,BaseAuthenticator.class);
                    if (auth == null)
                    {
                        Logger.error("[auth] cannot find any reference to the authenticator tool in the session!");
                        /* Maybe the current user tried to validate an expired login form... well... ask him again... */
                        response.sendRedirect(resolveLocalizedUri(request,loginPage));
                        return;
                    }
                    // check answer
                    if (auth.checkLogin(login,password))
                    {
                        // login ok
                        doLogin(request,response,chain);
                    }
                    else
                    {
                        badLogin(request,response,chain);
                    }
                }

            }
            else
            {
                /* do not redirect to the logout */
                if (uri.endsWith("/logout.do"))
                {
                    response.sendRedirect(resolveLocalizedUri(request,loginPage));
                }
                else
                {
                    doRedirect(request,response,chain);
                }
            }
        }
    }

    protected void refreshUserInstance(HttpSession session)
    {
        session.setAttribute(USER, ToolFinder.findSessionTool(session,BaseAuthenticator.class).getUser((String)session.getAttribute(LOGIN)));
    }

    protected void doRedirect(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException, ServletException
    {
        /* save the original request */
        String uri = request.getRequestURI();
        Logger.trace("[auth] saving request towards "+uri);
        HttpSession session = request.getSession();
        session.setAttribute(REQUEST,SavedRequest.saveRequest(request));

        /* check to see if the current user has been disconnected
           note that this test will fail when the servlet container
           reuses session ids */
        boolean disconnected = false;
        String reqId = request.getRequestedSessionId();
        if (reqId != null && (session == null || !session.getId().equals(reqId)))
        {
            disconnected = true;
        }

        if(disconnected)
        {
             String message = disconnectedMessage != null ?
                 disconnectedMessage :
                 getMessage(ToolFinder.findSessionTool(session,Localizer.class),disconnectedMsgKey,defaultDisconnectedMessage);
             session.setAttribute("loginMessage",message);
        }
        // redirect to login page
        String loginPage = resolveLocalizedUri(request,this.loginPage);
        Logger.trace("[auth] redirecting unauthenticated user to "+loginPage);
        response.sendRedirect(loginPage);
    }

    protected void doLogin(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException, ServletException
    {
        String login = request.getParameter(loginField);
        Logger.info("[auth] user '"+login+"' successfully logged in.");
        HttpSession session = request.getSession();
        session.setAttribute(USER, ToolFinder.findSessionTool(session,BaseAuthenticator.class).getUser(login));
        session.setAttribute(LOGIN,login);
        if (maxInactive > 0)
        {
            Logger.trace("[auth] setting session max inactive interval to "+maxInactive);
             session.setMaxInactiveInterval(maxInactive);
        }
        session.removeAttribute("challenge");
        session.removeAttribute("authenticator");
        // then handle the former request if not null
        goodLogin(request,response,chain);
    }

    protected void goodLogin(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException, ServletException
    {
        HttpSession session = request.getSession();

        // trying to use the "redirect=" parameter
        String redirect = request.getParameter("redirect");
        Logger.trace("[auth] redirect = "+redirect);
        if(redirect != null)
        {
          Logger.trace("[auth] redirecting newly logged user to 'redirect' param: "+redirect);
          response.sendRedirect(redirect);
          return;
        }

        SavedRequest savedRequest = (SavedRequest)session.getAttribute(REQUEST);
        if (savedRequest == null || savedRequest.getRequestURI().endsWith("/login.do"))
        {
            // try to redirect to the referrer url
            if(useLoginReferer)
            {
                String referer = request.getHeader("Referer");
                if(referer != null)
                { // TODO: some referer URLs should be avoided (login.do, logout.do...)
                    // only keep path and query
                    URL url = new URL(referer);
                    String path = url.getPath();
                    String query = url.getQuery();
                    String anchor = url.getRef();
                    String dest = path+(query != null && query.length()>0?"?"+query:"")+(anchor != null && anchor.length()>0?"#"+anchor:"");
                    Logger.trace("[auth] redirecting newly logged user to login.do referer: "+dest);
                    response.sendRedirect(dest);
                    return;
                }
            }

            // redirect to /auth/index.html
            String authIndex = resolveLocalizedUri(request,getAuthenticatedIndexPage(session));
            Logger.trace("[auth] redirecting newly logged user to "+authIndex);
            response.sendRedirect(authIndex);
        }
        else
        {
            session.removeAttribute(REQUEST);
            String formerUrl = savedRequest.getRequestURI();
            String query =  savedRequest.getQueryString();
            query = (query == null ? "" : "?"+query);
            formerUrl += query;
            Logger.trace("[auth] redirecting newly logged user to "+formerUrl);
            response.sendRedirect(formerUrl);
        }
    }

    protected void badLogin(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException, ServletException
    {
        Logger.warn("[auth] user " + request.getParameter(loginField) + " made an unsuccessfull login attempt.");
        HttpSession session = request.getSession();
        String message = badLoginMessage != null ?
            badLoginMessage :
            getMessage(ToolFinder.findSessionTool(session,Localizer.class),badLoginMsgKey,defaultBadLoginMessage);
        session.setAttribute("loginMessage",message);
        // redirect to login page
        String loginPage = resolveLocalizedUri(request,this.loginPage);
        Logger.trace("[auth] redirecting unauthenticated user to "+loginPage);
        response.sendRedirect(loginPage);
    }

    protected void doProcessAuthentified(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException, ServletException
    {
        /* if the request is still pointing on /login.html or /login.do, redirect to /auth/index.html */
        String uri = request.getRequestURI();
        HttpSession session = request.getSession();
        if (uri.equals(resolveLocalizedUri(request,loginPage)) || uri.endsWith("/login.do"))
        {
            goodLogin(request,response,chain);
        }
        else
        {
            Logger.trace("[auth] user is authenticated.");
            SavedRequest saved = (SavedRequest)session.getAttribute(REQUEST);
            if (saved != null && saved.getRequestURL().equals(request.getRequestURL()))
            {
                session.removeAttribute(REQUEST);
                chain.doFilter(new SavedRequestWrapper(request,saved),response);
            }
            else
            {
               chain.doFilter(request,response);
            }
        }
    }

    protected void doLogout(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException, ServletException
    {
        HttpSession session = request.getSession();
        Logger.trace("[auth] user logged out");
        session.removeAttribute(USER);
        session.removeAttribute(LOGIN);
        String loginPage = resolveLocalizedUri(request,this.loginPage);
        response.sendRedirect(loginPage);
    }


    protected String resolveLocalizedUri(HttpServletRequest request,String uri)
    {
        if (uri.indexOf('@')!=-1)
        {
            /* means the uri need the current locale */
            Locale locale = null;
            HttpSession session = request.getSession();
            if (session != null)
            {
                locale = (Locale)session.getAttribute("velosurf.l10n.active-locale"); /* TODO: gather 'active-locale' handling in HTTPLocalizerTool */
            }

            if (locale == null)
            {
                Logger.error("[auth] cannot find the active locale in the session!");
                Logger.error("[auth] the LocalizationFilter must reside before this filter in the filters chain.");
                //response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return uri;
            }
            uri = uri.replaceAll("@",locale.toString());
        }
        return uri;
    }

    protected String getAuthenticatedIndexPage(HttpSession session)
    {
        return authenticatedIndexPage;
    }

    /**
     * Message getter.
     * @param localizer localizer
     * @param key key
     * @param defaultMessage default message
     * @return localized message or default message
     */
    protected String getMessage(Localizer localizer,String key,String defaultMessage)
    {
        String message = null;
        if (localizer != null)
        {
            message = localizer.get(key);
        }
        return message == null || message.equals(key) ? defaultMessage : message;
    }

    /**
     * Destroy the filter.
     */
    public void destroy()
    {
    }
}
