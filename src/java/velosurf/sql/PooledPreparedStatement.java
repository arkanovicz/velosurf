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

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import velosurf.context.RowIterator;
import velosurf.model.Entity;
import velosurf.util.Logger;
import velosurf.util.SlotMap;
import velosurf.util.SlotTreeMap;
import velosurf.util.StringLists;

/**
 * this class encapsulates a jdbc PreparedStatement.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 */
public class PooledPreparedStatement extends PooledStatement implements RowHandler
{
    /** org.apache.velocity.tools.generic.ValueParser$ValueParserSub class, if found in the classpath. */
    private static Class valueParserSubClass = null;

    static
    {
        try
        {
            valueParserSubClass = Class.forName("org.apache.velocity.tools.generic.ValueParser$ValueParserSub");
        }
        catch(ClassNotFoundException cnfe) {}
    }

    /**
     * build a new PooledPreparedStatement.
     *
     * @param connection database connection
     * @param preparedStatement wrapped prepared statement
     */
    public PooledPreparedStatement(ConnectionWrapper connection, PreparedStatement preparedStatement)
    {
        this.connection = connection;
        this.preparedStatement = preparedStatement;
    }

    /**
     * check whether this pooled object is marked as valid or invalid.
     * (used in the recovery process)
     *
     * @return whether this object is in use
     */
    public boolean isValid()
    {
        return super.isValid() && preparedStatement != null;
    }

    /**
     * get a unique object by id.
     *
     * @param params parameter values
     * @exception SQLException thrown bu the database engine
     * @return fetched Instance
     */
    public synchronized Serializable fetch(List params) throws SQLException
    {
        return fetch(params, null);
    }

    /**
     * get a unique object by id and specify the Entity this object is an Instance of.
     *
     * @param params parameter values
     * @param resultEntity resulting entity
     * @exception SQLException thrown by the database engine
     * @return the fetched Instance
     */
    public synchronized Serializable fetch(List params, Entity resultEntity) throws SQLException
    {
        SlotMap row = null;

        try
        {
            Logger.trace("fetch-params=" + StringLists.join(params, ","));
            setParams(params);

            boolean hasNext = false;

            try
            {
                connection.enterBusyState();
                resultSet = preparedStatement.executeQuery();
                hasNext = resultSet.next();
            }
            finally
            {
                connection.leaveBusyState();
            }
            entity = resultEntity;
            if(hasNext)
            {
                if(resultEntity != null)
                {
                    row = resultEntity.newInstance(new ReadOnlyMap(this), true);
                }
                else
                {
                  row = new SlotTreeMap();
                    if(columnNames == null)
                    {
                        columnNames = SqlUtil.getColumnNames(resultSet);
                    }
                    for(Iterator it = columnNames.iterator(); it.hasNext(); )
                    {
                        String column = (String)it.next();
                        Serializable value = (Serializable)resultSet.getObject(column);

                        if(value != null &&!resultSet.wasNull())
                        {
                            row.put(column, value);
                        }
                    }
                }
            }
        }
        finally
        {
            notifyOver();
        }
        return (Serializable)row;
    }

    /**
     * get a unique object by id and specify the Entity this object is an Instance of.
     *
     * @param params parameter values
     * @param resultEntity resulting entity
     * @exception SQLException thrown by the database engine
     * @return the fetched Instance
     */
    public synchronized Serializable fetch(SlotMap params, Entity resultEntity) throws SQLException
    {
        List<Object> values = new ArrayList<Object>();

        for(Iterator i = resultEntity.getPKCols().iterator(); i.hasNext(); )
        {
            String key = (String)i.next();
            String value = (String)params.get(key);

            if(value == null)
            {
                throw new SQLException("Error: key column '" + key + "' is not specified!");
            }
            values.add(value);
        }
        return fetch(values, resultEntity);
    }

    /**
     * get the rowset.
     *
     * @param params parameter values
     * @exception SQLException thrown by the database engine
     * @return the resulting row iterator
     */
    public synchronized RowIterator query(List params) throws SQLException
    {
        return query(params, null);
    }

