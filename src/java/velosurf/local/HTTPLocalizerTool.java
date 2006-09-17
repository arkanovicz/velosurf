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
            _defaultLocale = def;
        }
        Logger.info("Localizer: using default locale: "+_defaultLocale);
    }

    public void init(Object initData) {
        if (initData instanceof ViewContext) {
            HttpServletRequest request = ((ViewContext)initData).getRequest();
            Enumeration locales = request.getLocales();
            _localeList = new ArrayList<Locale>();
            while(locales.hasMoreElements()) {
                _localeList.add((Locale)locales.nextElement());
            }
            /* always add the default locale afterwards */
            _localeList.add(new Locale(_defaultLocale));
        } else {
            Logger.error("Localizer tool should be used in a session scope!");
        }
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
