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

import velosurf.web.i18n.Localizer;
import velosurf.web.i18n.Localizer;

/**
 * Used to store some values
 */
public class UserContext {
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

    public String localize(String str) {
        if (localizer == null) {
            return str;
        }
        return localizer.get(str);
    }

    public void addValidationError(String err) {
        validationErrors.add(err);
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    /** generic getter
     *
     */
    public Object get(String key) {
        if ("error".equalsIgnoreCase(key)) {
            return getError();
        } else if ("validationErrors".equalsIgnoreCase(key)) {
            return getValidationErrors();
        }
        return null;
    }

    private String error = "";
    private List<String> validationErrors = new ArrayList<String>();
    private Localizer localizer = null;
}
