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
import java.lang.reflect.Method;
import java.sql.*;
import java.util.Map;
import java.util.Properties;
import velosurf.util.Logger;

/**
 * Connection wrapper class. Allows the handling of a busy state
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */

public class ConnectionWrapper
    implements Connection, Serializable
{

    /**
     * Constructor.
     * @param driver infos on the driver
     * @param connection connection to be wrapped
     */
    public ConnectionWrapper(DriverInfo driver,Connection connection)
    {
        this.driver = driver;
        this.connection = connection;
    }

    /**
     * Unwrap the connection.
     * @return the unwrapped connection
     */
    public Connection unwrap()
    {
        return connection;
    }

    /**
     * Create a statement.
     * @return created statement
     * @throws SQLException
     */
    public synchronized Statement createStatement()
        throws SQLException
    {
        try
        {
            enterBusyState();
            return connection.createStatement();
        }
        finally
        {
            leaveBusyState();
        }
    }

    /**
     * Prepare a statement.
     * @param s SQL query
     * @return prepared statement
     * @throws SQLException
     */
    public synchronized PreparedStatement prepareStatement(String s)
        throws SQLException
    {
        try
        {
            enterBusyState();
            return connection.prepareStatement(s);
        }
        finally
        {
            leaveBusyState();
        }
    }

    /**
     * Prepare a callable statement.
     * @param s SQL query
     * @return prepared callable statement
     * @throws SQLException
     */
    public synchronized CallableStatement prepareCall(String s)
        throws SQLException
    {
        try
        {
            enterBusyState();
            return connection.prepareCall(s);
        }
        finally
        {
            leaveBusyState();
        }
    }

    /**
     * Gets native SQL for a query.
     * @param s query
     * @return native SQL
     * @throws SQLException
     */
    public synchronized String nativeSQL(String s)
        throws SQLException
    {
        try
        {
            enterBusyState();
            return connection.nativeSQL(s);
        }
        finally
        {
            leaveBusyState();
        }
    }

    /**
     * Set autocommit flag.
     * @param flag autocommit
     * @throws SQLException
     */
    public void setAutoCommit(boolean flag)
        throws SQLException
    {
        connection.setAutoCommit(flag);
    }

    /**
     * Get autocommit flag.
     *
     * @return autocommit flag
     * @throws SQLException
     */
    public boolean getAutoCommit()
        throws SQLException
    {
        return connection.getAutoCommit();
    }

    /**
     * Commit.
     *
     * @throws SQLException
     */
    public synchronized void commit()
        throws SQLException
    {
        try
        {
            enterBusyState();
            connection.commit();
        }
        finally
        {
            leaveBusyState();
        }
    }

    /**
     * Rollback.
     *
     * @throws SQLException
     */
    public synchronized void rollback()
        throws SQLException
    {
        try
        {
            enterBusyState();
            connection.rollback();
        }
        finally
        {
            leaveBusyState();
        }
    }

    /**
     * Close.
     *
     * @throws SQLException
     */
    public void close()
        throws SQLException
    {
        // since some sql drivers refuse to close a connection that has been interrupted,
        // better handle this also ourselves
        closed = true;
        connection.close();
    }

    /**
     * Check the closed state.
     * @return closed state
     * @throws SQLException
     */
    public boolean isClosed()
        throws SQLException
    {
        return (closed || connection == null || connection.isClosed());
    }

    /**
     * Get meta data
     * @return database meta data
     * @throws SQLException
     */
    public DatabaseMetaData getMetaData()
        throws SQLException
    {
        return connection.getMetaData();
    }

    /**
     * set read-only flag
     * @param flag read-only
     * @throws SQLException
     */
    public void setReadOnly(boolean flag)
        throws SQLException
    {
        connection.setReadOnly(flag);
    }

    /**
     * Check the read-only state.
     * @return read-only state
     * @throws SQLException
     */
    public boolean isReadOnly()
        throws SQLException
    {
        return connection.isReadOnly();
    }

    /**
     * Catalog setter.
     * @param s catalog
     * @throws SQLException
     */
    public void setCatalog(String s)
        throws SQLException
    {
        connection.setCatalog(s);
    }

    /**
     * Catalog getter.
     * @return catalog
     * @throws SQLException
     */

    public String getCatalog()
        throws SQLException
    {
        return connection.getCatalog();
    }
    /**
     * Transaction isolation setter.
     * @param i transaction isolation
     * @throws SQLException
     */

    public void setTransactionIsolation(int i)
        throws SQLException
    {
        connection.setTransactionIsolation(i);
    }

    /**
     * Transaction isolation getter.
     * @return transaction isolation
     * @throws SQLException
     */
    public int getTransactionIsolation()
        throws SQLException
    {
        return connection.getTransactionIsolation();
    }

    /**
     * Get SQL warnings.
     * @return next SQL Warning.
     * @throws SQLException
     */
    public SQLWarning getWarnings()
        throws SQLException
    {
        return connection.getWarnings();
    }

    /**
     * Clear SQL warnings.
     * @throws SQLException
     */
    public void clearWarnings()
        throws SQLException
    {
        connection.clearWarnings();
    }

    /**
     * Create a statement.
     *
     * @param i result set type
     * @param j result set concurrency
     * @return new statement
     * @throws SQLException
     */
    public synchronized Statement createStatement(int i, int j)
        throws SQLException
    {
        try
        {
            enterBusyState();
            return connection.createStatement(i, j);
        }
        finally
        {
            leaveBusyState();
        }

    }

    /**
     * Prepare a statement.
     * @param s SQL query
     * @param i result set type
     * @param j result set concurrency
     * @return prepared statement
     * @throws SQLException
     */
    public synchronized PreparedStatement prepareStatement(String s, int i, int j)
        throws SQLException
    {
        try
        {
            enterBusyState();
            return connection.prepareStatement(s, i, j);
        }
        finally
        {
            leaveBusyState();
        }
    }

    /**
     * Prepare a call.
     * @param s SQL query
     * @param i result set type
     * @param j result set concurrency
     * @return callable statement
     * @throws SQLException
     */
    public synchronized CallableStatement prepareCall(String s, int i, int j)
        throws SQLException
    {
        try
        {
            enterBusyState();
            return connection.prepareCall(s, i, j);
        }
        finally
        {
            leaveBusyState();
        }
    }

    /**
     * Get type map.
     * @return type map
     * @throws SQLException
     */
    public Map getTypeMap()
        throws SQLException
    {
        return connection.getTypeMap();
    }

    /**
     * Set type map.
     * @param map type map
     * @throws SQLException
     */
    public void setTypeMap(Map map)
        throws SQLException
    {
        connection.setTypeMap(map);
    }

    /**
     * Set holdability.
     * @param i holdability
     * @throws SQLException
     */
    public void setHoldability(int i)
        throws SQLException
    {
        connection.setHoldability(i);
    }

    /**
     * Get holdability.
     * @return holdability
     * @throws SQLException
     */
    public int getHoldability()
        throws SQLException
    {
        return connection.getHoldability();
    }

    /**
     * Savepoint setter.
     * @return save point
     * @throws SQLException
     */
    public synchronized Savepoint setSavepoint()
        throws SQLException
    {
        return connection.setSavepoint();
    }

    /**
     * Set named savepoint.
     * @param s savepoint name
     * @return savepoint
     * @throws SQLException
     */
    public synchronized Savepoint setSavepoint(String s)
        throws SQLException
    {
        return connection.setSavepoint(s);
    }

    /**
     * Rollback.
     * @param savepoint savepoint
     * @throws SQLException
     */
    public synchronized void rollback(Savepoint savepoint)
        throws SQLException
    {
        connection.rollback(savepoint);
    }
    /**
     * Release savepoint.
     *
     * @param savepoint savepoint
     * @throws SQLException
     */
    public synchronized void releaseSavepoint(Savepoint savepoint)
        throws SQLException
    {
        connection.releaseSavepoint(savepoint);
    }

    /**
     * Create a statement.
     * @param i result set type
     * @param j result set concurrency
     * @param k result set holdability
     * @return created statement
     * @throws SQLException
     */
    public synchronized Statement createStatement(int i, int j, int k)
        throws SQLException
    {
        try
        {
           enterBusyState();
            return connection.createStatement(i, j, k);
        }
        finally 
        {
            leaveBusyState();
        }
    }

    /**
     * Prepare a statement.
     * @param s SQL query
     * @param i result set type
     * @param j result set concurrency
     * @param k result set holdability
     * @return prepared statement
     * @throws SQLException
     */
    public synchronized PreparedStatement prepareStatement(String s, int i, int j, int k)
        throws SQLException
    {
        try
        {
            enterBusyState();
            return connection.prepareStatement(s, i, j, k);
        }
        finally
        {
            leaveBusyState();
        }
    }

    /**
     * Prepare a callable statement.
     * @param s SQL query
     * @param i result set type
     * @param j result set concurrency
     * @param k result set holdability
     * @return prepared statement
     * @throws SQLException
     */
    public synchronized CallableStatement prepareCall(String s, int i, int j, int k)
        throws SQLException
    {
        try
        {
            enterBusyState();
            return connection.prepareCall(s, i, j, k);
        }
        finally
        {
            leaveBusyState();
        }
    }

    /**
     * Prepare a statement.
     * @param s SQL query
     * @param i autogenerated keys
     * @return prepared statement
     * @throws SQLException
     */
    public synchronized PreparedStatement prepareStatement(String s, int i)
        throws SQLException
    {
        try
        {
            enterBusyState();
            return connection.prepareStatement(s, i);
        }
        finally
        {
            leaveBusyState();
        }
    }

    /**
     * Prepare a statement.
     * @param s SQL query
     * @param ai autogenerated keys column indexes
     * @return prepared statement
     * @throws SQLException
     */
    public synchronized PreparedStatement prepareStatement(String s, int ai[])
        throws SQLException
    {
        try
        {
            enterBusyState();
            return connection.prepareStatement(s, ai);
        }
        finally
        {
            leaveBusyState();
        }
    }

    /**
     * Prepare a statement.
     * @param s SQL query
     * @param as autogenerated keys column names
     * @return prepared statement
     * @throws SQLException
     */
    public synchronized PreparedStatement prepareStatement(String s, String as[])
        throws SQLException
    {
        try     
        {
            enterBusyState();
            return connection.prepareStatement(s,as);
        }
        finally
        {
            leaveBusyState();
        }
    }

    /** 
     * Enter busy state.
     */
    public synchronized void enterBusyState()
    {
        //Logger.trace("connection #"+toString()+": entering busy state.");
        busy++;
    }

    /**
     * Leave busy state.
     */
    public synchronized void leaveBusyState()
    {
        lastUse = System.currentTimeMillis();
        busy--;
        //Logger.trace("connection #"+toString()+": leaving busy state.");
    }

    /**
     * Check busy state.
     * @return busy state
     */
    public boolean isBusy()
    {
        return busy>0;
    }

    /**
     * Get last use timestamp
     *
     * @return last use
     */
    public long getLastUse()
    {
      return lastUse;
    }

    /**
     * Get last inserted ID.
     * 
     * @param statement
     * @return last inserted id
     * @throws SQLException
     */
    public long getLastInsertId(Statement statement) throws SQLException
    {
        return driver.getLastInsertId(statement);
    }

    /**
     * Check connection.
     *
     * @return true if the connection is ok
     */
    public synchronized boolean check()
    {
        try
        {
            String checkQuery = driver.getPingQuery();
            if (checkQuery == null)
            {
                // at least, call isClosed
                if (isClosed())
                {
                    return false;
                }
            }
            else
            {
                if (checkStatement == null)
                {
                    checkStatement = prepareStatement(checkQuery);
                }
                checkStatement.executeQuery();
            }
            return true;
        }
        catch (Exception e)
        {
            Logger.warn("Exception while checking connection!");
            Logger.info("Refreshing...");
            return false;
        }
    }

    /** Infos on the driver. */
    private DriverInfo driver = null;

    /** Wrapped connection. */
    private transient Connection connection = null;

    /** Busy state. */
    private int busy = 0;

    /** Last use */
    private long lastUse = System.currentTimeMillis();

    /** Closed state. */
    private boolean closed = false;

    /** statement used to check connection ("select 1").
     */
    private transient PreparedStatement checkStatement = null;

    /*
     * stores new 1.6 methods using reflection api to ensure backward compatibility
     */

    static Method _createClob = null;
    static Method _createBlob = null;
    static Method _createNClob = null;
    static Method _createSQLXML = null;
    static Method _isValid = null;
    static Method _setClientInfo = null;
    static Method _setClientInfo2 = null;
    static Method _getClientInfo = null;
    static Method _getClientInfo2 = null;
    static Method _createArrayOf = null;
    static Method _createStruct = null;
    static Method _isWrapperFor = null;

    static
    {
        try
        {
            _createClob = getConnectionMethod("createClob",new Class[]{});
            _createBlob = getConnectionMethod("createBlob",new Class[]{});
            _createNClob = getConnectionMethod("createNClob",new Class[]{});
            _createSQLXML = getConnectionMethod("createSQLXML",new Class[]{});
            _isValid = getConnectionMethod("isValid",new Class[]{int.class});
            _setClientInfo = getConnectionMethod("setClientInfo",new Class[]{String.class,String.class});
            _setClientInfo2 = getConnectionMethod("setClientInfo",new Class[]{Properties.class});
            _getClientInfo = getConnectionMethod("getClientInfo",new Class[]{});
            _getClientInfo2 = getConnectionMethod("getClientInfo",new Class[]{String.class});
            _createArrayOf = getConnectionMethod("createArrayOf",new Class[]{String.class,Class.forName("[Ljava.lang.Object;")});
            _createStruct = getConnectionMethod("createStruct",new Class[]{String.class,Class.forName("[Ljava.lang.Object;")});
            _isWrapperFor = getConnectionMethod("isWrapperFor",new Class[]{Class.class});
        }
        catch(Exception e)
        {
        }
    }

    static private Method getConnectionMethod(String name,Class[] parameterTypes)
    {
        try
        {
            return Connection.class.getMethod(name,parameterTypes);
        }
        catch(NoSuchMethodException nsme)
        {
            return null;
        }
    }

    public Clob createClob() throws SQLException
    {
        if(_createClob == null)
        {
            throw new SQLException("Unsupported method.");
        }
        else
        {
            try
            {
                return (Clob)_createClob.invoke(connection, new Object[] {});
            }
            catch(Exception e)
            {
                Throwable cause = e.getCause();
                if (cause == null)
                {
                    cause = e;
                }
                if(cause instanceof SQLException)
                {
                    throw (SQLException)cause;
                }
                else
                {
                    throw new SQLException(cause);
                }
            }
        }
    }

    public Blob createBlob() throws SQLException
    {
        if(_createBlob == null)
        {
            throw new SQLException("Unsupported method.");
        }
        else
        {
            try
            {
                return (Blob)_createBlob.invoke(connection, new Object[] {});
            }
            catch(Exception e)
            {
                Throwable cause = e.getCause();
                if (cause == null)
                {
                    cause = e;
                }
                if(cause instanceof SQLException)
                {
                    throw (SQLException)cause;
                }
                else
                {
                    throw new SQLException(cause);
                }
            }
        }
    }

    public NClob createNClob() throws SQLException
    {
        if(_createNClob == null)
        {
            throw new SQLException("Unsupported method.");
        }   
        else
        {
            try
            {
                return (NClob)_createNClob.invoke(connection, new Object[] {});
            }
            catch(Exception e)
            {
                Throwable cause = e.getCause();
                if (cause == null)
                {
                    cause = e;
                }
                if(cause instanceof SQLException)
                {
                    throw (SQLException)cause;
                }
                else
                {
                    throw new SQLException(cause);
                }
            }
        }
    }

    public SQLXML createSQLXML() throws SQLException
    {
        if(_createSQLXML == null)
        {
            throw new SQLException("Unsupported method.");
        }
        else
        {
            try
            {
                return (SQLXML)_createSQLXML.invoke(connection, new Object[] {});
            }
            catch(Exception e)
            {
                Throwable cause = e.getCause();
                if (cause == null)
                {
                    cause = e;
                }
                if(cause instanceof SQLException)
                {
                    throw (SQLException)cause;
                }
                else
                {
                    throw new SQLException(cause);
                }
            }
        }
    }

    public boolean isValid(int timeout) throws SQLException
    {
        if(_isValid == null)
        {
            throw new SQLException("Unsupported method.");
        }
        else
        {
            try
            {
                return (Boolean)_isValid.invoke(connection, new Object[] {timeout});
            }
            catch(Exception e)
            {
                Throwable cause = e.getCause();
                if (cause == null)
                {
                    cause = e;
                }
                if(cause instanceof SQLException)
                {
                    throw (SQLException)cause;
                }
                else
                {
                    throw new SQLException(cause);
                }
            }
        }
    }

    public void setClientInfo(String name,String value)
    {
        if(_setClientInfo == null)
        {
            throw new RuntimeException("Unsupported method.");
        }
        else
        {
            try
            {
                _setClientInfo.invoke(connection, new Object[] {name,value});
            }
            catch(Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public void setClientInfo(Properties properties)
    {
        if(_setClientInfo2 == null)
        {
            throw new RuntimeException("Unsupported method.");
        }
        else
        {
            try
            {
                _setClientInfo2.invoke(connection, new Object[] {properties});
            }
            catch(Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public Properties getClientInfo() throws SQLException
    {
        if(_getClientInfo == null)
        {
            throw new SQLException("Unsupported method.");
        }
        else
        {
            try
            {
                return (Properties)_getClientInfo.invoke(connection, new Object[] {});
            }
            catch(Exception e)
            {
                Throwable cause = e.getCause();
                if (cause == null)
                {
                    cause = e;
                }
                if(cause instanceof SQLException)
                {
                    throw (SQLException)cause;
                }
                else
                {
                    throw new SQLException(cause);
                }
            }
        }
    }

    public String getClientInfo(String name) throws SQLException
    {
        if(_getClientInfo2 == null)
        {
            throw new SQLException("Unsupported method.");
        }
        else
        {
            try
            {
                return (String)_getClientInfo2.invoke(connection, new Object[] {name});
            }
            catch(Exception e)
            {
                Throwable cause = e.getCause();
                if (cause == null)
                {
                    cause = e;
                }
                if(cause instanceof SQLException)
                {
                    throw (SQLException)cause;
                }
                else
                {
                    throw new SQLException(cause);
                }
            }
        }
    }

    public Array createArrayOf(String typeName, Object[] elements) throws SQLException
    {
        if(_createArrayOf == null)
        {
            throw new SQLException("Unsupported method.");
        }
        else
        {
            try
            {
                return (Array)_createArrayOf.invoke(connection, new Object[] {typeName,elements});
            }
            catch(Exception e)
            {
                Throwable cause = e.getCause();
                if (cause == null)
                {
                    cause = e;
                }
                if(cause instanceof SQLException)
                {
                    throw (SQLException)cause;
                }
                else
                {
                    throw new SQLException(cause);
                }
            }
        }
    }

    public Struct createStruct(String typeName, Object[] attributes) throws SQLException
    {
        if(_createStruct == null)
        {
            throw new SQLException("Unsupported method.");
        }
        else
        {
            try
            {
                return (Struct)_createStruct.invoke(connection, new Object[] {typeName,attributes});
            }
            catch(Exception e)
            {
                Throwable cause = e.getCause();
                if (cause == null)
                {
                    cause = e;
                }
                if(cause instanceof SQLException)
                {
                    throw (SQLException)cause;
                }
                else
                {
                    throw new SQLException(cause);
                }
            }
        }
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
        throw new SQLException("Unsupported method.");
    }

    public <T> T unwrap(Class<T> iface) throws SQLException
    {
        throw new SQLException("Unsupported method.");
    }
}
