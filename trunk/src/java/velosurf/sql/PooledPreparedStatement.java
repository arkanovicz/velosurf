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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import velosurf.context.RowIterator;
import velosurf.model.Entity;
import velosurf.util.Logger;

/** this class encapsulates a jdbc PreparedStatement
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 */
public class PooledPreparedStatement extends Pooled implements DataAccessor {


    /** builds a new PooledPreparedStatement
     *
     * @param inConnection database connection
     * @param inPreparedStatement wrapped prepared statement
     */
    public PooledPreparedStatement(ConnectionWrapper inConnection,PreparedStatement inPreparedStatement) {
        mConnection = inConnection;
        mPreparedStatement = inPreparedStatement;
    }

    // get a unique object (typically by id)
    /** get a unique object by id
     *
     * @param inParams parameter values
     * @exception SQLException thrown bu the database engine
     * @return fetched Instance
     */
    public synchronized Object fetch(List inParams) throws SQLException { return fetch(inParams,null); }

    /** get a unique object by id and specify the Entity this object is an Instance of
     *
     * @param inParams parameter values
     * @param inResultEntity resulting entity
     * @exception SQLException thrown by the database engine
     * @return the fetched Instance
     */
    public synchronized Object fetch(List inParams,Entity inResultEntity) throws SQLException {
        notifyInUse();
        setParams(inParams);
        mConnection.enterBusyState();
        mRS = mPreparedStatement.executeQuery();
        boolean hasNext = mRS.next();
        mConnection.leaveBusyState();
        mEntity = inResultEntity;
        Map row = null;
        if (hasNext) {
            if (inResultEntity!=null) row = inResultEntity.getInstance(this);
            else {
                row = new HashMap();
                if (mColumnNames == null) mColumnNames = SqlUtil.getColumnNames(mRS);
                for (Iterator it=mColumnNames.iterator();it.hasNext();) {
                    String column = (String)it.next();
                    Object value = mRS.getObject(column);
                    if (value != null && !mRS.wasNull())
                        row.put(column,value);
                }
            }
        }
        notifyOver();
        return row;
    }

    /** get a unique object by id and specify the Entity this object is an Instance of
     *
     * @param inParams parameter values
     * @param inResultEntity resulting entity
     * @exception SQLException thrown by the database engine
     * @return the fetched Instance
     */
    public synchronized Object fetch(Map inParams,Entity inResultEntity) throws SQLException {
        List values = new ArrayList();
        for (Iterator i = inResultEntity.getKeys().iterator();i.hasNext();) {
            String key = (String)i.next();
            String value = (String)inParams.get(key);
            if (value==null) throw new SQLException("Error: key column '"+key+"' is not specified!");
            values.add(value);
        }
        return fetch(values,inResultEntity);
    }

    /** gets the rowset
     *
     * @param inParams parameter values
     * @exception SQLException thrown by the database engine
     * @return the resulting row iterator
     */
    public synchronized RowIterator query(List inParams) throws SQLException { return query(inParams,null); }
    /** gets the rowset
     *
     * @param inParams parameter values
     * @param inResultEntity resulting entity
     * @exception SQLException thrown by the database engine
     * @return resulting RowIterator
     */
    public synchronized RowIterator query(List inParams,Entity inResultEntity) throws SQLException {
        notifyInUse();
        setParams(inParams);
        mConnection.enterBusyState();
        RowIterator result = new RowIterator(this,mPreparedStatement.executeQuery(),inResultEntity);
        mConnection.leaveBusyState();
        return result;
    }

    /** gets a scalar result from this statement
     *
     * @param inParams parameter values
     * @exception SQLException thrown bu the database engine
     * @return scalar result
     */
    public synchronized Object evaluate(List inParams) throws SQLException {
        Object value = null;
        int i = 0;
        ResultSet rs = null;
        notifyInUse();
        try {
            setParams(inParams);
            mConnection.enterBusyState();
            rs = mPreparedStatement.executeQuery();
            boolean hasNext = rs.next();
            mConnection.leaveBusyState();
            if (hasNext) {
                value = rs.getObject(1);
                if (rs.wasNull()) value = null;
            }
        }
        finally {
            if (rs != null) rs.close();
            notifyOver();
        }
        return value;
    }

    /** issues the modification query of this prepared statement
     *
     * @param inParams parameter values
     * @exception SQLException thrown by the database engine
     * @return the numer of affected rows
     */
    public synchronized int update(List inParams) throws SQLException {
        notifyInUse();
        setParams(inParams);
        mConnection.enterBusyState();
        int rows = mPreparedStatement.executeUpdate();
        mConnection.leaveBusyState();
        notifyOver();
        return rows;
    }

    /** get the object value of the specified resultset column
     *
     * @param key the name of the resultset column
     * @exception SQLException thrown by the database engine
     * @return the object value returned by jdbc
     */
    public synchronized Object get(Object key) throws SQLException {
        if (!(key instanceof String)) return null;
        Object ret = mRS.getObject((String)key);
        if (mEntity != null && mEntity.isObfuscated((String)key))
            ret = mEntity.obfuscate((ret));
        return ret;
    }

    /** get the last insert id - implemented only for mysql for now...
     *
     * @exception SQLException thrown by the database engine
     * @return the last insert id
     */
    public synchronized long getLastInsertID() throws SQLException {
        return ((ConnectionWrapper)mConnection).getLastInsertId(mPreparedStatement);
    }

    /** close this statement
     *
     * @exception SQLException thrown by the database engine
     */
    public synchronized void close() throws SQLException{
        if (mPreparedStatement!=null) mPreparedStatement.close();
    }

    /** get statement's Connection
     *
     *  @return the Connection object (usually a ConnectionWrapper object)
     */
    public ConnectionWrapper getConnection() {
        return mConnection;
    }


    /** set prepared parameter values
     *
     * @param inParams parameter values
     * @exception SQLException thrown by the database engine
     */
    protected void setParams(List inParams) throws SQLException {
        for (int i=0;i<inParams.size();i++) {
            mPreparedStatement.setObject(i+1,inParams.get(i));
        }
    }

    /** the connection
     */
    protected ConnectionWrapper mConnection = null;
    /** the result set
     */
    protected ResultSet mRS = null;
    /** column names
     */
    protected List mColumnNames = null;
    /** wrapped prepared statement
     */
    protected PreparedStatement mPreparedStatement = null;
    /** the resulting entity
     */
    protected Entity mEntity = null;
    /** has meta information been fetched ?
     */
    protected boolean mMetaDone = false; // hum...
}
