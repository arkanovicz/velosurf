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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import velosurf.context.RowIterator;
import velosurf.model.Entity;
import velosurf.util.Logger;
import velosurf.util.SlotMap;
import velosurf.util.SlotTreeMap;

/**
 * this class encapsulates a jdbc Statement.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 */
public class PooledSimpleStatement extends PooledStatement
{
    /**
     * build a new PooledStatement.
     *
     * @param connection database connection
     * @param statement wrapped Statement
     */
    public PooledSimpleStatement(ConnectionWrapper connection, Statement statement)
    {
        this.connection = connection;
        this.statement = statement;
    }

    /**
     * check whether this pooled object is marked as valid or invalid.
     * (used in the recovery process)
     *
     * @return whether this object is in use
     */
    public boolean isValid()
    {
        return super.isValid() && statement != null;
    }

    /**
     * get the resultset for this statement.
     *
     * @param query SQL query
     * @exception SQLException thrown by the database engine
     * @return resulting RowIterator
     */
    public synchronized RowIterator query(String query) throws SQLException
    {
        return query(query, null);
    }

    /**
     * get the resultset for this statement, specifying the entity the results belong to.
     *
     * @param query SQL query
     * @param resultEntity entity
     * @exception SQLException thrown by the database engine
     * @return the resulting RowIterator
     */
    public synchronized RowIterator query(String query, Entity resultEntity) throws SQLException
    {
        RowIterator result = null;

        try
        {
            Logger.trace("query-" + query);
            connection.enterBusyState();
            result = new RowIterator(this, statement.executeQuery(query), resultEntity);
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
     * fetch a single row.
     *
     * @param query SQL query
     * @exception SQLException thrown by the database engine
     * @return fetched row
     */
    public synchronized Serializable fetch(String query) throws SQLException
    {
        return fetch(query, null);
    }

    /**
     * fetch a single row, specyfing the entity it belongs to.
     *
     * @param query SQL query
     * @param resultEntity entity
     * @exception SQLException thrown by the database engine
     * @return the fetched Instance
     */
    public synchronized Serializable fetch(String query, Entity resultEntity) throws SQLException
    {
        try
        {
            Logger.trace("fetch-" + query);
            connection.enterBusyState();

            boolean hasNext = false;

            try
            {
                resultSet = statement.executeQuery(query);
                hasNext = resultSet.next();
            }
            finally
            {
                connection.leaveBusyState();
            }

            SlotMap row = null;

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
                    for(String column : columnNames)
                    {
                      Serializable value = (Serializable)resultSet.getObject(column);

                        row.put(Database.adaptContextCase(column), value);
                    }
                }
            }
            return (Serializable)row;
        }
        finally
        {
            notifyOver();
        }
    }

    /**
     * get specified column as an object.
     *
     * @param key column
     * @exception SQLException thrown by the database engine
     * @return object value
     */
    public Serializable get(Object key) throws SQLException
    {
        if(!(key instanceof String) || resultSet == null)
        {
            return null;
        }
        return (Serializable)resultSet.getObject((String)key);
    }

    public Set<String> keySet() throws SQLException
    {
        if(resultSet == null) new HashSet<String>();
        return new HashSet<String>(SqlUtil.getColumnNames(resultSet));
    }

    /**
     * evaluates the SQL query as  a scalar.
     *
     * @param query SQL query
     * @exception SQLException thrown by the database engine
     * @return found scalar
     */
    public synchronized Serializable evaluate(String query) throws SQLException
    {
        Logger.trace("evaluate-" + query);

        Object result = null;

        try
        {
            connection.enterBusyState();
            resultSet = statement.executeQuery(query);

            boolean hasNext = resultSet.next();

            if(hasNext)
            {
                result = resultSet.getObject(1);
            }
        }
        finally
        {
            connection.leaveBusyState();
            notifyOver();
        }
        return (Serializable)result;
    }

    /**
     * issue the update contained in the query.
     *
     * @param query SQL query
     * @exception SQLException thrown by the database engine
     * @return number of affected rows
     */
    public synchronized int update(String query) throws SQLException
    {
        try
        {
            Logger.trace("update-" + query);
            connection.enterBusyState();

            int result = statement.executeUpdate(query);

            return result;
        }
        finally
        {
            connection.leaveBusyState();
            notifyOver();
        }
    }

    /**
     * issue a query returning a number of rows
     *
     * @param query SQL query
     * @exception SQLException thrown by the database engine
     * @return number of rows
     */
    public synchronized int execute(String query) throws SQLException
    {
        try
        {
            Logger.trace("update-" + query);
            connection.enterBusyState();

						if(statement.execute(query)) throw new SQLException("execute() method only expect methods that returns a number of rows");
            int ret = statement.getUpdateCount();
						return ret == -1 ? 0 : ret;
        }
        finally
        {
            connection.leaveBusyState();
            notifyOver();
        }
    }

    /**
     * close this statement.
     *
     * @exception SQLException thrown by the database engine
     */
    public void close() throws SQLException
    {
        if(statement != null)
        {
            statement.close();
        }
    }

    /**
     * notify this statement is no more used and can be recycled.
     */
    public void notifyOver()
    {
        super.notifyOver();
    }

    /**
     * gets the last insert id.
     *
     * @exception SQLException thrown by the database engine
     * @return last insert id
     */
    public long getLastInsertID() throws SQLException
    {
        return((ConnectionWrapper)connection).getLastInsertId(statement);
    }

    /**
     * get statement's Connection.
     *
     *  @return the Connection object (usually a ConnectionWrapper object)
     */
    public ConnectionWrapper getConnection()
    {
        return connection;
    }

    /**
     * wrapped statement.
     */
    private transient Statement statement = null;
}
