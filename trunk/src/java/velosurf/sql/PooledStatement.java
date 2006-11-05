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

package velosurf.sql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import velosurf.context.RowIterator;
import velosurf.model.Entity;
import velosurf.util.Logger;

/** this class encapsulates a jdbc Statement
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 */
public class PooledStatement extends Pooled implements ReadOnlyMap {

    /** builds a new PooledStatement
     *
     * @param connection database connection
     * @param statement wrapped Statement
     */
    protected PooledStatement(ConnectionWrapper connection,Statement statement) {
        this.connection = connection;
        this.statement = statement;
    }

    /** gets the resultset for this statement
     *
     * @param query SQL query
     * @exception SQLException thrown by the database engine
     * @return resulting RowIterator
     */
    public synchronized RowIterator query(String query) throws SQLException {    return query(query,null); }
    /** gets the resultset for this statement, specifying the entity the results belong to
     *
     * @param query SQL query
     * @param resultEntity entity
     * @exception SQLException thrown by the database engine
     * @return the resulting RowIterator
     */
    public synchronized RowIterator query(String query,Entity resultEntity) throws SQLException {
        notifyInUse();
        this.query = query;
        Logger.debug("query-"+query);
        connection.enterBusyState();
        RowIterator result = new RowIterator(this,statement.executeQuery(query),resultEntity);
        connection.leaveBusyState();
        return result;
    }

    /** fetch a single row
     *
     * @param query SQL query
     * @exception SQLException thrown by the database engine
     * @return fetched row
     */
    public synchronized Object fetch(String query) throws SQLException { return fetch(query,null); }
    /** fetch a single row, specyfing the entity it belongs to
     *
     * @param query SQL query
     * @param resultEntity entity
     * @exception SQLException thrown by the database engine
     * @return the fetched Instance
     */
    public synchronized Object fetch(String query,Entity resultEntity) throws SQLException {
        notifyInUse();
        Logger.debug("fetch-"+query);
        connection.enterBusyState();
        resultSet = statement.executeQuery(query);
        boolean hasNext = resultSet.next();
        connection.leaveBusyState();
        Map row = null;
        if (hasNext) {
            if (resultEntity!=null) row = resultEntity.getInstance(this);
            else {
                row = new HashMap();
                if (columnNames == null) columnNames = SqlUtil.getColumnNames(resultSet);
                for (Iterator it=columnNames.iterator();it.hasNext();) {
                    String column = (String)it.next();
                    Object value = resultSet.getObject(column);
                    row.put(Database.adaptContextCase(column),value);
                }
            }
        }
        notifyOver();
        return row;
    }

    /** get specified column as an object
     *
     * @param key column
     * @exception SQLException thrown by the database engine
     * @return object value
     */
    public Object get(Object key) throws SQLException {
        if (!(key instanceof String)) return null;
        return resultSet.getObject((String)key);
    }

    public Set keySet() throws SQLException {
        return new HashSet(SqlUtil.getColumnNames(resultSet));
    }


    /** evaluates the SQL query as  a scalar
     *
     * @param query SQL query
     * @exception SQLException thrown by the database engine
     * @return found scalar
     */
    public synchronized Object evaluate(String query) throws SQLException {
        notifyInUse();
        Logger.debug("evaluate-"+query);
        Object result = null;
        ResultSet rs = null;
        try {
            connection.enterBusyState();
            rs = statement.executeQuery(query);
            boolean hasNext = rs.next();
            connection.leaveBusyState();
            if (hasNext) result=rs.getObject(1);
        }
        finally {
            if (rs != null) rs.close();
            notifyOver();
        return result;
        }
    }

    /** issues the update contained in the query
     *
     * @param query SQL query
     * @exception SQLException thrown by the database engine
     * @return number of affected rows
     */
    public synchronized int update(String query) throws SQLException {
        Logger.debug("update-"+query);
        connection.enterBusyState();
        int result = statement.executeUpdate(query);
        connection.leaveBusyState();
        return result;
    }

    /** close thos statement
     *
     * @exception SQLException thrown by the database engine
     */
    public void close() throws SQLException {
        if (statement!=null) statement.close();
    }

    /** notify this statement is no more used and can be recycled
     */
    public void notifyOver() {
        super.notifyOver();
    }

    /** gets the last insert id
     *
     * @exception SQLException thrown by the database engine
     * @return last insert id
     */
    public long getLastInsertID() throws SQLException {
        return ((ConnectionWrapper)connection).getLastInsertId(statement);
    }

    /** get statement's Connection
     *
     *  @return the Connection object (usually a ConnectionWrapper object)
     */
    public ConnectionWrapper getConnection() {
        return connection;
    }

    /** SQL query
     */
    protected String query = null;
    /** database connection
     */
    protected ConnectionWrapper connection = null;
    /** result set
     */
    protected ResultSet resultSet = null;
    /** column names in natural order
     */
    protected List columnNames = null;
    /** wrapped statement
     */
    protected Statement statement = null;
}
