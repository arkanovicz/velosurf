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
import velosurf.model.Entity;

/**
 * Used to store contextual values relatives to the user (in a web context, there is one UserContext per http session).
 *
 * @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class UserContext {

    /** key used to store the user context in the http session.
     */
    public static final String USER_CONTEXT_KEY = "velosurf.util.UserContext:session-key";
    /**
     * Constructor.
     */
    public UserContext() {

    }
    /**
     * Last error setter.
     * @param err error
     */
    public void setError(String err) {
        error = err;
    }
    /**
     * Last error getter.
     * @return last error message.
     */
    public String getError() {
        return error;
    }
    /** Localizer setter.
     *
     * @param loc localizer
     */
    public void setLocalizer(Localizer loc) {
        localizer = loc;
    }
    /** Locale setter.
     *
     * @param loc Locale
     */
    public void setLocale(Locale loc) {
        locale = loc;
    }
    /**
     * Localize a parameterized message.
     * @param str message to localize
     * @param params parameters that are meant to replace "{0}", "{1}", ... in the message
     * @return localized message
     */
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
    /**
     * Clear validation errors.
     */
    public void clearValidationErrors() {
        validationErrors.clear();
    }
    /**
     * Add a validation error.
     * @param err validation error
     */
    public void addValidationError(String err) {
        validationErrors.add(err);
    }
    /** Get all validation error messages.
     *
     * @return validation error messages
     */
    public List<String> getValidationErrors() {
        /* returning null allows a test like "#if($db.validationErrors)" */
        return validationErrors.size()>0 ? validationErrors : null;
    }

    /** generic getter.
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
    /**
     * Locale getter.
     * @return current locale
     */
    public Locale getLocale() {
        if(localizer != null) {
            return localizer.getLocale();
        } else if (locale != null) {
            return locale;
        } else {
            return Locale.getDefault();
        }
    }
    /**
     * Set the last inserted ID for an entity.
     * @param entity entity
     * @param id last inserted id
     */
    public void setLastInsertedID(Entity entity,long id) {
        lastInsertedIDs.put(entity,id);
    }
    /**
     * Get the last inserted ID for an entity.
     * @param entity entity
     * @return last inserted ID of -1
     */
    public long getLastInsertedID(Entity entity) {
        Long id = lastInsertedIDs.get(entity);
        if(id != null) {
            return id.longValue();
        } else {
            Logger.error("getLastInsertID called for entity '"+entity+"' which doesn't have any");
            return -1;
        }
    }
    /** last error message */
    private String error = "";
    /** list of validation error messages */
    private List<String> validationErrors = new ArrayList<String>();
    /** localizer */
    private Localizer localizer = null;
     /** locale */
    private Locale locale = null;
    /** map of last inserted IDs */
    private Map<Entity,Long> lastInsertedIDs = new HashMap<Entity,Long>();
}
