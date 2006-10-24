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

import org.apache.velocity.tools.view.context.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
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
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 **/


public abstract class HTTPLocalizerTool implements Localizer {

    public void init(Object initData) {
        if (initData instanceof ViewContext) {
            HttpSession session = ((ViewContext)initData).getRequest().getSession();
            if (session != null) {
                Locale locale = (Locale)session.getAttribute("velosurf.i18n.active-locale");
                if (locale == null) {
                    /* means the localization filter did not intercept this query */
                    Logger.trace("l10n: unlocalized page - using locale "+locale);
                    locale = ((ViewContext)initData).getRequest().getLocale();
                }
                setLocale(locale);
            }
        }
        else {
            Logger.error("Localizer tool should be used in a session scope!");
            return;
        }
    }


    public void setLocale(Locale locale) {
        _locale = locale;
    }

    public Locale getLocale() {
        return _locale;
    }

    public abstract String get(Object id);

    protected Locale _locale = null;
}