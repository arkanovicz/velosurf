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
 * instead of one.</p>
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

    protected int _minLen = 0;
    protected int _maxLen = Integer.MAX_VALUE;

    /**
     *  Constructor.
     * @param minLen the minimum length allowed (inclusive)
     * @param maxLen the maximum length allowed (inclusive)
     */
    public Length(int minLen,int maxLen) {
        _minLen = minLen;
        _maxLen = maxLen;
        setMessage("field {0}: value '{1}' is not of the proper length");
    }

    /**
     * Constructor.
     * @param minLen the minimum length allowed (inclusive)
     * @param maxLen the maximum length allowed (inclusive)
     * @param msg a string like "is not of the proper length"
     */
    public Length(int minLen,int maxLen,String msg) {
        _minLen = minLen;
        _maxLen = maxLen;
        _message = msg;
    }

    public void setMinLength(int minLen) {
        _minLen = minLen;
    }

    public void setMaxLength(int maxLen) {
        _maxLen = maxLen;
    }

    /**
     *
     * @param data the data to be validated
     * @return true if data is a string of the expected length
     */
    public boolean validate(Object data) {
        if (data == null) {
            return true;
        }
        int len = data.toString().length();
        return len >= _minLen && len <= _maxLen;
    }

    public String toString() {
        return "length "+(_minLen>0 && _maxLen<Integer.MAX_VALUE?"between "+_minLen+" and "+_maxLen:_minLen>0?">= "+_minLen:"<="+_maxLen);
    }
}
