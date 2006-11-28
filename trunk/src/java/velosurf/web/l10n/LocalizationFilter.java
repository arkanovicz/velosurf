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

package velosurf.web.l10n;

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
 * <li><code>supported-locales</code>: comma separated list of supported locales ; if not provided, there is an attempt to programatically determine it<sup>(1)</sup>.
 * No default value provided.</li>
 * <li><code>default-locale</code>: the locale to be used by default (after four checks: the incoming URI, the session, cookies, and the request headers).
 * No default value provided.</li>
 * <li><code>localization-method</code>: <code>forward</code> or <code>redirect</code>, default is <code>redirect</code>.
 * <li><code>match-host</code> & <code>rewrite-host</code>: not yet implemented.<sup>(2)</sup></li>
 * <li><code>match-uri</code> & <code>rewrite-uri</code>: the regular expression against which an unlocalized uri is matched, and the replacement uri, where
 * <code>@</code> represents the locale and $1, $2, ... the matched sub-patterns. Defaults are <code>^/(.*)$</code>
 * for <code>match-uri</code> and <code>/@/$1</code> for rewrite-uri.(2)</li>
 * <li><code>match-query-string</code> & <code>rewrite-query-string</code>: not yet implemented.(2)</li>
 * <li><code>match-url</code> & <code>rewrite-url</code>: not yet implemented.(2)</li>
 * </ul>
 * </p>
 *
 * <p><b><small>(1)</small></b> for now, to find supported locales if this parameter is not provided,
 * the filter try to use the <code>rewrite-uri</code> param and to check for the existence of corresponding directories (only if the rewriting
 * string contains a pattern like '/@/', that is if you use directories to store localized sites).
 * <p><b><small>(2)</small></b> The different <code>match-</code> and <code>rewrite-</code> parameters pairs are mutually exclusive.
 * All matches are case-insensitive.</p>
 *
 * <p>When the <code>redirect</code> method is used, these supplementary parameters (mutually exclusive) allow the filter to
 * know whether or not an incoming URI is localized.
 * <ul>
 * <li><code>inspect-host</code>: not yet implemented.</li>
 * <li><code>inspect-uri</code>: default is <code>^/(.+)(?:/|$)</code>.
 * <li><code>inspect-query-string</code>: not yet implemented.</li>
 * <li><code>inspect-url</code>: not yet implemented.</li>
 * </ul>
 * </p>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */

public class LocalizationFilter implements Filter {

    /** filter config. */
    private FilterConfig config = null;

    /** supported locales. */
    private List<Locale> supportedLocales = null;
    /** default locale. */
    private Locale defaultLocale = null;

    /** seconds in year (for setting cookies age). */
    private static int SECONDS_IN_YEAR = 31536000;

    /** default match uri. */
    private static String defaultMatchUri = "^/(.*)$";
    /** default rewrite uri. */
    private static String defaultRewriteUri = "/@/$1";
    /** default inspect uri. */
    private static String defaultInspectUri = "^/(.+)(?:/|$)";
    /** match uri. */
    private  Pattern matchUri = null;
    /** rewrite uri */
    private String rewriteUri = null;
    /** inspect uri. */
    private Pattern inspectUri = null;

    /** forward method constant. */
    private static final int FORWARD = 1;
    /** redirect method constant. */
    private static final int REDIRECT = 2;

    /** localization method. */
    private int l10nMethod = REDIRECT;

