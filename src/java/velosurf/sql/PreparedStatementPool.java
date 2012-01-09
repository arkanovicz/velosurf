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
import java.util.Iterator;
import java.util.List;
import velosurf.util.HashMultiMap;
import velosurf.util.Logger;

/**
 * This class is a pool of PooledPreparedStatements.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 */
public class PreparedStatementPool implements /* Runnable, */ Pool
{
    /**
     * build a new pool.
     *
     * @param connectionPool connection pool
     */
    protected PreparedStatementPool(ConnectionPool connectionPool, boolean checkConnections, long checkInterval)
    {
        this.connectionPool = connectionPool;
        this.checkConnections = checkConnections;
        this.checkInterval = checkInterval;

//      if(checkConnections) checkTimeoutThread = new Thread(this);
//      checkTimeoutThread.start();
    }

    /**
     * get a PooledPreparedStatement associated with this query.
     *
     * @param query an SQL query
     * @exception SQLException thrown by the database engine
     * @return a valid statement
     */
    public synchronized PooledPreparedStatement getPreparedStatement(String query) throws SQLException
    {
        Logger.trace("prepare-" + query);

        PooledPreparedStatement statement = null;
        ConnectionWrapper connection = null;
        List available = statementsMap.get(query);

        for(Iterator it = available.iterator(); it.hasNext(); )
        {
            statement = (PooledPreparedStatement)it.next();
            if(statement.isValid())
            {
                if(!statement.isInUse() &&!(connection = (ConnectionWrapper)statement.getConnection()).isBusy())
                {
                    // check connection
                    if(!checkConnections || System.currentTimeMillis() - connection.getLastUse() < checkInterval
                        || connection.check())
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
            throw new SQLException("Error: Too many opened prepared statements!");
        }
        connection = connectionPool.getConnection();
        statement = new PooledPreparedStatement(connection,
            connection.prepareStatement(query, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY));
        statementsMap.put(query, statement);
        statement.notifyInUse();
        return statement;
    }

    /**
     * cycle through statements to check and recycle them.
     * 
     * public void run() {
     *   while (running) {
     *       try {
     *           Thread.sleep(checkDelay);
     *       } catch (InterruptedException e) {}
     *       long now = System.currentTimeMillis();
     *       PooledPreparedStatement statement = null;
     *       for (Iterator it=statementsMap.keySet().iterator();it.hasNext();)
     *           for (Iterator jt=statementsMap.get(it.next()).iterator();jt.hasNext();) {
     *               statement = (PooledPreparedStatement)jt.next();
     *               if (statement.isInUse() && now-statement.getTagTime() > timeout)
     *                   statement.notifyOver();
     *           }
     *   }
     * }
     */

    /**
     * close all statements.
     */
    public void clear()
    {
        // close all statements
        for(Iterator it = statementsMap.keySet().iterator(); it.hasNext(); )
        {
            for(Iterator jt = statementsMap.get(it.next()).iterator(); jt.hasNext(); )
            {
                try
                {
                    ((PooledPreparedStatement)jt.next()).close();
                }
                catch(SQLException e)
                {    // don't care now...
                    Logger.log(e);
                }
            }
        }
        statementsMap.clear();
    }

    /*
     *  drop all statements relative to a specific connection
     * @param connection the connection
     */
    private void dropConnection(Connection connection)
    {
        for(Iterator it = statementsMap.keySet().iterator(); it.hasNext(); )
        {
            for(Iterator jt = statementsMap.get(it.next()).iterator(); jt.hasNext(); )
            {
                PooledPreparedStatement statement = (PooledPreparedStatement)jt.next();

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
     * clear statements on exit.
     */
    protected void finalize()
    {
        clear();
    }

    /**
     * debug - get usage statistics.
     *
     * @return an int array : [nb of statements in use , total nb of statements]
     */
    public int[] getUsageStats()
    {
        int[] stats = new int[] { 0, 0 };

        for(Iterator it = statementsMap.keySet().iterator(); it.hasNext(); )
        {
            for(Iterator jt = statementsMap.get(it.next()).iterator(); jt.hasNext(); )
            {
                if(!((PooledPreparedStatement)jt.next()).isInUse())
                {
                    stats[0]++;
                }
            }
        }
        stats[1] = statementsMap.size();
        return stats;
    }

    /**
     * connection pool.
     */
    private ConnectionPool connectionPool;

    /**
     * statements count.
     */
    private int count = 0;

    /**
     * map queries -> statements.
     */
    private HashMultiMap statementsMap = new HashMultiMap();    // query -> PooledPreparedStatement

    /**
     * running thread.
     */
    private Thread checkTimeoutThread = null;

    /**
     * true if running.
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
     * check delay.
     */

//  private static final long checkDelay = 30*1000;

    /**
     * after this timeout, statements are recycled even if not closed.
     */
//  private static final long timeout = 60*60*1000;

    /**
     * max number of statements.
     */
    private static final int maxStatements = 50;
}
