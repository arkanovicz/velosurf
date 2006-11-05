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

package velosurf.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

import velosurf.web.l10n.Localizer;
import velosurf.web.l10n.Localizer;
import velosurf.model.Entity;

/**
 * Used to store contextual values relatives to the user (in a web context, there is one UserContext per http session)
 *
 * @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class UserContext {

    /** key used to store the user context in the http session
     */
    public static final String USER_CONTEXT_KEY = "velosurf.util.UserContext:session-key";

    public UserContext() {

    }

    public void setError(String err) {
        error = err;
    }

    public String getError() {
        return error;
    }

    public void setLocalizer(Localizer loc) {
        localizer = loc;
    }

    public void setLocale(Locale loc) {
        locale = loc;
    }

    public String localize(String str,Object ... params) {
        if (localizer == null) {
            if(params.length > 0) {
                Logger.warn("user context localization: localizer not found, ignoring arguments "+StringLists.join(Arrays.asList(params),",")+" for message "+str);
            }
            return str;
        }
        switch(params.length) {
            case 0: return localizer.get(str);
            case 1: return localizer.get(str,params[0]);
            case 2: return localizer.get(str,params[0],params[1]);
            case 3: return localizer.get(str,params[0],params[1],params[2]);
            default:
                Logger.error("user context localization: too many parameters for message "+str);
                return localizer.get(str,params[0],params[1],params[2]);
        }

    }

    public void clearValidationErrors() {
        validationErrors.clear();
    }

    public void addValidationError(String err) {
        validationErrors.add(err);
    }

    public List<String> getValidationErrors() {
        /* returning null allows a test like "#if($db.validationErrors)" */
        return validationErrors.size()>0 ? validationErrors : null;
    }

    /** generic getter
     *
     */
    public Object get(String key) {
        if ("error".equalsIgnoreCase(key)) {
            return getError();
        } else if ("validationErrors".equalsIgnoreCase(key)) {
            return getValidationErrors();
        } else if ("locale".equalsIgnoreCase(key)) {
            return getLocale();
        }
        return null;
    }

    public Locale getLocale() {
        if(localizer != null) {
            return localizer.getLocale();
        } else if (locale != null) {
            return locale;
        } else {
            return Locale.getDefault();
        }
    }

    public void setLastInsertedID(Entity entity,long id) {
        lastInsertedIDs.put(entity,id);
    }

    public long getLastInsertedID(Entity entity) {
        Long id = lastInsertedIDs.get(entity);
        if(id != null) {
            return id.longValue();
        } else {
            Logger.error("getLastInsertID called for entity '"+entity+"' which doesn't have any");
            return -1;
        }
    }

    private String error = "";
    private List<String> validationErrors = new ArrayList<String>();
    private Localizer localizer = null;
    private Locale locale = null;
    private Map<Entity,Long> lastInsertedIDs = new HashMap<Entity,Long>();
}