    /** initialization.
     *
     * @param config filter config
     * @throws ServletException
     */
    public synchronized void init(FilterConfig config) throws ServletException {
        this.config = config;

        /* logger initialization */
        if (!Logger.isInitialized()) {
            Logger.setWriter(new ServletLogWriter(config.getServletContext()));
        }

        String param;

        /* uri */
        matchUri = Pattern.compile(getInitParameter("match-uri",defaultMatchUri),Pattern.CASE_INSENSITIVE);
        rewriteUri = getInitParameter("rewrite-uri",defaultRewriteUri);
        inspectUri = Pattern.compile(getInitParameter("inspect-uri",defaultInspectUri),Pattern.CASE_INSENSITIVE);

        /* method */
        param = getInitParameter("localization-method","redirect");
        if (param.equalsIgnoreCase("redirect")) {
            l10nMethod = REDIRECT;
        } else if (param.equalsIgnoreCase("forward")) {
            l10nMethod = FORWARD;
        } else {
            Logger.error("LocalizationFilter: '"+param+"' is not a valid l10n method; should be 'forward' or 'redirect'.");
        }

        /* supported locales */
        findSupportedLocales(this.config);

        /* default locale */
        defaultLocale = getMatchedLocale(getInitParameter("default-locale"));
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

  //      Logger.trace("l10n: URI ="+request.getRequestURI());

        /* Guess #1 - if using redirect method, deduce from URI (and, while looking at URI, fills the shouldAct vairable) */
        if (l10nMethod == REDIRECT) {
            Matcher matcher = inspectUri.matcher(request.getRequestURI());
            if (matcher.find()) {
                String candidate = matcher.group(1);
                locale = getMatchedLocale(candidate);
                if (locale != null) {
                    shouldAct = false;
                }
            }
//            Logger.trace("l10n: URI locale = "+locale);
        } else {
            /* for the forward method, shouldAct rule is: always only when not already forwarded */
            Boolean forwarded = (Boolean)request.getAttribute("velosurf.l10n.l10n-forwarded");
            if(forwarded != null && forwarded.booleanValue()) {
                shouldAct = false;
            }
        }

        if (locale == null) {
            /* Guess #2 - is there an attribute in the session? */
            locale = (Locale)session.getAttribute("velosurf.l10n.active-locale");
//            Logger.trace("l10n: session locale = "+locale);

            if (locale == null) {
                /* Guess #3 - is there a cookie?*/
                Cookie cookies[] = request.getCookies();
                if (cookies != null) {
                    for(Cookie cookie:cookies) {
                        if ("velosurf.l10n.active-locale".equals(cookie.getName())) {
                            locale = getMatchedLocale(cookie.getValue());
                        }
                    }
                }
//                Logger.trace("l10n: cookies locale = "+locale);

                if(locale == null) {
                    /* Guess #4 - use the Accepted-Language HTTP header */
                    List<Locale> requestedLocales = getRequestedLocales(request);
                    locale = getPreferredLocale(requestedLocales);
                    Logger.trace("l10n: Accepted-Language header best matching locale = "+locale);
                }
            }
        }

        if (locale == null && defaultLocale != null) {
            locale = defaultLocale;
        }
/* not needed - the tool should find the active locale in the session
        if (locale != null) {
            Localizer tool = ToolFinder.findSessionTool(session,Localizer.class);
            if (tool != null) {
                tool.setLocale(locale);
            } else {
                Logger.warn("l10n: cannot find any Localizer tool!");
            }
        }
*/
        /* sets the session atribute and the cookies */
  //      Logger.trace("l10n: setting session current locale to "+locale);
        session.setAttribute("velosurf.l10n.active-locale",locale);
        Cookie localeCookie = new Cookie("velosurf.l10n.active-locale",locale.toString());
        localeCookie.setPath("/");
        localeCookie.setMaxAge(SECONDS_IN_YEAR);
        response.addCookie(localeCookie);

        Matcher match = matchUri.matcher(request.getRequestURI());
        shouldAct &= match.find();

        if (shouldAct) {
            //  && (i = rewriteUri.indexOf("@")) != -1) ?

            String rewriteUri = this.rewriteUri.replaceFirst("@",locale.toString());
            String newUri = match.replaceFirst(rewriteUri);
            RequestDispatcher dispatcher;

            String query = request.getQueryString();
            if (query == null) {
                query = "";
            } else {
                query = "?" + query;
            }

            switch(l10nMethod) {
                case REDIRECT:
                    Logger.trace("l10n: redirecting request to "+newUri+query);
                    response.sendRedirect(newUri+query);
                    break;
                case FORWARD:
                    dispatcher = config.getServletContext().getRequestDispatcher(newUri+query);
                    if (dispatcher == null) {
                        Logger.error("l10n: cannot find a request dispatcher for path '"+newUri+"'");
                        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    } else {
                        Logger.trace("l10n: forwarding request to "+newUri+query);
                        request.setAttribute("velosurf.l10n.l10n-forwarded",Boolean.valueOf(shouldAct));
                        dispatcher.forward(request,response);
                    }
                    break;
            }
        } else {
//            Logger.trace("l10n: letting request pass towards "+request.getRequestURI());
            chain.doFilter(request,response);
        }
    }

