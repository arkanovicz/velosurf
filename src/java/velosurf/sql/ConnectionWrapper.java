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

import velosurf.util.Logger;

import java.sql.*;
import java.util.Map;

/** Connection wrapper class. Allows the handling of a busy state
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */

public class ConnectionWrapper
    implements Connection
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
    public Statement createStatement()
        throws SQLException
    {
        try {
            enterBusyState();
            return connection.createStatement();
        } finally {
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
        try {
            enterBusyState();
            return connection.prepareStatement(s);
        } finally {
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
        try {
            enterBusyState();
            return connection.prepareCall(s);
        } finally {
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
        try {
            enterBusyState();
            return connection.nativeSQL(s);
        } finally {
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
    /** Get autocommit flag.
     *
     * @return autocommit flag
     * @throws SQLException
     */
    public boolean getAutoCommit()
        throws SQLException
    {
        return connection.getAutoCommit();
    }
    /** Commit.
     *
     * @throws SQLException
     */
    public synchronized void commit()
        throws SQLException
    {
        try {
            enterBusyState();
            connection.commit();
        } finally {
            leaveBusyState();
        }
    }
    /** Rollback.
     *
     * @throws SQLException
     */
    public synchronized void rollback()
        throws SQLException
    {
        try {
            enterBusyState();
            connection.rollback();
        } finally {
            leaveBusyState();
        }
    }
    /** Close.
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
        return (closed || connection.isClosed());
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

    /** Create a statement.
     *
     * @param i result set type
     * @param j result set concurrency
     * @return new statement
     * @throws SQLException
     */
    public synchronized Statement createStatement(int i, int j)
        throws SQLException
    {
        try {
            enterBusyState();
            return connection.createStatement(i, j);
        } finally {
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
        try {
            enterBusyState();
            return connection.prepareStatement(s, i, j);
        } finally {
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
        try {
            enterBusyState();
            return connection.prepareCall(s, i, j);
        } finally {
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
    /** Release savepoint.
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
        try {
           enterBusyState();
            return connection.createStatement(i, j, k);
        } finally {
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
        try {
            enterBusyState();
            return connection.prepareStatement(s, i, j, k);
        } finally {
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
        try {
            enterBusyState();
            return connection.prepareCall(s, i, j, k);
        } finally {
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
        try {
            enterBusyState();
            return connection.prepareStatement(s, i);
        } finally {
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
        try {
            enterBusyState();
            return connection.prepareStatement(s, ai);
        } finally {
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
        try {
            enterBusyState();
            return connection.prepareStatement(s,as);
        } finally {
            leaveBusyState();
        }
    }
    /** Enter busy state.
     */
    public void enterBusyState() {
        //Logger.trace("connection #"+toString()+": entering busy state.");
        busy++;
    }
    /** Leave busy state.
     */
    public void leaveBusyState() {
        busy--;
        //Logger.trace("connection #"+toString()+": leaving busy state.");
    }
    /** Check busy state.
     * @return busy state
     */
    public boolean isBusy() {
        return busy>0;
    }
    /**
     * Get last inserted ID.
     * @param statement
     * @return last inserted id
     * @throws SQLException
     */
    public long getLastInsertId(Statement statement) throws SQLException {
        return driver.getLastInsertId(statement);
    }

    /** Check connection.
     *
     * @return true if the connection is ok
     */
    public synchronized boolean check() {
        try {
            String checkQuery = driver.getPingQuery();
            if (checkQuery == null) {
                // at least, call isOpen
                if (isClosed()) throw new Exception ("Connection is closed");
            }
            else {
                if (checkStatement == null) checkStatement = prepareStatement(checkQuery);
                checkStatement.executeQuery();
            }
            return true;
        }
        catch (Exception e) {
            Logger.warn("Exception while checking connection!");
            Logger.info("Refreshing...");
            return false;
        }
    }
    /** Infos on the driver. */
    private DriverInfo driver = null;
    /** Wrapped connection. */
    private Connection connection = null;
    /** Busy state. */
    private int busy = 0;
    /** Closed state. */
    private boolean closed = false;

    /** statement used to check connection ("select 1").
     */
    private PreparedStatement checkStatement = null;
}
