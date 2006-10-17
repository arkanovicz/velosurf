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

import java.util.Locale;
import java.util.Date;
import java.text.DateFormat;
import java.text.ParseException;

/**
 * <p>A type and range constraint on dates. Syntax is:</p>
 *
 *  <pre><code>
 *    &lt;<i>column</i> [type="date"] [after="<i>date-after</i>"] [before="<i>date-before</i>"] /&gt;
 *  </code></pre>
 *<p>The <code>type="date"</code> parameter is implied when <code>after</code> or <code>before</code> is found.<br>Or:</p>
 *   <pre>
 *     &lt;<i>column</i>&gt;
 *       &lt;date [after="<i>afer-date</i>"] [before="<i>before-date</i>"] [message="<i>error-message</i>"] /&gt;
 *     &lt;/<i>column</i>&gt;
 *   </pre>
 * <br><p>The format used to specify after and before dates is yyyyMMdd. The format used to parse the input
 * is the short local date format (which depends upon the user locale).</p>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */

public class DateRange extends FieldConstraint {

    private Date _before = null;
    private Date _after = null;

    public DateRange() {
        setMessage("is not a valid date");
    }

    public void setBeforeDate(Date before) {
        _before = before;
    }

    public void setAfterDate(Date after) {
        _after = after;
    }

    public boolean validate(Object data,Locale locale) {
        try {
            if (data == null) return true;
            DateFormat format = DateFormat.getDateInstance(DateFormat.SHORT,locale);
            Date date = format.parse((String)data);
            if(_before != null &&  date.after(_before)) {
                return false;
            }
            if(_after != null && date.before(_after)) {

            }
            return true;
        } catch(ParseException pe) {
            return false;
        }
    }
}
