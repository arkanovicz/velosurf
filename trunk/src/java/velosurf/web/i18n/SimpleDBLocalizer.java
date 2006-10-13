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
 * Basic Localizer implementation
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

    private String localizedTable = LOCALIZED_TABLE_DEFAULT;
    private String idField = ID_FIELD_DEFAULT;
    private String localeField = LOCALE_FIELD_DEFAULT;
    private String stringField = STRING_FIELD_DEFAULT;

    private static boolean _initialized = false;

    private static Map<Locale,Map<Object,String>> _localeStrings = null;

    private Map<Object,String> _currentStrings = null;

    private Map config;

    public SimpleDBLocalizer() {}

    public void init(Object initData) {
        super.init(initData);
        if (!_initialized) {
            if(config != null) {
                String value;
                value = (String)config.get(LOCALIZED_TABLE_KEY);
                if (value != null) {
                    localizedTable = value;
                }
                value = (String)config.get(ID_FIELD_KEY);
                if (value != null) {
                    idField = value;
                }
                value = (String)config.get(LOCALE_FIELD_KEY);
                if (value != null) {
                    localeField = value;
                }
                value = (String)config.get(STRING_FIELD_KEY);
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
    }

    private static synchronized void readLocales(ServletContext ctx) {
        if (_initialized) return;
        try {
            DBReference db = VelosurfTool.getDefaultInstance(ctx);
            if (db==null) {
                throw new Exception("Cannot find database!");
            }
            EntityReference entity = (EntityReference)db.get("localized");
            if (entity==null) {
                throw new Exception("Cannot find 'localized' database entity!");
            }
            _localeStrings = new HashMap<Locale,Map<Object,String>>();
            entity.setOrder("locale");
            Iterator locales = entity.iterator(); // sorted by locale
            Map<Object,String> map = null;
            String current = null;
            Locale loc = null;
            while (locales.hasNext()) {
                Instance row = (Instance)locales.next();
                String key = (String)row.get("id");
                String locale = (String)row.get("locale");
                String string = (String)row.get("string");
                if (!locale.equals(current)) {
                    current = locale;
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

    public void setLocale(Locale locale) {
        if (locale == null && getLocale() == null || locale != null && locale.equals(getLocale())) {
            /* no change */
            return;
        }
        super.setLocale(locale);
        _currentStrings = _localeStrings.get(getLocale());
        if (_currentStrings == null) {
            Logger.error("l10n: no strings found for locale "+getLocale());
        }
    }

    public String get(Object id) {
        String message = _currentStrings.get(id);
        Logger.trace("l10n: "+id+" -> "+message);
        return message;
    }

    public void configure(Map map) {
        config = map;
    }

}
