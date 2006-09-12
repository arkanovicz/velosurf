package velosurf.auth;

import java.io.IOException;
import java.util.Map;

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
                Authenticator auth = null;
                Map sessionTools = (Map)session.getAttribute("org.apache.velocity.tools.view.servlet.ServletToolboxManager:session-tools");
                if (sessionTools != null) {
                    auth = (Authenticator)sessionTools.get("authent");
                }
                if (auth == null) {
                    Logger.fatal("AuthenticationFilter: cannot find any reference to the authenticator tool in the session!");
                    session.setAttribute("loginMessage","Mauvais login ou mot de passe."); // FIXME: localize!
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
                    session.setAttribute("loginMessage","Mauvais login ou mot de passe."); // FIXME: localize!
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
