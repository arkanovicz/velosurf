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
import java.util.regex.Pattern;

/**
 * <p>A type and range constraint on numbers. Syntax is:</p>
 *  <pre>
 *    &lt;<i>column</i> [type="integer"] [min="<i>min</i>"] [max="<i>max</i>"]/&gt;
 *  </pre>
 * <p>where you donnot need to specify both min and max.</p>
 * <p>Or:</p>
 * <pre>
 *     &lt;integer|number [min="<i>min-value</i>"] [max="<i>max-value</i>"] [message="<i>error-message</i>"] /&gt;
 * </pre>
 * <p>Note: this constraint is not meant to replace an internal SQL constraint in the database,
 * since it cannot be made sure that complex updates will respect this constraint.</p>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class Range extends FieldConstraint {

    private Number _min = null;
    private Number _max = null;
    private boolean _integer = false;

    private static Pattern _intPattern = Pattern.compile("(?:\\+|-)?\\d+");

    /**
     *  Constructor.
     */
    public Range() {
        setMessage("field {0}: '{1}' is not in the valid range"); /* TODO review... format message! */
    }

    public void setInteger(boolean integer) {
        _integer = integer;
    }

    public void setMin(Number min) {
        _min = min;
    }

    public void setMax(Number max) {
        _max = max;
    }

    /**
     *
     * @param data the data to be validated
     * @return true if data is in the expected range and type
     */
    public boolean validate(Object data) throws SQLException {
        if (data == null || data.toString().length() == 0) {
            return true;
        }
        Number number;
        if (data instanceof Number) {
            number = (Number)data;
        } else {
            try {
                if(_integer) {
                    number = Integer.parseInt(data.toString());
                } else {
                    number = Double.parseDouble(data.toString());
                }
            } catch(NumberFormatException nfe) {
                return false;
            }
        }
        if (_min != null && _min.doubleValue() > number.doubleValue()) {
            return false;
        }
        if (_max != null && _max.doubleValue() < number.doubleValue()) {
            return false;
        }
        return true;
    }

    public String toString() {
        return (_integer?"type integer":"type number") + (_min != null && _max != null?", between "+_min+" and "+_max:_min != null?", >= "+_min:", <="+_max);
    }
}
