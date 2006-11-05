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

package velosurf.web.i18n;

import velosurf.util.Logger;
import velosurf.util.StringLists;
import velosurf.util.ServletLogWriter;
import velosurf.util.ToolFinder;

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
import java.util.Enumeration;
import java.util.Set;
import java.util.Collections;
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
 * <li>supported-locales: comma separated list of supported locales ; if not provided, there is an attempt to programatically determine it<sup>(1)</sup>.
 * No default value provided.</li>
 * <li>default-locale: the locale to be used by default (after four checks: the incoming URI, the session, cookies, and the request headers).
 * No default value provided.</li>
 * <li>localization-method: <code>forward</code> or <code>redirect</code>, default is <code>redirect</code>.
 * <li>match-host & rewrite-host: not yet implemented.<sup>(2)</sup></li>
 * <li>match-uri & rewrite-uri: the regular expression against which an unlocalized uri is matched, and the replacement uri, where
 * <code>@</code> represents the locale and $1, $2, ... the matched sub-patterns. Defaults are <code>^/(.*)$</code>
 * for match-uri and <code>/@/$1</code> for rewrite-uri.(2)</li>
 * <li>match-query-string & rewrite-query-string: not yet implemented.(2)</li>
 * <li>match-url & rewrite-url: not yet implemented.(2)</li>
 * </ul>
 * </p>
 *
 * <p><b><small>(1)</small></b> for now, to find supported locales if this parameter is not provided,
 * the filter try to use the rewrite-uri param and to check for the existence of corresponding directories (only if the rewriting
 * string contains a pattern like '/@/', that is if you use directories to store localized sites).
 * <p><b><small>(2)</small></b> The different match- and rewrite- parameters pairs are mutually exclusive. All matches are case-insensitive.
 * When using the redirect method, POST parameters are lost.</p>
 *
 * <p>When the REDIRECT method is used, these supplementary parameters (mutually exclusive) allow the filter to
 * know whether or not an incoming URI is localized.
 * <ul>
 * <li>inspect-host: not yet implemented.</li>
 * <li>inspect-uri: default is <code>^/(.+)(?:/|$)</code>.
 * <li>inspect-query-string: not yet implemented.</li>
 * <li>inspect-url: not yet implemented.</li>
 * </ul>
 * </p>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */

public class LocalizationFilter implements Filter {

    private FilterConfig _config = null;

    private List<Locale> _supportedLocales = null;
    private Locale _defaultLocale = null;

    private static int SECONDS_IN_YEAR = 31536000;

    private static String _defaultMatchUri = "^/(.*)$";
    private static String _defaultRewriteUri = "/@/$1";
    private static String _defaultInspectUri = "^/(.+)(?:/|$)";
    private  Pattern _matchUri = null;
    private String _rewriteUri = null;
    private Pattern _inspectUri = null;

    private static final int FORWARD = 1;
    private static final int REDIRECT = 2;

    private int _l10nMethod = REDIRECT;

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
        param = getInitParameter("localization-method","redirect");
        if (param.equalsIgnoreCase("redirect")) {
            _l10nMethod = REDIRECT;
        } else if (param.equalsIgnoreCase("forward")) {
            _l10nMethod = FORWARD;
        } else {
            Logger.error("LocalizationFilter: '"+param+"' is not a valid l10n method; should be 'forward' or 'redirect'.");
        }

        /* supported locales */
        findSupportedLocales(_config);

        /* default locale */
        _defaultLocale = getMatchedLocale(getInitParameter("default-locale"));
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpSession session = request.getSession(true); /* we'll store the active locale in it */
        HttpServletResponse response = (HttpServletResponse)servletResponse;

        Locale locale = null;

        /* should an action (forward/redirect) be taken by the filter? */
        boolean shouldAct = true;

        /* Now, what is the current locale ?
           Guess #1 is the URI, if already localized (only for REDIRECT method).
           Guess #2 is the session attribute 'active-locale'.
           Guess #3 is a cookie 'used-locale'.
           Guess #4 is from the Accepted-Language header.
        */

