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

import org.apache.velocity.tools.view.context.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.*;
import java.text.MessageFormat;
import java.lang.ref.WeakReference;

import velosurf.util.Logger;

/** <p>This class rely on the "Accepted-Language" HTTP header to detect
 *  the appropriate locale to be used.</p>
 *
 * <p>This tool accepts a "default-locale" configuration parameter in toolbox.xml.</p>
 * <p>It is meant for the session scope.</p>
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 **/


public abstract class HTTPLocalizerTool implements Localizer {

    /**
     * Initialize this tool.
     * @param initData a view context
     */
    public void init(Object initData) {
        if (initData instanceof ViewContext) {
            HttpSession session = ((ViewContext)initData).getRequest().getSession();
            if (session != null) {
                this.session = new WeakReference<HttpSession>(session);
                Locale locale = (Locale)session.getAttribute("velosurf.l10n.active-locale");
                if (locale == null) {
                    /* means the localization filter did not intercept this query */
                    locale = getBestLocale(listFromEnum(((ViewContext)initData).getRequest().getLocales()));
                    Logger.trace("l10n: unlocalized page - using locale "+locale);
                }
                setLocale(locale);
            }
        }
        else {
            Logger.error("l10n: Localizer tool should be used in a session scope!");
            return;
        }
    }

    /**
     * Transform an enumeration into a list of locales.
     * @param e enumeration
     * @return a list of locales
     */
    private static List<Locale> listFromEnum(Enumeration e) {
        List<Locale> list = new ArrayList<Locale>();
        while(e.hasMoreElements()) {
            list.add((Locale)e.nextElement());
        }
        return list;
    }

    /**
     * Get the locale best matching available localized data among a list.
     * @param locales list of input locales
     * @return best matching locale
     */
    public Locale getBestLocale(List<Locale> locales) {
        for(Locale locale:locales) {
            if (hasLocale(locale)) {
                return locale;
            }
        }
        /* second pass without the country code */
        for(Locale locale:locales) {
            String country = locale.getCountry();
            if(country != null && country.length() > 0) {
                Locale l = new Locale(locale.getLanguage());
                if (hasLocale(l)) {
                    return l;
                }
            }
        }
        return null;
    }

    /**
     * Check for the presence of a locale.
     * @param locale locale to check
     * @return true if present
     */
    public abstract boolean hasLocale(Locale locale);

    /**
     * Current locale setter.
     * @param locale locale
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    /** Current lcoale getter.
     *
     * @return current locale
     */
    public Locale getLocale() {
        checkLocaleChange();
        return locale;
    }

    /**
     *  Check that the locale has not changed in the session.
     */
    public void checkLocaleChange() {
        HttpSession s = session.get();
        if (s != null) {
            Locale l = (Locale)s.getAttribute("velosurf.l10n.active-locale");
            if (l != null && !l.equals(locale)) {
                setLocale(l);
            }
        }
    }

    /**
     * Get the localized message for this key.
     * @param id message key
     * @return localized message (or id if not found).
     */
    public abstract String get(Object id);


    /**
     * Get the localized parameterized message for this key.
     * @param id message key
     * @param params message parameters
     * @return localized message (or id if not found).
     */
    public String get(Object id,Object ... params) {
        String message = get(id).replaceAll("'","''");
        return MessageFormat.format(message,params);
    }

    /** keep a reference on the session */
    private WeakReference<HttpSession> session = null;

    /** current locale */
    protected Locale locale = null;
}
