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

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import velosurf.util.StringLists;

/**
 * <p>An enumeration constraint. Syntax is:</p>
 *  <pre>
 *    &lt;<i>column</i> one-of="<i>value1,value2,value3...</i>"/&gt;
 *  </pre>
 * <p>Or:</p>
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
public class OneOf extends FieldConstraint
{
    private List values = null;

    /**
     * Constructor.
     * @param values the list of possible values
     */
    public OneOf(List values)
    {
        this.values = values;
        setMessage("field {0}: value [{1}] must be one of: " + StringLists.join(this.values, ", "));
    }

    /**
     * Validate data against this constraint.
     * @param data the data to be validated
     * @return true if data is among the expected values
     */
    public boolean validate(Object data)
    {
        return data == null || values.contains(data.toString());
    }

    /**
     * return a string representation for this constraint.
     * @return string
     */
    public String toString()
    {
        return "one of " + StringLists.join(values, ", ");
    }
}
