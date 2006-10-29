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
import java.util.Date;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import velosurf.util.Logger;

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
        setMessage("field {0}: '{1}' is not a valid date or is outside range");
    }

    public void setBeforeDate(Date before) {
        _before = before;
    }

    public void setAfterDate(Date after) {
        _after = after;
    }

    public boolean validate(Object data,Locale locale) {
        SimpleDateFormat format = null;
        try {
            if (data == null || data.toString().length() == 0) return true;
            if (locale == null) {
                Logger.error("date range validation: locale is null!");
                return true;
            }
            format = (SimpleDateFormat)SimpleDateFormat.getDateInstance(DateFormat.SHORT,locale);
            String reformatted = reformat(format.toPattern(), data.toString());
            Date date = format.parse(reformatted);
            if(_after != null && date.before(_after)) {
                return false;
            }
            if(_before != null &&  date.after(_before)) {
                return false;
            }
            return true;
        } catch(ParseException pe) {
            Logger.warn("date validation: could not parse date '"+data.toString()+"' with format: "+format.toPattern());
//            Logger.log(pe);
            return false;
        }
    }

    private static final Pattern y4 = Pattern.compile("\\d{4}");

    private static String reformat(String pattern,String date) {
        /* tries to reformat the date to match pattern conventions */
        int patternLength = pattern.length();
        int dateLength = date.length();
        char patternSep,dateSep;
        boolean warn = false;
        patternSep = pattern.indexOf('/') != -1 ?
               '/' : pattern.indexOf('-') != -1 ?
               '-' : '?';
        dateSep = date.indexOf('/') != -1 ?
               '/' : date.indexOf('-') != -1 ?
               '-' : '?';
        if(patternSep == '?') {
            if (dateSep == '?') {
                warn = (patternLength != dateLength);
            } else {
                date = date.replace(""+dateSep,"");
                warn = (patternLength != date.length());
            }
        } else {
            if (dateSep == '?') {
                if(dateLength == 6) {
                    /* not ABSOLUTELY sure that six chars without - or / are one of ddMMyy, MMddyy, yyMMdd and the like... but quite. */
                    date = date.substring(0,2)+patternSep+date.substring(2,4)+patternSep+date.substring(4,6);
                    warn = (patternLength != 8);
                } else {
                    /* too complex */
                    warn = true;
                }
            } else {
                if(patternSep != dateSep) {
                    date = date.replace(dateSep,patternSep);
                }
                if(patternLength <= dateLength - 2) {
                    Matcher m = y4.matcher(date);
                    if(m.find()) {
                        date = date.substring(0,m.start()) + date.substring(m.start()+2);
                    } else {
                        warn = true;
                    }
                }
            }
        }
        if(warn) {
            Logger.warn("date range validation: could not match date '"+date+"' with format '"+pattern+"'");
        }
        return date;
    }
}