    /**
     * get the rowset.
     *
     * @param params parameter values
     * @param resultEntity resulting entity
     * @exception SQLException thrown by the database engine
     * @return resulting RowIterator
     */
    public synchronized RowIterator query(List params, Entity resultEntity) throws SQLException
    {
        RowIterator result = null;

        try
        {
            Logger.trace("query-params=" + StringLists.join(params, ","));
            if(params != null)
            {
                setParams(params);
            }
            connection.enterBusyState();
            result = new RowIterator(this, preparedStatement.executeQuery(), resultEntity);
            return result;
        }
        finally
        {
            connection.leaveBusyState();
            if(result == null)
            {
                notifyOver();
            }
        }
    }

    /**
     * get a scalar result from this statement.
     *
     * @param params parameter values
     * @exception SQLException thrown bu the database engine
     * @return scalar result
     */
    public synchronized Serializable evaluate(List params) throws SQLException
    {
        Serializable value = null;

        try
        {
            Logger.trace("evaluate-params=" + StringLists.join(params, ","));
            if(params != null)
            {
                setParams(params);
            }
            connection.enterBusyState();
            resultSet = preparedStatement.executeQuery();

            boolean hasNext = resultSet.next();

            if(hasNext)
            {
              value = (Serializable)resultSet.getObject(1);
                if(resultSet.wasNull())
                {
                    value = null;
                }
            }
        }
        finally
        {
            connection.leaveBusyState();
            notifyOver();
        }
        return value;
    }

    /**
     * issue the modification query of this prepared statement.
     *
     * @param params parameter values
     * @exception SQLException thrown by the database engine
     * @return the numer of affected rows
     */
    public synchronized int update(List params) throws SQLException
    {
        try
        {
            Logger.trace("update-params=" + StringLists.join(params, ","));
            setParams(params);
            connection.enterBusyState();

            int rows = preparedStatement.executeUpdate();

            return rows;
        }
        finally
        {
            connection.leaveBusyState();
            notifyOver();
        }
    }

    /**
     * get the object value of the specified resultset column.
     *
     * @param key the name of the resultset column
     * @exception SQLException thrown by the database engine
     * @return the object value returned by jdbc
     */
    public synchronized Serializable get(Object key) throws SQLException
    {
        if(!(key instanceof String) || resultSet == null)
        {
            return null;
        }

        Serializable ret = (Serializable)resultSet.getObject((String)key);

        if(entity != null && entity.isObfuscated((String)key))
        {
            ret = entity.obfuscate((ret));
        }
        return ret;
    }

    public Set<String> keySet() throws SQLException
    {
        if(resultSet == null) return new HashSet<String>();
        return new HashSet<String>(SqlUtil.getColumnNames(resultSet));
    }

    /**
     * get the last insert id.
     *
     * @exception SQLException thrown by the database engine
     * @return the last insert id
     */
    public synchronized Object getLastInsertID() throws SQLException
    {
        return((ConnectionWrapper)connection).getLastInsertId(preparedStatement);
    }

    /**
     * close this statement.
     *
     * @exception SQLException thrown by the database engine
     */
    public synchronized void close() throws SQLException
    {
        if(preparedStatement != null)
        {
            preparedStatement.close();
        }
    }

    /**
     * get statement Connection.
     *
     *  @return the Connection object (usually a ConnectionWrapper object)
     */
    public ConnectionWrapper getConnection()
    {
        return connection;
    }

    /**
     * set prepared parameter values.
     *
     * @param params parameter values
     * @exception SQLException thrown by the database engine
     */
    private void setParams(List params) throws SQLException
    {
        for(int i = 0; i < params.size(); i++)
        {
            Object param = params.get(i);

            if(valueParserSubClass != null && valueParserSubClass.isAssignableFrom(param.getClass()))
            {
                param = param.toString();
            }
            preparedStatement.setObject(i + 1, param);
        }
    }

    private List columnNames = null;

    /**
     * wrapped prepared statement.
     */
    private transient PreparedStatement preparedStatement = null;

    /**
     * the resulting entity.
     */
    private Entity entity = null;

    /**
     * has meta information been fetched?
     */
}
