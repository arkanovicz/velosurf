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

import org.apache.velocity.tools.view.context.ViewContext;
import org.apache.velocity.tools.view.tools.ViewTool;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletContext;
import java.util.*;

import velosurf.util.Logger;

/** <p>This class rely on the "Accepted-Language" HTTP header to detect
 *  the appropriate locale to be used.</p>
 *
 * <p>You can find on the web the list of
 * <a href="http://www.loc.gov/standards/iso639-2/englangn.html">ISO Language Codes</a>
 * and the list of
 * <a href="http://www.iso.ch/iso/en/prods-services/iso3166ma/02iso-3166-code-lists/list-en1.html">ISO Country Codes</a>.
 *
 * <p>This tool accepts a "default-locale" configuration parameter in toolbox.xml.</p>
 * <p>It is meant for the session scope.</p>
 *
 *  <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 **/


public abstract class HTTPLocalizerTool implements Localizer,ViewTool {

    public void configure(Map parameters) {
        String def = (String)parameters.get("default-locale");
        if (def != null) {
            synchronized(_defaultLocale) {
                _defaultLocale = def; // TODO : this static parameter is initialized at each session creation !!!
            }
        }
        Logger.info("Localizer: using default locale: "+_defaultLocale);
    }

    public void init(Object initData) {
        HttpServletRequest request = null;
        if (initData instanceof ViewContext) {
            request = ((ViewContext)initData).getRequest();
            // for now... TODO
            _locale = (Locale)request.getSession(false).getAttribute("active-locale");
        }
        else {
            Logger.error("Localizer tool should be used in a session scope!");
            return;
        }
        _localeList = getRequestedLocales(request);
    }

    public static Locale parseLocale(String candidate) {
        if (_supportedLocalesCache == null) {
            Logger.error("HTTPLocalizerTool.parseLocale(): I don't know supported locales!");
            return null;
        }
        for(Locale locale:_supportedLocalesCache) {
            if (locale.toString().equals(candidate)) {
                return locale;
            }
        }
        return null;
    }

    public static List<Locale> getRequestedLocales(HttpServletRequest request) {
        List<Locale> list = new ArrayList<Locale>();
        Enumeration locales = request.getLocales();
        while(locales.hasMoreElements()) {
            list.add((Locale)locales.nextElement());
        }
        /* always add the default locale afterwards */
        list.add(new Locale(_defaultLocale));
        return list;
    }

    public static synchronized List<Locale> getSupportedLocales(ServletContext ctx,String path) {
        if (_supportedLocalesCache != null) return _supportedLocalesCache;
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
        _supportedLocalesCache = locales;
        return locales;
    }

    public static Locale getBestMatchingLocale(List<Locale> requestedLocales, List<Locale> supportedLocales) {
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
        /* Oh, well... */
        return new Locale(_defaultLocale);
    }

    public static Locale getDefaultLocale() {
        return new Locale(_defaultLocale);
    }

    /**
     * Meant to be overloaded.
     * @return the list of supported locales
     */
    public List<Locale> getSupportedLocales() {
        List<Locale> list = new ArrayList<Locale>();
        list.add(Locale.ENGLISH);
        return list;
    }

    public Locale getLocale() {
        return _locale;
    }

    public abstract String get(Object id);

    protected List<Locale> _localeList = null;

    protected Locale _locale = null;

    protected static String _defaultLocale = "en";
    protected static List<Locale> _supportedLocalesCache = null;
}
