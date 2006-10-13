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

/** Connection wrapper class - allows handling of a busy state
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */

import velosurf.util.Logger;

import java.sql.*;
import java.util.Map;
import java.util.Properties;

public class ConnectionWrapper
    implements Connection
{

    public ConnectionWrapper(DriverInfo inDriver,Connection inConn)
    {
        driver = inDriver;
        connection = inConn;
    }

    public Connection unwrap()
    {
        return connection;
    }

    // this operation should not be heavy - don't trigger busy state
    public Statement createStatement()
        throws SQLException
    {
        return connection.createStatement();
    }

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

    public void setAutoCommit(boolean flag)
        throws SQLException
    {
        connection.setAutoCommit(flag);
    }

    public boolean getAutoCommit()
        throws SQLException
    {
        return connection.getAutoCommit();
    }

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

    public void close()
        throws SQLException
    {
        // since some sql drivers refuse to close a connection that has been interrupted,
        // better handle this also ourselves
        closed = true;
        connection.close();
    }

    public boolean isClosed()
        throws SQLException
    {
        return (closed || connection.isClosed());
    }

    public DatabaseMetaData getMetaData()
        throws SQLException
    {
        return connection.getMetaData();
    }

    public void setReadOnly(boolean flag)
        throws SQLException
    {
        connection.setReadOnly(flag);
    }

    public boolean isReadOnly()
        throws SQLException
    {
        return connection.isReadOnly();
    }

    public void setCatalog(String s)
        throws SQLException
    {
        connection.setCatalog(s);
    }

    public String getCatalog()
        throws SQLException
    {
        return connection.getCatalog();
    }

    public void setTransactionIsolation(int i)
        throws SQLException
    {
        connection.setTransactionIsolation(i);
    }

    public int getTransactionIsolation()
        throws SQLException
    {
        return connection.getTransactionIsolation();
    }

    public SQLWarning getWarnings()
        throws SQLException
    {
        return connection.getWarnings();
    }

    public void clearWarnings()
        throws SQLException
    {
        connection.clearWarnings();
    }

    // this operation should not be heavy - don't trigger busy state
    public synchronized Statement createStatement(int i, int j)
        throws SQLException
    {
        return connection.createStatement(i, j);
    }

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

    public Map getTypeMap()
        throws SQLException
    {
        return connection.getTypeMap();
    }

    public void setTypeMap(Map map)
        throws SQLException
    {
        connection.setTypeMap(map);
    }

    public void setHoldability(int i)
        throws SQLException
    {
        connection.setHoldability(i);
    }

    public int getHoldability()
        throws SQLException
    {
        return connection.getHoldability();
    }

    public synchronized Savepoint setSavepoint()
        throws SQLException
    {
        return connection.setSavepoint();
    }

    public synchronized Savepoint setSavepoint(String s)
        throws SQLException
    {
        return connection.setSavepoint(s);
    }

    public synchronized void rollback(Savepoint savepoint)
        throws SQLException
    {
        connection.rollback(savepoint);
    }

    public synchronized void releaseSavepoint(Savepoint savepoint)
        throws SQLException
    {
        connection.releaseSavepoint(savepoint);
    }

    public synchronized Statement createStatement(int i, int j, int k)
        throws SQLException
    {
        return connection.createStatement(i, j, k);
    }

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

    public void enterBusyState() {
        //Logger.trace("connection #"+toString()+": entering busy state.");
        busy++;
    }

    public void leaveBusyState() {
        busy--;
        //Logger.trace("connection #"+toString()+": leaving busy state.");
    }

    public boolean isBusy() {
        return busy>0;
    }

    public long getLastInsertId(Statement statement) {
        return driver.getLastInsertId(statement);
    }

    /** check connection
     *
     * @return true if the connection is ok
     */
    protected synchronized boolean check() {
        try {
            String checkQuery = driver.getPingQuery();
            if (checkQuery == null) {
                // at least, call isOpen
                if (isClosed()) throw new Exception ("Connection is closed");
            }
            else {
                if (mCheckStatement == null) mCheckStatement = prepareStatement(checkQuery);
                mCheckStatement.executeQuery();
            }
            return true;
        }
        catch (Exception e) {
            Logger.warn("Exception while checking connection!");
            Logger.info("Refreshing...");
            return false;
        }
    }

    protected DriverInfo driver = null;
    protected Connection connection = null;
    protected int busy = 0;
    protected boolean closed = false;

    /** statement used to check connection ("select 1")
     */
    protected PreparedStatement mCheckStatement = null;
}
