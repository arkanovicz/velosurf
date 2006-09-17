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

package velosurf.local;

import org.apache.velocity.tools.view.context.ViewContext;
import org.apache.velocity.tools.view.tools.ViewTool;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

import velosurf.util.Logger;

/** <p>This class is able to parse the "Accepted-Language" HTTP header to detect
 *  the appropriate locale to be used. The "Accepted-Language" header contains
 *  a list of at least one locale (a language code plus an optional country code)
 * with an optional <i>quality</i> coefficient </p>
 *
 * <p>You can find on the web the list of
 * <a href="http://www.loc.gov/standards/iso639-2/englangn.html">ISO Language Codes</a>
 * and the list of
 * <a href="http://www.iso.ch/iso/en/prods-services/iso3166ma/02iso-3166-code-lists/list-en1.html">ISO Country Codes</a>.
 *
 * <p>This tool accepts a "default-language" configuration parameter in toolbox.xml.</p>
 * <p>It is meant for the session scope.</p>
 *
 *  <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 **/


public abstract class HTTPLocalizerTool implements Localizer,ViewTool {

    protected class PreferredLanguageComparator implements Comparator<String> {
        public int compare(String l1,String l2) {
            float q1,q2;
            int sep = l1.indexOf(';');
            q1 = sep==-1 ? (float)1.0 : Float.parseFloat(l1.substring(sep+3));
            sep = l2.indexOf(';');
            q2 = sep==-1 ? (float)1.0 : Float.parseFloat(l2.substring(sep+3));
            return q1<q2 ? -1 : (q1>q2 ? 1 : 0);
        }
    }

    public void configure(Map parameters) {
        String def = (String)parameters.get("default-language");
        if (def != null) {
            _defaultLocale = def;
        }
        Logger.info("Localizer: using default locale: "+_defaultLocale);
    }

    public void init(Object initData) {
        if (initData instanceof ViewContext) {
            HttpServletRequest request = ((ViewContext)initData).getRequest();
			String languageHeader = request.getHeader("Accept-Language");
            Logger.debug("localizer: Accept-Language = "+languageHeader);
            if (languageHeader != null) {
                parseLanguageHeader(_defaultLocale);
            } else {
                parseLanguageHeader(languageHeader);
            }
        } else {
			Logger.error("Localizer tool should be used in a session scope!");
        }
    }

    protected void parseLanguageHeader(String header) {
        _localeList = new ArrayList<Locale>();
        String[] entries = header.split(",");
        Arrays.sort(entries,new PreferredLanguageComparator());
        for(String entry:entries) {
            int sep = entry.indexOf(';');
            double quality = 1.0;
            if (sep != -1) {
                entry = entry.substring(0,sep);
                quality = Float.parseFloat(entry.substring(sep+3));
            }
            if (quality > 0.0) {
                Locale locale;
                if ((sep=entry.indexOf('-'))==-1) {
                    locale = new Locale(entry);
                } else {
                    locale = new Locale(entry.substring(0,sep),entry.substring(sep+1));
                }
                _localeList.add(locale);
            }
        }
        // Is it faster to always add the default language or to check
        // if it is already present in the array ?
        // The former will be faster for valid pages and longer for invalid ones... so maybe better
        _localeList.add(new Locale(_defaultLocale));
    }

    public List<Locale> getLocaleList() {
        return _localeList ;
    }

    public Locale getLocale() {
        return _localeList.get(0);
    }

    public abstract String get(Object id);

    protected List<Locale> _localeList = null;
    protected String _defaultLocale = "en";
}
