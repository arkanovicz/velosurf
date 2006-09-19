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

package velosurf.i18n;

import velosurf.util.Logger;
import velosurf.util.StringLists;
import velosurf.util.ServletLogWriter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;
import java.io.IOException;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * <p>Localization filter. It's goal is to redirect or forward incoming unlocalized http requests (depending on the
 * choosen method, <code>FORWARD</code> or <code>REDIRECT</code>) towards an address taking into account the best match
 * between requested locales and supported locales, and also to deduce the locale from URLS (when <code>REDIRECT</code>
 * is used).</p>
 *
 * <p>Optional init parameters:
 * <ul>
 * <li>supported-locales: default value is "en", after an attempt to programatically determine it<sup>(1)</sup>. Note: if present,
 * this parameter can also - and should rather be - declared as a
 * global context-param rather than a filter specific init-param so that the Localizer tool, if present, can find it.</li>
 * <li>localization-method: <code>redirect</code> or <code>forward</code>, default is <code>redirect</code>.</li>
 * <li>match-host & rewrite-host: not yet implemented.<sup>(2)</sup></li>
 * <li>match-uri & rewrite-uri: the regular expression against which an unlocalized uri is matched, and the replacement uri, where
 * <code>@</code> represents the locale and $1, $2, ... the matched sub-patterns. Defaults are <code>^/(.*)$</code>
 * for match-uri and <code>/@/$1</code> for rewrite-uri.(2)</li>
 * <li>match-query-string & rewrite-query-string: not yet implemented.(2)</li>
 * <li>match-url & rewrite-url: not yet implemented.(2)</li>
 * </ul>
 * </p>
 *
 *
 * <p><b><small>(1)</small></b>
 * <p><b><small>(2)</small></b> The different match- and rewrite- parameters pairs are mutually exclusive. All matches are case-insensitive.
 * When using the redirect method, POST parameters are lost.</p>
 *
 * <p>When the REDIRECT method is used, these supplementary parameters (mutually exclusive) allow the filter to deduce
 * the locale from already localized URLs:
 * <ul>
 * <li>inspect-host: not yet implemented.</li>
 * <li>inspect-uri: default is <code>^/(.*)/</code>.
 * <li>inspect-query-string: not yet implemented.</li>
 * <li>inspect-url: not yet implemented.</li>
 * </ul>
 * </p>
 *
 */

public class LocalizationFilter implements Filter {

    private FilterConfig _config = null;

    protected String _defaultSupportedLocales = "en";

    protected List<Locale> _supportedLocales = null;

    protected String _defaultMatchUri = "^/(.*)$";
    protected String _defaultRewriteUri = "/@/$1";
    protected Pattern _matchUri = null;
    protected String _rewriteUri = null;

    protected String _defaultInspectUri = "^/(.*)/";
    protected Pattern _inspectUri = null;

    protected static final int REDIRECT = 1;
    protected static final int FORWARD = 2;

    protected int _l10nMethod = REDIRECT;

