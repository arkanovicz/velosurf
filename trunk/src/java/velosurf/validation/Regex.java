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

import java.util.regex.Pattern;
import java.util.Locale;

/**
 * <p>A regular expression pattern constraint. Syntax is:</p>
 *  <pre>
 *    &lt;<i>column</i> regex="<i>regex-pattern</i>"/&gt;
 *  </pre>
 *<p>Or:</p>
 *   <pre>
 *     &lt;<i>column</i>&gt;
 *       &lt;regex pattern="<i>regex-pattern</i>" [message="<i>error-message</i>"] &gt;
 *     &lt;/<i>column</i>&gt;
 *   </pre>
 * <p>Note: this constraint is not meant to replace an internal SQL constraint clause in the database,
 * since it cannot be made sure that complex updates will respect this constraint.</p>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class Regex extends FieldConstraint {

    protected Pattern _pattern = null;

    /**
     * Constructor.
     * @param pattern the regex pattern to be matched
     */
    public Regex(Pattern pattern) {
        _pattern = pattern;
        setMessage("field {0}: value '{1}' is not valid");
    }

    /**
     *
     * @param data the data to be validated
     * @return true if data matches the regex pattern
     */
    public boolean validate(Object data) {
        return data == null ||  data.toString().length() == 0 || _pattern.matcher(data.toString()).matches();
    }
}
