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

package velosurf.validation;

import java.util.Locale;

/**
 * <p>A "not empty" constraint. Syntax is:</p>
 *  <pre>
 *    &lt;<i>column</i> not-empty="true"/&gt;
 *  </pre>
 *<p>Or:</p>
 *   &lt;<i>column</i>&gt;
 *     &lt;not-empty [message="<i>error-message</i>"]/&gt;
 *   &lt;/<i>column</i>&gt;
 * <p>Note: this constraint is not meant to replace an internal SQL clause in the database,
 * since it cannot be made sure that complex updates will respect this constraint.</p>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class NotEmpty extends FieldConstraint {

    /**
     * Default constructor.
     */
    public NotEmpty() {
        setMessage("field {0} cannot be empty");
    }

    /**
     *
     * @param data the data to be validated
     * @return true if data is not null
     */
    public boolean validate(Object data) {
        return data != null && data.toString().length() > 0;
    }

    public String toString() {
        return "not-empty";
    }
}