        Logger.trace("l10n: URI ="+request.getRequestURI());

        /* Guess #1 - if using redirect method, deduce from URI (and, while looking at URI, fills the shouldAct vairable) */
        if (_l10nMethod == REDIRECT) {
            Matcher matcher = _inspectUri.matcher(request.getRequestURI());
            if (matcher.find()) {
                String candidate = matcher.group(1);
                locale = getMatchedLocale(candidate);
                if (locale != null) {
                    shouldAct = false;
                }
            }
            Logger.trace("l10n: URI locale = "+locale);
        } else {
            /* for the forward method, shouldAct rule is: always only when already forwarded */
            Boolean forwarded = (Boolean)request.getAttribute("velosurf.i18n.l10n-forwarded");
            if(forwarded != null && forwarded.booleanValue()) {
                shouldAct = false;
            }
        }

        if (locale == null) {
            /* Guess #2 - is there an attribute in the session? */
            locale = (Locale)session.getAttribute("velosurf.i18n.active-locale");
            Logger.trace("l10n: session locale = "+locale);

            if (locale == null) {
                /* Guess #3 - is there a cookie?*/
                Cookie cookies[] = request.getCookies();
                if (cookies != null) {
                    for(Cookie cookie:cookies) {
                        if ("velosurf.i18n.active-locale".equals(cookie.getName())) {
                            locale = getMatchedLocale(cookie.getValue());
                        }
                    }
                }
                Logger.trace("l10n: cookies locale = "+locale);

                if(locale == null) {
                    /* Guess #4 - use the Accepted-Language HTTP header */
                    List<Locale> requestedLocales = getRequestedLocales(request);
                    locale = getPreferredLocale(requestedLocales);
                    Logger.trace("l10n: Accepted-Language header best matching locale = "+locale);
                }
            }
        }

        if (locale == null && _defaultLocale != null) {
            locale = _defaultLocale;
        }

        if (locale != null) {
            Localizer tool = ToolFinder.findTool(session,Localizer.class);
            if (tool != null) {
                tool.setLocale(locale);
            } else {
                Logger.warn("l10n: cannot find any Localizer tool!");
            }
        }

        /* sets the session atribute and the cookies */
        session.setAttribute("velosurf.i18n.active-locale",locale);
        Cookie localeCookie = new Cookie("velosurf.i18n.active-locale",locale.toString());
        localeCookie.setPath("/");
        localeCookie.setMaxAge(SECONDS_IN_YEAR);
        response.addCookie(localeCookie);