    public synchronized void init(FilterConfig config) throws ServletException {
        _config = config;

        /* logger initialization */
        if (!Logger.isInitialized()) {
            Logger.setWriter(new ServletLogWriter(config.getServletContext()));
        }

        String param;

        /* uri */
        _matchUri = Pattern.compile(getInitParameter("match-uri",_defaultMatchUri),Pattern.CASE_INSENSITIVE);
        _rewriteUri = getInitParameter("rewrite-uri",_defaultRewriteUri);
        _inspectUri = Pattern.compile(getInitParameter("inspect-uri",_defaultInspectUri),Pattern.CASE_INSENSITIVE);

        /* method */
        param = getInitParameter("localization-method","REDIRECT");
        if (param.equalsIgnoreCase("redirect")) {
            _l10nMethod = REDIRECT;
        }
        else if (param.equalsIgnoreCase("forward")) {
            _l10nMethod = FORWARD;
        } else {
            Logger.error("LocalizationFilter: '"+param+"' is not a valid l10n method ('redirect' or 'forward').");
        }

        /* supported locales */
        findSupportedLocales(_config);
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpSession session = request.getSession(true); /* we'll store the active locale in it */
        HttpServletResponse response = (HttpServletResponse)servletResponse;

        Locale locale = null;
        boolean shouldRedirectOrForward = true;

        /* Now, what is the current locale ?
           Guess #1 is the URI, if already localized (only for REDIRECT method).
           Guess #2 is the session attribute 'active-locale'.
           Guess #3 is a cookie 'used-locale'.
           Guess #4 is from the Accepted-Language header.
        */

        /* Guess #1 - deduce from URI */
        if (_l10nMethod == REDIRECT) {
            Matcher matcher = _inspectUri.matcher(request.getRequestURI());
            if (matcher.find()) {
                String candidate = matcher.group(1);
                locale = HTTPLocalizerTool.parseLocale(candidate);
                shouldRedirectOrForward = false;
            }
            Logger.trace("l10n: URI locale = "+locale);
        }


        if (locale == null) {
            /* Guess #2 - is there an attribute in the session? */
            locale = (Locale)session.getAttribute("active-locale");
            Logger.trace("l10n: session locale = "+locale);

            if (locale == null) {
                /* Guess #3 - is there a cookie?*/
                Cookie cookies[] = request.getCookies();
                if (cookies != null) {
                    for(Cookie cookie:cookies) {
                        if ("active-locale".equals(cookie.getName())) {
                            locale = HTTPLocalizerTool.parseLocale(cookie.getValue());
                        }
                    }
                }
                Logger.trace("l10n: cookies locale = "+locale);

                if(locale == null) {
                    /* Guess #4 - use the Accepted-Language HTTP header */
                    List<Locale> requestedLocales = HTTPLocalizerTool.getRequestedLocales(request);
                    locale = HTTPLocalizerTool.getBestMatchingLocale(requestedLocales,_supportedLocales);
                    Logger.trace("l10n: Accepted-Language header best matching locale = "+locale);

                    if (locale == null) {
                        /* Oh well... */
                        locale = HTTPLocalizerTool.getDefaultLocale();
                        Logger.warn("LocalizationFilter: Cannot guess the proper locale... using the default locale: "+locale);
                    }
                }
            }
        }

        /* sets the session atribute and the cookies */
        if (locale != null) {
            session.setAttribute("active-locale",locale);
            Cookie localeCookie = new Cookie("active-locale",locale.toString());
            localeCookie.setPath("/");
            response.addCookie(localeCookie);
        }

        /* no need to forward if already forwarded! */
        if(_l10nMethod == FORWARD) {
            Boolean forwarded = (Boolean)session.getAttribute("l10n-forwarded") == null;
            if(forwarded == null) {
                forwarded = Boolean.valueOf(false);
            }
            shouldRedirectOrForward = !forwarded.booleanValue();
            session.setAttribute("l10n-forwarded",Boolean.valueOf(shouldRedirectOrForward));
        }

        if (shouldRedirectOrForward) {
            //  && (i = _rewriteUri.indexOf("@")) != -1) ?

            String rewriteUri = _rewriteUri.replaceFirst("@",locale.toString());
            String newUri = _matchUri.matcher(request.getRequestURI()).replaceFirst(rewriteUri);

            switch(_l10nMethod) {
                case REDIRECT:
                    Logger.trace("l10n: redirecting request to "+newUri);
                    response.sendRedirect(newUri);
                    break;
                case FORWARD:
                    RequestDispatcher dispatcher = _config.getServletContext().getRequestDispatcher(newUri);
                    if (dispatcher == null) {
                        Logger.error("l10n: cannot find a request dispatcher for path '"+newUri+"'");
                        /* What to do now ? */
                        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    } else {
                        Logger.trace("l10n: forwarding request to "+newUri);
                        dispatcher.forward(request,response);
                    }
                    break;
            }
        } else {
            Logger.trace("l10n: letting request pass towards "+request.getRequestURI());
            chain.doFilter(request,response);
        }
    }

    protected synchronized void findSupportedLocales(FilterConfig config) {
        /* look in the filter init-params */
        String param = config.getInitParameter("supported-locales");
        if (param == null) {
            /* look in the webapp context-params */
            param = config.getServletContext().getInitParameter("supported-locales");
            if (param == null) {
                int i;
                if (_rewriteUri != null && (i=_rewriteUri.indexOf("@")) != -1) {
                    _supportedLocales = HTTPLocalizerTool.getSupportedLocales(_config.getServletContext(),_rewriteUri.substring(0,i));
                    if(Logger.getLogLevel() <= Logger.TRACE_ID) {
                        Logger.trace("l10n: supported locales = " + StringLists.join(Arrays.asList(_supportedLocales),","));
                    }
                    if (_supportedLocales != null && _supportedLocales.size() > 0) {
                        return;
                    }
                }
                Logger.error("LocalizationFilter: Cannot find any supported locale! Please add a 'supported-locale' context-param.");

                /* use default */
                param = _defaultSupportedLocales;
            }
        }
        _supportedLocales = new ArrayList<Locale>();
        String[] list = param.split(",");
        for(String code:list) {
            _supportedLocales.add(new Locale(code));
        }
    }

    protected String getInitParameter(String key) {
        return _config.getInitParameter(key);
    }

    protected String getInitParameter(String key,String defaultValue) {
        String param = _config.getInitParameter(key);
        return param == null ? defaultValue : param;
    }

    public void destroy() {
    }

}
