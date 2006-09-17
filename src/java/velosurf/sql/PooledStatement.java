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

import velosurf.context.RowIterator;
import velosurf.model.Entity;
import velosurf.util.Logger;

/** this class encapsulates a jdbc Statement
 *
 *  <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 */
public class PooledStatement extends Pooled implements DataAccessor {

    /** builds a new PooledStatement
     *
     * @param inConnection database connection
     * @param inStatement wrapped Statement
     */
    protected PooledStatement(ConnectionWrapper inConnection,Statement inStatement) {
        mConnection = inConnection;
        mStatement = inStatement;
    }

    /** gets the resultset for this statement
     *
     * @param inQuery SQL query
     * @exception SQLException thrown by the database engine
     * @return resulting RowIterator
     */
    public synchronized RowIterator query(String inQuery) throws SQLException {    return query(inQuery,null); }
    /** gets the resultset for this statement, specifying the entity the results belong to
     *
     * @param inQuery SQL query
     * @param inResultEntity entity
     * @exception SQLException thrown by the database engine
     * @return the resulting RowIterator
     */
    public synchronized RowIterator query(String inQuery,Entity inResultEntity) throws SQLException {
        notifyInUse();
        mQuery = inQuery;
        Logger.debug("query-"+inQuery);
        mConnection.enterBusyState();
        RowIterator result = new RowIterator(this,mStatement.executeQuery(inQuery),inResultEntity);
        mConnection.leaveBusyState();
        return result;
    }

    /** fetch a single row
     *
     * @param inQuery SQL query
     * @exception SQLException thrown by the database engine
     * @return fetched row
     */
    public synchronized Object fetch(String inQuery) throws SQLException { return fetch(inQuery,null); }
    /** fetch a single row, specyfing the entity it belongs to
     *
     * @param inQuery SQL query
     * @param inResultEntity entity
     * @exception SQLException thrown by the database engine
     * @return the fetched Instance
     */
    public synchronized Object fetch(String inQuery,Entity inResultEntity) throws SQLException {
        notifyInUse();
        Logger.debug("fetch-"+inQuery);
        mConnection.enterBusyState();
        mRS = mStatement.executeQuery(inQuery);
        boolean hasNext = mRS.next();
        mConnection.leaveBusyState();
        Map row = null;
        if (hasNext) {
            if (inResultEntity!=null) row = inResultEntity.getInstance(this);
            else {
                row = new HashMap();
                if (mColumnNames == null) mColumnNames = SqlUtil.getColumnNames(mRS);
                for (Iterator it=mColumnNames.iterator();it.hasNext();) {
                    String column = (String)it.next();
                    Object value = mRS.getObject(column);
                    row.put(column,value);
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
        return mRS.getObject((String)key);
    }

    /** evaluates the SQL query as  a scalar
     *
     * @param inQuery SQL query
     * @exception SQLException thrown by the database engine
     * @return found scalar
     */
    public synchronized Object evaluate(String inQuery) throws SQLException {
        notifyInUse();
        Logger.debug("evaluate-"+inQuery);
        Object result = null;
        ResultSet rs = null;
        try {
            mConnection.enterBusyState();
            rs = mStatement.executeQuery(inQuery);
            boolean hasNext = rs.next();
            mConnection.leaveBusyState();
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
     * @param inQuery SQL query
     * @exception SQLException thrown by the database engine
     * @return number of affected rows
     */
    public synchronized int update(String inQuery) throws SQLException {
        Logger.debug("update-"+inQuery);
        mConnection.enterBusyState();
        int result = mStatement.executeUpdate(inQuery);
        mConnection.leaveBusyState();
        return result;
    }

    /** close thos statement
     *
     * @exception SQLException thrown by the database engine
     */
    public void close() throws SQLException {
        if (mStatement!=null) mStatement.close();
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
        return ((ConnectionWrapper)mConnection).getLastInsertId(mStatement);
    }

    /** get statement's Connection
     *
     *  @return the Connection object (usually a ConnectionWrapper object)
     */
    public ConnectionWrapper getConnection() {
        return mConnection;
    }
    
    /** SQL query
     */
    protected String mQuery = null;
    /** database connection
     */
    protected ConnectionWrapper mConnection = null;
    /** result set
     */
    protected ResultSet mRS = null;
    /** column names in natural order
     */
    protected List mColumnNames = null;
    /** wrapped statement
     */
    protected Statement mStatement = null;
}
