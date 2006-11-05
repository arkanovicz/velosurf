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
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;

import velosurf.sql.ReadOnlyMap;
import velosurf.sql.ConnectionWrapper;
import velosurf.sql.PooledPreparedStatement;
import velosurf.util.StringLists;

/** This class is an action that gather several consecutive queries
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 */
public class Transaction extends Action
{
    /** Builds a new transaction
     *
     * @param name transaction name
     * @param entity entity
     */
    public Transaction(String name,Entity entity) {
        super(name,entity);
    }

    public void setQueries(List<String> queries) {
        this.queries = queries;
    }

    public void setParamNamesLists(List<List<String>> paramLists) {
        paramNamesList = paramLists;
    }

    /** performs this action
     *
     * @param source DataAccessor containing parameter values
     * @exception SQLException thrown from the database
     * @return number of affected rows (addition of all the partial counts)
     */
    public int perform(ReadOnlyMap source) throws SQLException {

        ConnectionWrapper conn = db.getTransactionConnection();
        try {
            int nb = queries.size();
            int ret = 0;
            for (int i=0; i<nb; i++) {
                // fool the buildArrayList method by using
                //  the super member paramNames
                paramNames = (List)paramNamesList.get(i);
                List params = buildArrayList(source);
                /* TODO: pool transaction statements */
                PooledPreparedStatement statement = new PooledPreparedStatement(conn,conn.prepareStatement(queries.get(i),ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY));
                ret += statement.update(params);
                statement.close();
            }
            conn.commit();
            return ret;
        }
        catch (SQLException sqle) {
            conn.rollback();
            throw sqle;
        }
        finally {
            conn.leaveBusyState();
        }
    }

    /** debug method
     *
     * @return the definition string of the transaction
     */
    public String toString() {
        StringBuffer result = new StringBuffer();
        int nb = queries.size();
        int ret = 0;
        for (int i=0; i<nb; i++) {
            List paramNames = (List)paramNamesList.get(i);
            if (paramNames.size()>0) {
                result.append("(");
                result.append(StringLists.join(paramNames,",")+")");
            }
            result.append(":"+queries.get(i));
            if (i<nb-1) result.append('\n');
        }
        return result.toString();

    }

    /** all the queries
     */
    protected List<String> queries; //  = null; WARNING : this init code is executed AFER Action constructor
    /** list of lists of parameter names
     */
    protected List<List<String>> paramNamesList; // = null;

}
