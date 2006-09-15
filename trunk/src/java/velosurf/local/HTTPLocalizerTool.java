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

/** This class is able to parse the "Accepted-Language" HTTP header to detect
 *  the appropriate locale to be used.
 * Its subclasses must implement the abstract <code>String get(Object id)</code> method.
 * It accepts a "default-language" configuration parameter in toolbox.xml.
 * It is meant for the session scope.
 *
 * Note: this class uses some java 1.5 syntax.
 *
 * @author Claude Brisson
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
        _defaultLanguage = (String)parameters.get("default-language");
        Logger.info("Localizer: using default language: "+_defaultLanguage);
    }

    public void init(Object initData) {
        if (initData instanceof ViewContext) {
            HttpServletRequest request = ((ViewContext)initData).getRequest();
			String languageHeader = request.getHeader("Accept-Language");
            Logger.debug("localizer: Accept-Language = "+languageHeader);
            if (languageHeader == null) {
                parseLanguageHeader(_defaultLanguage);
            } else {
                parseLanguageHeader(languageHeader);
            }
        } else {
			Logger.error("Localizer tool should be used in a session scope!");
        }
    }

    protected void parseLanguageHeader(String header) {
        _languageList = new ArrayList<String>();
        String[] entries = header.split(",");
        Arrays.sort(entries,new PreferredLanguageComparator());
        for(String entry:entries) {
            int sep = entry.indexOf(';');
            _languageList .add(sep==-1?entry:entry.substring(0,sep));
        }
        // Is it faster to always add the default language or to check
        // if it is already present in the array ?
        // The former will be faster for valid pages and longer for invalid ones... so maybe better
        _languageList .add(_defaultLanguage);
    }

    public List<String> getLanguageList() {
        return _languageList ;
    }

    public abstract String get(Object id);

    protected List<String> _languageList = null;
    protected String _defaultLanguage = "fr";
}
