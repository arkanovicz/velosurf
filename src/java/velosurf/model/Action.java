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

package velosurf.model;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import velosurf.sql.Database;
import velosurf.sql.ReadOnlyMap;
import velosurf.util.Logger;
import velosurf.util.StringLists;

/** This class correspond to custom update and delete queries
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 */
public class Action
{
    /** Constructor
     *
     * @param name name
     * @param entity entity
     */
    public Action(String name,Entity entity) {
        this.entity = entity;
        db = this.entity.db;
        this.name = name;
    }

    public void addParamName(String paramName) {
        paramNames.add(paramName);
    }

    public void setQuery(String query) {
        this.query = query;
    }

    /** executes this action
     *
     * @param source the object on which apply the action
     * @exception SQLException an SQL problem occurs
     * @return number of impacted rows
     */
    public int perform(ReadOnlyMap source) throws SQLException {
        // TODO: check type
        List params = buildArrayList(source);
        return db.prepare(query).update(params);
    }


    /** get the list of values for all parameters
     *
     * @param source the DataAccessor
     * @exception SQLException thrown by the DataAccessor
     * @return the list of values
     */
    public List buildArrayList(ReadOnlyMap source) throws SQLException {
        ArrayList result = new ArrayList();
        if (source!=null)
            for (Iterator i = paramNames.iterator();i.hasNext();) {
                String paramName = (String)i.next();
                Object value = source.get(paramName);
                if (entity.isObfuscated(paramName)) value = db.deobfuscate(value);
                if (value == null) Logger.warn("Query "+query+": param "+paramName+" is null!");
                result.add(value);
            }
        return result;
    }

    /** get the name of the action
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /** for debugging purpose
     *
     * @return definition string
     */
    public String toString() {
        String result = "";
        if (paramNames.size()>0) result += "("+StringLists.join(paramNames,",")+")";
        result+=":"+query;
        return result;
    }

    /** get the database connection
     *
     * @return the database connection
     */
    public Database getDB() {
        return db;
    }

    /** the satabase connection
     */
    protected Database db = null;
    /** the entity this action belongs to
     */
    protected Entity entity = null;
    /** the name of this action
     */
    protected String name = null;

    // for simple actions
    /** parameter names of this action
     */
    protected List<String> paramNames = new ArrayList<String>();
    /** query of this action
     */
    protected String query = null;

}
