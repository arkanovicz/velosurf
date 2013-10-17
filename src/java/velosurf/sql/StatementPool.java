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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import velosurf.util.Logger;

/**
 * This class is a pool of PooledStatements.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 */
public class StatementPool implements /* Runnable, */ Pool
{
    /**
     * build a new pool.
     *
     * @param connectionPool connection pool
     */
    protected StatementPool(ConnectionPool connectionPool, boolean checkConnections, long checkInterval)
    {
        this.connectionPool = connectionPool;
        this.checkConnections = checkConnections;
        this.checkInterval = checkInterval;

//      if(checkConnections) checkTimeoutThread = new Thread(this);
//      checkTimeoutThread.start();
    }

    /**
     * get a valid statement.
     *
     * @exception SQLException thrown by the database engine
     * @return a valid statement
     */
    public synchronized PooledSimpleStatement getStatement() throws SQLException
    {
        PooledSimpleStatement statement = null;
        ConnectionWrapper connection = null;

        for(Iterator<PooledSimpleStatement> it = statements.iterator(); it.hasNext(); )
        {
            statement = it.next();
            if(statement.isValid())
            {
                if(!statement.isInUse() && !(connection = (ConnectionWrapper)statement.getConnection()).isBusy())
                {
                    // check connection
                    if(!connection.isClosed() && (!checkConnections || System.currentTimeMillis() - connection.getLastUse() < checkInterval || connection.check()))
                    {
                        statement.notifyInUse();
                        return statement;
                    }
                    else
                    {
                        dropConnection(connection);
                        it.remove();
                    }
                }
            }
            else
            {
                it.remove();
            }
        }
        if(count == maxStatements)
        {
            throw new SQLException("Error: Too many opened statements!");
        }
        connection = connectionPool.getConnection();
        statement = new PooledSimpleStatement(connection,
            connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY));
        statements.add(statement);
        statement.notifyInUse();
        return statement;
    }

    // timeout loop

    /**
     * run the loop of statements checking and recycling.
     * 
     * public void run() {
     *   while (running) {
     *       try {
     *           Thread.sleep(checkDelay);
     *       } catch (InterruptedException e) {}
     *       long now = System.currentTimeMillis();
     *       for(PooledSimpleStatement statement:statements) {
     *           if (statement.isInUse() && now-statement.getTagTime() > timeout)
     *               statement.notifyOver();
     *       }
     *   }
     * }
     */

    /**
     * debug - two ints long array containing nb of statements in use and total nb of statements.
     *
     * @return 2 integers long array
     */
    public int[] getUsageStats()
    {
        int[] stats = new int[] { 0, 0 };

        for(PooledSimpleStatement statement : statements)
        {
            if(!statement.isInUse())
            {
                stats[0]++;
            }
        }
        stats[1] = statements.size();
        return stats;
    }

    /**
     * close all statements.
     */
    public void clear()
    {
        // close all statements
        for(PooledSimpleStatement statement : statements)
        {
            try
            {
                statement.close();
            }
            catch(SQLException sqle)
            {    // don't care now...
                Logger.log(sqle);
            }
        }
        for(Iterator it = statements.iterator(); it.hasNext(); )
        {
            statements.clear();
        }
    }

    /*
     *  drop all statements relative to a specific connection
     * @param connection the connection
     */
    private void dropConnection(ConnectionWrapper connection)
    {
        for(Iterator it = statements.iterator(); it.hasNext(); )
        {
            PooledSimpleStatement statement = (PooledSimpleStatement)it.next();

            if(statement.getConnection() == connection)
            {
                try
                {
                    statement.close();
                }
                catch(SQLException sqle) {}
                statement.setInvalid();
            }
        }
        try
        {
            connection.close();
        }
        catch(SQLException sqle) {}
    }

    /**
     * close statements on exit.
     */
    protected void finalize()
    {
        clear();
    }

    /**
     * Connection pool.
     */
    private ConnectionPool connectionPool = null;

    /**
     * number of statements.
     */
    private int count = 0;

    /**
     * statements.
     */
    private List<PooledSimpleStatement> statements = new ArrayList<PooledSimpleStatement>();

    /**
     * timeout checking thread.
     */
    private Thread checkTimeoutThread = null;

    /**
     * is the thread running?
     */
    private boolean running = true;

    /**
     * do we need to check connections?
     */
    private boolean checkConnections = true;

    /**
     * minimal check interval
     */
    private long checkInterval;

    /**
     * delay between checks.
     */

//  private static final long checkDelay = 30*1000;

    /**
     * timeout on which statements are automatically recycled if not used.
     */
//  private static final long timeout = 10*60*1000;

    /**
     * maximum number of statements.
     */
    private static final int maxStatements = 50;
}
