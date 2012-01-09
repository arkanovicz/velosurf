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
 * <p>This class encapsulates a constraint on a column of an entity, used to validate data
 * before an update or an insert.</p>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public abstract class FieldConstraint
{
    /**
     * validate data against this constraint.
     * @param data to validate
     * @param locale
     * @return true if the data respects the constraint
     */
    public boolean validate(Object data, Locale locale) throws SQLException
    {
        return validate(data);
    }

    /**
     * validate data against this constraint.
     * @param data data to validate
     * @return true if data respects the constaint
     * @throws SQLException
     */
    public boolean validate(Object data) throws SQLException
    {
        return false;
    }

    /** the error message */
    private String message = null;

    /**
     * error message setter
     *
     * @param msg error message
     */
    public void setMessage(String msg)
    {
        message = msg;
    }

    /**
     * get the error message
     * @return the error message
     */
    public String getMessage()
    {
        return message;
    }
}