        if (shouldAct) {
            //  && (i = _rewriteUri.indexOf("@")) != -1) ?

            String rewriteUri = _rewriteUri.replaceFirst("@",locale.toString());
            String newUri = _matchUri.matcher(request.getRequestURI()).replaceFirst(rewriteUri);
            RequestDispatcher dispatcher;

            switch(_l10nMethod) {
                case REDIRECT:
                    Logger.trace("l10n: redirecting request to "+newUri);
                    response.sendRedirect(newUri);
                    break;
                case FORWARD:
                    dispatcher = _config.getServletContext().getRequestDispatcher(newUri);
                    if (dispatcher == null) {
                        Logger.error("l10n: cannot find a request dispatcher for path '"+newUri+"'");
                        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    } else {
                        Logger.trace("l10n: forwarding request to "+newUri);
                        request.setAttribute("velosurf.i18n.l10n-forwarded",Boolean.valueOf(shouldAct));
                        dispatcher.forward(request,response);
                    }
                    break;
            }
        } else {
            Logger.trace("l10n: letting request pass towards "+request.getRequestURI());
            chain.doFilter(request,response);
        }
    }

    private void findSupportedLocales(FilterConfig config) {
        /* look in the filter init-params */
        String param = config.getInitParameter("supported-locales");
        if (param == null) {
            /* look in the webapp context-params */
            param = config.getServletContext().getInitParameter("supported-locales");
        }

        if (param == null) {
            /* try to determine it */
            int i;
            if (_rewriteUri != null && (i=_rewriteUri.indexOf("@")) != -1) {
                _supportedLocales = guessSupportedLocales(_config.getServletContext(),_rewriteUri.substring(0,i));
                if(Logger.getLogLevel() <= Logger.TRACE_ID) {
                    Logger.trace("l10n: supported locales = " + StringLists.join(Arrays.asList(_supportedLocales),","));
                }
                if (_supportedLocales != null && _supportedLocales.size() > 0) {
                    return;
                }
            }
        } else {
            _supportedLocales = new ArrayList<Locale>();
            String[] list = param.split(",");
            for(String code:list) {
                _supportedLocales.add(new Locale(code));
            }
        }
        if(_supportedLocales != null && _supportedLocales.size() > 0) {
            /* let other objects see it?
            _config.getServletContext().setAttribute("velosurf.i18n.supported-locales",_supportedLocales);
             */
        } else {
            Logger.error("l10n: Cannot find any supported locale! Please add a 'supported-locales' context-param.");
        }
    }

    private String getInitParameter(String key) {
        return _config.getInitParameter(key);
    }

    private String getInitParameter(String key,String defaultValue) {
        String param = _config.getInitParameter(key);
        return param == null ? defaultValue : param;
    }

    public void destroy() {
    }

    private List<Locale> guessSupportedLocales(ServletContext ctx,String path) {
        List<Locale> locales = new ArrayList<Locale>();
        String languages[] = Locale.getISOLanguages();
        String countries[] = Locale.getISOCountries();
        Arrays.sort(languages);
        Arrays.sort(countries);
        String language,country;
        for(String resource:(Set<String>)ctx.getResourcePaths(path)) {
            /* first, it must be a path */
            if (resource.endsWith("/")) {
                int len = resource.length();
                int i = resource.lastIndexOf('/',len-2);
                String locale = resource.substring(i+1,len-1);
                if((i=locale.indexOf('_'))!=-1) {
                    language = locale.substring(0,i);
                    country = locale.substring(i+1);
                } else {
                    language = locale;
                    country = null;
                }
                /* then it must contains valid language and country codes */
                if (Arrays.binarySearch(languages,language) >= 0
                        && (country == null || Arrays.binarySearch(countries,country) >= 0 )) {
                    /* looks ok... */
                    locales.add(country == null ? new Locale(language) : new Locale(language,country));
                }
            }
        }
        _supportedLocales = locales;
        return locales;
    }

    private List<Locale> getRequestedLocales(HttpServletRequest request) {
        List<Locale> list = (List<Locale>)Collections.list(request.getLocales());
        if(/*list.size() == 0 && */_defaultLocale != null) {
            list.add(_defaultLocale);
        }
        return list;
    }

    private Locale getMatchedLocale(String candidate) {
        if(candidate == null) return null;
        if (_supportedLocales == null) {
            Logger.error("l10n: the list of supported locales is empty!");
            return null;
        }
        for(Locale locale:_supportedLocales) {
            if (candidate.startsWith(locale.toString())) {
                return locale;
            }
        }
        for(Locale locale:_supportedLocales) {
            if (locale.toString().startsWith(candidate)) {
                return locale;
            }
        }
        return null;
    }

    private Locale getPreferredLocale(List<Locale> requestedLocales) {
        for(Locale locale:requestedLocales) {
            if(_supportedLocales.contains(locale)) {
                return locale;
            }
        }
        /* still there? Ok, second pass without the country. */
        for(Locale locale:requestedLocales) {
            if (locale.getCountry() != null) {
                locale = new Locale(locale.getLanguage());
                if(_supportedLocales.contains(locale)) {
                    return locale;
                }
            }
        }
        Logger.warn("l10n: did not find a matching locale for "+StringLists.join(requestedLocales,","));
        /* then return the default locale, even if it doesn't match...
        if(_defaultLocale != null) {
            return _defaultLocale;
        }*/
        /* Oh, well... */
        return null;
    }
}
