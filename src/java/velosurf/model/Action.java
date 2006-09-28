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
import velosurf.sql.DataAccessor;
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
     * @param inEntity entity
     */
    public Action(String name,Entity inEntity) {
        mEntity = inEntity;
        mDB = mEntity.mDB;
        mName = name;
    }

    public void addParamName(String paramName) {
        mParamNames.add(paramName);
    }

    public void setQuery(String query) {
        mQuery = query;
    }

    /** executes this action
     *
     * @param inSource the object on which apply the action
     * @exception SQLException an SQL problem occurs
     * @return number of impacted rows
     */
    public int perform(DataAccessor inSource) throws SQLException {
        // TODO: check type
        List params = buildArrayList(inSource);
        return mDB.prepare(mQuery).update(params);
    }


    /** get the list of values for all parameters
     *
     * @param inSource the DataAccessor
     * @exception SQLException thrown by the DataAccessor
     * @return the list of values
     */
    public List buildArrayList(DataAccessor inSource) throws SQLException {
        ArrayList result = new ArrayList();
        if (inSource!=null)
            for (Iterator i = mParamNames.iterator();i.hasNext();) {
                String paramName = (String)i.next();
                Object value = inSource.get(paramName);
                if (mEntity.isObfuscated(paramName)) value = mDB.deobfuscate(value);
                if (value == null) Logger.warn("Query "+mQuery+": param "+paramName+" is null!");
                result.add(value);
            }
        return result;
    }

    /** get the name of the action
     *
     * @return the name
     */
    public String getName() {
        return mName;
    }

    /** for debugging purpose
     *
     * @return definition string
     */
    public String toString() {
        String result = "";
        if (mParamNames.size()>0) result += "("+StringLists.join(mParamNames,",")+")";
        result+=":"+mQuery;
        return result;
    }

    /** get the database connection
     *
     * @return the database connection
     */
    public Database getDB() {
        return mDB;
    }

    /** the satabase connection
     */
    protected Database mDB = null;
    /** the entity this action belongs to
     */
    protected Entity mEntity = null;
    /** the name of this action
     */
    protected String mName = null;

    // for simple actions
    /** parameter names of this action
     */
    protected List<String> mParamNames = new ArrayList<String>();
    /** query of this action
     */
    protected String mQuery = null;

}
