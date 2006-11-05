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

import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

import javax.servlet.ServletContext;

import org.apache.velocity.tools.view.context.ViewContext;

import velosurf.util.Logger;
import velosurf.context.Instance;
import velosurf.context.DBReference;
import velosurf.context.EntityReference;
import velosurf.web.VelosurfTool;

/**
 * <p>A basic database based Localizer implementation.</p>
 * <p>The following <code>toolbox.xml</code> parameters are available:</p>
 * <ul>
 * <li><code>localized-table</code>: the name of the table containing localized strings (default: "<code>localized</code>").</li>
 * <li><code>id-field</code>: the name of the field containing the string id (default: "<code>id</code>").</li>
 * <li><code>locale-field</code>: the name of the field containing the ISO locale code (default: "<code>locale</code>").</li>
 * <li><code>string-field</code>: the name of the field containing the lcoalized string (default: "<code>string</code>").</li>
 * </ul>
 *
 * <p>You can find on the web the list of
 * <a href="http://www.loc.gov/standards/iso639-2/englangn.html">ISO Language Codes</a>
 * and the list of
 * <a href="http://www.iso.ch/iso/en/prods-services/iso3166ma/02iso-3166-code-lists/list-en1.html">ISO Country Codes</a>.
 *
 */

public class SimpleDBLocalizer extends HTTPLocalizerTool {

    private static final String LOCALIZED_TABLE_KEY = "localized-table";
    private static final String ID_FIELD_KEY = "id-field";
    private static final String LOCALE_FIELD_KEY = "locale-field";
    private static final String STRING_FIELD_KEY = "string-field";

    private static final String LOCALIZED_TABLE_DEFAULT = "localized";
    private static final String ID_FIELD_DEFAULT = "id";
    private static final String LOCALE_FIELD_DEFAULT = "locale";
    private static final String STRING_FIELD_DEFAULT = "string";

    private static String localizedTable = LOCALIZED_TABLE_DEFAULT;
    private static String idField = ID_FIELD_DEFAULT;
    private static String localeField = LOCALE_FIELD_DEFAULT;
    private static String stringField = STRING_FIELD_DEFAULT;

    private static boolean _initialized = false;

    private static Map<Locale,Map<Object,String>> _localeStrings = null;

    private Map<Object,String> _currentStrings = null;

    private Map _config;

    public SimpleDBLocalizer() {
    }

    public void configure(Map config) {
        _config = config;
    }

    public void init(Object initData) {
        if (!_initialized) {
            if(_config != null) {
                String value;
                value = (String)_config.get(LOCALIZED_TABLE_KEY);
                if (value != null) {
                    localizedTable = value;
                }
                value = (String)_config.get(ID_FIELD_KEY);
                if (value != null) {
                    idField = value;
                }
                value = (String)_config.get(LOCALE_FIELD_KEY);
                if (value != null) {
                    localeField = value;
                }
                value = (String)_config.get(STRING_FIELD_KEY);
                if (value != null) {
                    stringField = value;
                }
            }
            ServletContext ctx =
                initData instanceof ServletContext ?
                (ServletContext)initData   :
                ((ViewContext)initData).getServletContext();
            readLocales(ctx);
        }
        super.init(initData);
    }

    private static synchronized void readLocales(ServletContext ctx) {
        if (_initialized) return;
        try {
            DBReference db = VelosurfTool.getDefaultInstance(ctx);
            if (db==null) {
                throw new Exception("Cannot find database!");
            }
            EntityReference entity = (EntityReference)db.get(localizedTable);
            if (entity==null) {
                throw new Exception("Cannot find 'localized' database entity!");
            }
            _localeStrings = new HashMap<Locale,Map<Object,String>>();
            entity.setOrder(localeField);
            Iterator locales = entity.iterator(); // sorted by locale
            Map<Object,String> map = null;
            String current = null;
            Locale loc = null;
            while (locales.hasNext()) {
                Instance row = (Instance)locales.next();
                String key = (String)row.get(idField);
                String locale = (String)row.get(localeField);
                String string = (String)row.get(stringField);
                if (!locale.equals(current)) {
                    current = locale;
                    Logger.trace("Found new locale in db: "+locale);
                    map = new HashMap<Object,String>();
                    /* for now, take language and country into account... TODO: take variant into account */
                    int sep = locale.indexOf('_');
                    loc = ( sep == -1 ? new Locale(locale) : new Locale(locale.substring(0,sep),locale.substring(sep+1)) );
                    _localeStrings.put(loc,map);
                }
                map.put(key,string);
            }
            _initialized = true;
        } catch (Exception e) {
            Logger.log(e);
        }
    }

    public boolean hasLocale(Locale locale) {
        return _localeStrings.containsKey(locale);
    }

    public void setLocale(Locale locale) {
        if (locale == null && getLocale() == null || locale != null && locale.equals(getLocale())) {
            /* no change */
            return;
        }
        super.setLocale(locale);
        _currentStrings = _localeStrings.get(getLocale());
        if (_currentStrings == null) {
            Logger.warn("l10n: no strings found for locale "+getLocale());
        }
    }

    public String get(Object id) {
        if (_currentStrings == null) {
            Logger.warn("l10n: no current locale! (was getting string id '"+id+"')");
            return id.toString();
        }
        String message = _currentStrings.get(id);
        //Logger.trace("l10n: "+id+" -> "+message);
        return message == null ? id.toString() : message;
    }
}