    /** Find supported locales.
     *
     * @param config filter config
     */
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
            if (rewriteUri != null && (i=rewriteUri.indexOf("@")) != -1) {
                supportedLocales = guessSupportedLocales(this.config.getServletContext(),rewriteUri.substring(0,i));
                if(Logger.getLogLevel() <= Logger.TRACE_ID) {
                    Logger.trace("l10n: supported locales = " + StringLists.join(Arrays.asList(supportedLocales),","));
                }
                if (supportedLocales != null && supportedLocales.size() > 0) {
                    return;
                }
            }
        } else {
            supportedLocales = new ArrayList<Locale>();
            String[] list = param.split(",");
            for(String code:list) {
                supportedLocales.add(new Locale(code));
            }
        }
        if(supportedLocales != null && supportedLocales.size() > 0) {
            /* let other objects see it?
            config.getServletContext().setAttribute("velosurf.l10n.supported-locales",supportedLocales);
             */
        } else {
            Logger.error("l10n: Cannot find any supported locale! Please add a 'supported-locales' context-param.");
        }
    }

    /** Helper function.
     *
     * @param key
     * @return init-parameter
     */
    private String getInitParameter(String key) {
        return config.getInitParameter(key);
    }

    /** Helper function.
     *
     * @param key
     * @param defaultValue
     * @return init-parameter
     */
    private String getInitParameter(String key,String defaultValue) {
        String param = config.getInitParameter(key);
        return param == null ? defaultValue : param;
    }

    /** Destroy the filter.
     *
     */
    public void destroy() {
    }

    /**
     * Guess supported locales.
     * @param ctx servlet context
     * @param path path
     * @return list of locales
     */
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
        supportedLocales = locales;
        return locales;
    }

    /** get the list of requested locales.
     *
     * @param request request
     * @return list of locales
     */
    private List<Locale> getRequestedLocales(HttpServletRequest request) {
        List<Locale> list = (List<Locale>)Collections.list(request.getLocales());
        if(/*list.size() == 0 && */defaultLocale != null) {
            list.add(defaultLocale);
        }
        return list;
    }

    /**
     * get matched locale.
     * @param candidate candidate
     * @return locale
     */
    private Locale getMatchedLocale(String candidate) {
        if(candidate == null) return null;
        if (supportedLocales == null) {
            Logger.error("l10n: the list of supported locales is empty!");
            return null;
        }
        for(Locale locale:supportedLocales) {
            if (candidate.startsWith(locale.toString())) {
                return locale;
            }
        }
        for(Locale locale:supportedLocales) {
            if (locale.toString().startsWith(candidate)) {
                return locale;
            }
        }
        return null;
    }

    /**
     * Get preferred locale.
     * @param requestedLocales requested locales
     * @return preferred locale
     */
    private Locale getPreferredLocale(List<Locale> requestedLocales) {
        for(Locale locale:requestedLocales) {
            if(supportedLocales.contains(locale)) {
                return locale;
            }
        }
        /* still there? Ok, second pass without the country. */
        for(Locale locale:requestedLocales) {
            if (locale.getCountry() != null) {
                locale = new Locale(locale.getLanguage());
                if(supportedLocales.contains(locale)) {
                    return locale;
                }
            }
        }
        Logger.warn("l10n: did not find a matching locale for "+StringLists.join(requestedLocales,","));
        /* then return the default locale, even if it doesn't match...
        if(defaultLocale != null) {
            return defaultLocale;
        }*/
        /* Oh, well... */
        return null;
    }
}
