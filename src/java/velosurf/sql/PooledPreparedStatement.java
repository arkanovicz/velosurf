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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;

import velosurf.context.RowIterator;
import velosurf.model.Entity;
import velosurf.util.Logger;

/** this class encapsulates a jdbc PreparedStatement.
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 */

public class PooledPreparedStatement extends PooledStatement  implements RowHandler {

    /** org.apache.velocity.tools.generic.ValueParser$ValueParserSub class, if found in the classpath. */
    private static Class valueParserSubClass = null;

    static {
        try {
            valueParserSubClass = Class.forName("org.apache.velocity.tools.generic.ValueParser$ValueParserSub");
        } catch(ClassNotFoundException cnfe) {}
    }

    /** build a new PooledPreparedStatement.
     *
     * @param connection database connection
     * @param preparedStatement wrapped prepared statement
     */
    public PooledPreparedStatement(ConnectionWrapper connection,PreparedStatement preparedStatement) {
        this.connection = connection;
        this.preparedStatement = preparedStatement;
    }

    /** get a unique object by id.
     *
     * @param params parameter values
     * @exception SQLException thrown bu the database engine
     * @return fetched Instance
     */
    public synchronized Object fetch(List params) throws SQLException
    {
        return fetch(params,null);
    }

    /** get a unique object by id and specify the Entity this object is an Instance of.
     *
     * @param params parameter values
     * @param resultEntity resulting entity
     * @exception SQLException thrown by the database engine
     * @return the fetched Instance
     */
    public synchronized Object fetch(List params,Entity resultEntity) throws SQLException {
        notifyInUse();
        setParams(params);
        connection.enterBusyState();
        resultSet = preparedStatement.executeQuery();
        boolean hasNext = resultSet.next();
        connection.leaveBusyState();
        entity = resultEntity;
        Map<String,Object> row = null;
        if (hasNext) {
            if (resultEntity!=null) row = resultEntity.newInstance(new ReadOnlyMap(this),true);
            else {
                row = new TreeMap<String,Object>();
                if (columnNames == null) columnNames = SqlUtil.getColumnNames(resultSet);
                for (Iterator it=columnNames.iterator();it.hasNext();) {
                    String column = (String)it.next();
                    Object value = resultSet.getObject(column);
                    if (value != null && !resultSet.wasNull())
                        row.put(column,value);
                }
            }
        }
        notifyOver();
        return row;
    }

    /** get a unique object by id and specify the Entity this object is an Instance of.
     *
     * @param params parameter values
     * @param resultEntity resulting entity
     * @exception SQLException thrown by the database engine
     * @return the fetched Instance
     */
    public synchronized Object fetch(Map<String,Object> params,Entity resultEntity) throws SQLException {
        List<Object> values = new ArrayList<Object>();
        for (Iterator i = resultEntity.getPKCols().iterator();i.hasNext();) {
            String key = (String)i.next();
            String value = (String)params.get(key);
            if (value==null) throw new SQLException("Error: key column '"+key+"' is not specified!");
            values.add(value);
        }
        return fetch(values,resultEntity);
    }

    /** get the rowset.
     *
     * @param params parameter values
     * @exception SQLException thrown by the database engine
     * @return the resulting row iterator
     */
    public synchronized RowIterator query(List params) throws SQLException { return query(params,null); }
    /** get the rowset.
     *
     * @param params parameter values
     * @param resultEntity resulting entity
     * @exception SQLException thrown by the database engine
     * @return resulting RowIterator
     */
    public synchronized RowIterator query(List params,Entity resultEntity) throws SQLException {
        notifyInUse();
        if (params != null) {
            setParams(params);
        }
        connection.enterBusyState();
        RowIterator result = new RowIterator(this,preparedStatement.executeQuery(),resultEntity);
        connection.leaveBusyState();
        return result;
    }

    /** get a scalar result from this statement.
     *
     * @param params parameter values
     * @exception SQLException thrown bu the database engine
     * @return scalar result
     */
    public synchronized Object evaluate(List params) throws SQLException {
        Object value = null;
        ResultSet rs = null;
        notifyInUse();
        try {
            if (params != null) {
                setParams(params);
            }
            connection.enterBusyState();
            rs = preparedStatement.executeQuery();
            boolean hasNext = rs.next();
            connection.leaveBusyState();
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

    /** issue the modification query of this prepared statement.
     *
     * @param params parameter values
     * @exception SQLException thrown by the database engine
     * @return the numer of affected rows
     */
    public synchronized int update(List params) throws SQLException {
        notifyInUse();
        setParams(params);
        connection.enterBusyState();
        int rows = preparedStatement.executeUpdate();
        connection.leaveBusyState();
        notifyOver();
        return rows;
    }

    /** get the object value of the specified resultset column.
     *
     * @param key the name of the resultset column
     * @exception SQLException thrown by the database engine
     * @return the object value returned by jdbc
     */

    public synchronized Object get(Object key) throws SQLException {
        if (!(key instanceof String)) return null;
        Object ret = resultSet.getObject((String)key);
        if (entity != null && entity.isObfuscated((String)key))
            ret = entity.obfuscate((ret));
        return ret;
    }

    public Set<String> keySet() throws SQLException {
        return new HashSet<String>(SqlUtil.getColumnNames(resultSet));
    }

    /** get the last insert id.
     *
     * @exception SQLException thrown by the database engine
     * @return the last insert id
     */
    public synchronized long getLastInsertID() throws SQLException {
        return ((ConnectionWrapper)connection).getLastInsertId(preparedStatement);
    }

    /** close this statement.
     *
     * @exception SQLException thrown by the database engine
     */
    public synchronized void close() throws SQLException{
        if (preparedStatement!=null) preparedStatement.close();
    }

    /** get statement Connection.
     *
     *  @return the Connection object (usually a ConnectionWrapper object)
     */
    public ConnectionWrapper getConnection() {
        return connection;
    }


    /** set prepared parameter values.
     *
     * @param params parameter values
     * @exception SQLException thrown by the database engine
     */
    private void setParams(List params) throws SQLException {
        for (int i=0;i<params.size();i++) {
            Object param = params.get(i);
            if (valueParserSubClass != null && valueParserSubClass.isAssignableFrom(param.getClass())) {
                param = param.toString();
            }
            preparedStatement.setObject(i+1,param);
        }
    }

    /** the connection.
     */
    private ConnectionWrapper connection = null;
    /** the result set.
     */
    private ResultSet resultSet = null;
    /** column names.
     */
    private List columnNames = null;
    /** wrapped prepared statement.
     */
    private PreparedStatement preparedStatement = null;
    /** the resulting entity.
     */
    private Entity entity = null;
    /** has meta information been fetched?
     */
}
