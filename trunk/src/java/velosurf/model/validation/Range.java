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

import java.sql.SQLException;

/**
 * <p>A range constraint. Syntax is:</p>
 *  <pre>
 *    &lt;<i>column</i> min="<i>min</i>" max="<i>max</i>"/&gt;
 *  </pre>
 * <p>where you donnot need to specify both min and max.</p>
 * <p>Or:</p>
 * <pre>
 *     &lt;min value="<i>min-value</i>" [message="<i>error-message</i>"]&gt;
 *     &lt;max value="<i>max-value</i>" [message="<i>error-message</i>"]&gt;
 * </pre>
 * <p>Note: this constraint is not meant to replace an internal SQL constraint in the database,
 * since it cannot be made sure that complex updates will respect this constraint.</p>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class Range extends FieldConstraint {

    protected Number _min = null;
    protected Number _max = null;

    /**
     *  Constructor.
     * @param min the minimum allowed (inclusive for natural numbers)
     * @param max the maximum allowed (inclusive for natural numbers)
     */
    public Range(Number min,Number max) {
        _min = min;
        _max = max;
        setMessage("is not in the valid range");
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
     * @return true if data is in the expected range
     */
    public boolean validate(Object data) throws SQLException {
        if (data == null) {
            return true;
        }
        if (! (data instanceof Number)) {
            throw new SQLException ("Range constraint: expecting a Number, got a "+data.getClass().getName());
        }
        if (_min != null && _min.doubleValue() > ((Number)data).doubleValue()) {
            return false;
        }
        if (_max != null && _max.doubleValue() < ((Number)data).doubleValue()) {
            return false;
        }
        return true;
    }
}
