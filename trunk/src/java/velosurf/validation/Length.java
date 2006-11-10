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

import java.sql.SQLException;
import java.util.Locale;

/**
 * <p>A string length constraint. Syntax is:</p>
 *  <pre>
 *    &lt;<i>column</i> min-len="<i>min</i>" max-len="<i>max</i>"/&gt;
 *  </pre>
 * <p>where you donnot need to specify both min-len and max-len.</p>
 * <p></p> 
 * <p>Or:</p>
 * <pre>
 *   &lt;<i>column</i>&gt;
 *     &lt;min-len value="<i>min-value</i>" [message="<i>error-message</i>"]&gt;
 *     &lt;max-len value="<i>max-value</i>" [message="<i>error-message</i>"]&gt;
 *   &lt;/<i>column</i>&gt;
 * </pre>
 * <p>Note: this constraint is not meant to replace an internal SQL constraint in the database,
 * since it cannot be made sure that complex updates will respect this constraint.</p>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class Length extends FieldConstraint {
    /** min lmength. */
    private int minLen = 0;
    /** max length. */
    private int maxLen = Integer.MAX_VALUE;

    /**
     *  Constructor.
     * @param minLen the minimum length allowed (inclusive)
     * @param maxLen the maximum length allowed (inclusive)
     */
    public Length(int minLen,int maxLen) {
        this.minLen = minLen;
        this.maxLen = maxLen;
        setMessage("field {0}: value '{1}' is not of the proper length");
    }

    /**
     * Constructor.
     * @param minLen the minimum length allowed (inclusive)
     * @param maxLen the maximum length allowed (inclusive)
     * @param msg a string like "is not of the proper length"
     */
    public Length(int minLen,int maxLen,String msg) {
        this.minLen = minLen;
        this.maxLen = maxLen;
        setMessage(msg);
    }

    /**
     * Min length setter.
     * @param minLen minimum length
     */
    public void setMinLength(int minLen) {
        this.minLen = minLen;
    }

    /**
     * Maximum length setter.
     * @param maxLen maximum length
     */
    public void setMaxLength(int maxLen) {
        this.maxLen = maxLen;
    }

    /**
     * Validate data against this constraint.
     * @param data data to validate
     * @return whether data is valid
     */
    public boolean validate(Object data) {
        if (data == null) {
            return true;
        }
        int len = data.toString().length();
        return len >= minLen && len <= maxLen;
    }

    /**
     * return a string representation for this constraint.
     * @return string
     */
    public String toString() {
        return "length "+(minLen>0 && maxLen<Integer.MAX_VALUE?"between "+minLen+" and "+maxLen:minLen>0?">= "+minLen:"<="+maxLen);
    }
}
