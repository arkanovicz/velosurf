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

package velosurf.model.validation;

import java.util.regex.Pattern;
import java.util.List;
import java.util.Locale;

/**
 * <p>An enumeration constraint. Syntax is:</p>
 *  <pre>
 *    &lt;<i>column</i> one-of="<i>value1,value2,value3...</i>"/&gt;
 *  </pre>
 *<p>Or:</p>
 *   <pre>
 *     &lt;<i>column</i>&gt;
 *       &lt;one-of [message="<i>error-message</i>"]&gt;
 *         &lt;value&gt;<i>value1</i>&lt;/value&gt;
 *         &lt;value&gt;<i>value2</i>&lt;/value&gt;
 *         &lt;value&gt;<i>value3</i>&lt;/value&gt;
 *         ...
 *       &lt;/one-of&gt;
 *     &lt;/<i>column</i>&gt;
 *   </pre>
 * <p>Note: this constraint is not meant to replace an internal SQL enumeration constraint clause in the database,
 * since it cannot be made sure that complex updates will respect this constraint.</p>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class OneOf extends FieldConstraint {

    protected List _values = null;

    /**
     * Constructor.
     * @param values the list of possible values
     */
    public OneOf(List values) {
        _values = values;
        setMessage("is not valid");
    }

    /**
     *
     * @param data the data to be validated
     * @return true if data is among the expected values
     */
    public boolean validate(Object data) {
        return data == null || _values.contains(data.toString());
    }
}