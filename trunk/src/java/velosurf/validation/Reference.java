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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.sql.SQLException;

import velosurf.sql.Database;
import velosurf.sql.PooledPreparedStatement;
import velosurf.util.Logger;

/**
 * <p>A foreign key constraint. Syntax is:</p>
 *  <pre>
 *    &lt;<i>column</i> references="<i>table.foreign-key</i>"&gt;
 * </pre>
 * or:
 * <pre>
 *   &lt;<i>column</i>&gt;
 *     &lt;references foreign-key="<i>table.foreign-key</i>" [message="<i>error-message</i>"]/&gt;
 *   &lt;/<i>column</i>&gt;
 * </pre>
 *
 * <p>Note: his constraint is not meant to replace an internal SQL "references" clause in the database,
 * since it cannot be made sure that complex updates will respect this constraint.</p>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class Reference extends FieldConstraint {

    protected Database db = null;
    protected String table = null;
    protected String column = null;

    /**
     * Constructor.
     * @param table the table name
     * @param column the column name
     */
    public Reference(Database db,String table,String column) {
        this.db = db;
        this.table = table;
        this.column = column;
        setMessage("field {0}: value '{1}' not found in "+table+"."+column);
    }

    /**
     *
     * @param data the data to be validated
     * @return true if data respects the specified reference
     */
    public boolean validate(Object data) {
        try {
            if (data == null || data.toString().length() == 0) return true;
            List param = new ArrayList();
            param.add(data);
            /* TODO this kind of query may vary with dbms...
               - under Oracle, we need to add "from dual"
               - does it return "1" or "true"?
              So, need to add some stuff to DriverInfo.
              For now, tweak the ping query.*/

            String query = db.getDriverInfo().getPingQuery();
            query = query.replace("1","? in (select distinct "+column+" from "+table+")");
            PooledPreparedStatement stmt = db.prepare(query);
            Object ret = stmt.evaluate(param);
            return ret != null && ret.equals(Boolean.valueOf(true));
        } catch(SQLException sqle) {
            Logger.warn("reference constraint validation threw an exception: "+sqle.getMessage());
            return false;
        }
    }

    public String toString() {
        return "references "+table+"."+column;
    }

}
