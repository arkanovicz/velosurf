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

import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Connection;

import java.util.Iterator;
import java.util.List;

import velosurf.util.HashMultiMap;
import velosurf.util.Logger;

/** This class is a pool of PooledPreparedStatements
 *
 * @author Claude Brisson
 *
 */
public class PreparedStatementPool implements Runnable,Pool {

	/** builds a new pool
	 *
	 * @param inConnectionPool connection pool
	 */
	protected PreparedStatementPool(ConnectionPool inConnectionPool) {
		mConnectionPool = inConnectionPool;
		mCheckTimeoutThread = new Thread(this);
//		mCheckTimeoutThread.start();
	}

	/** gets a PooledPreparedStatement associated with this query
	 *
	 * @param inQuery an SQL query
	 * @exception SQLException thrown by the database engine
	 * @return a valid statement
	 */
	public synchronized PooledPreparedStatement getPreparedStatement(String inQuery) throws SQLException {
		Logger.trace("prepare-"+inQuery);
		PooledPreparedStatement statement = null;
        ConnectionWrapper connection = null;
		List available = mStatementsMap.get(inQuery);
		for (Iterator it = available.iterator();it.hasNext();) {
			statement = (PooledPreparedStatement)it.next();
			if (statement.isValid()) {
                if (!statement.isInUse() && !(connection=(ConnectionWrapper)statement.getConnection()).isBusy()) {
                    // check connection
                    if (connection.check()) return statement;
                    else {
                        dropConnection(connection);
                        it.remove();
                    }
                }
            }
            else {
                it.remove();
            }
		}
		if (mCount == sMaxStatements) throw new SQLException("Error: Too many opened prepared statements!");
        connection = mConnectionPool.getConnection();
		statement= new PooledPreparedStatement(connection,connection.prepareStatement(inQuery,ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY));
		mStatementsMap.put(inQuery,statement);
        statement.notifyInUse();
		return statement;
	}

	// timeout and delay loop
	/** cycle through statements to check and recycle them
	 */
	public void run() {
		while (mRunning) {
			try {
				Thread.sleep(sCheckDelay);
			} catch (InterruptedException e) {}
			long now = System.currentTimeMillis();
			PooledPreparedStatement statement = null;
			for (Iterator it=mStatementsMap.keySet().iterator();it.hasNext();)
				for (Iterator jt=mStatementsMap.get(it.next()).iterator();jt.hasNext();) {
					statement = (PooledPreparedStatement)jt.next();
					if (statement.isInUse() && now-statement.getTagTime() > sTimeout)
						statement.notifyOver();
				}
		}
	}

	/** close all statements
	 */
    public void clear() {
        // close all statements
        for (Iterator it=mStatementsMap.keySet().iterator();it.hasNext();)
            for (Iterator jt=mStatementsMap.get(it.next()).iterator();jt.hasNext();)
                try {
                    ((PooledPreparedStatement)jt.next()).close();
                }
                catch (SQLException e) { // don't care now...
                    Logger.log(e);
                }
        mStatementsMap.clear();
    }

    /* drop all statements relative to a specific connection
     * @param connection the connection
     */
    protected void dropConnection(Connection connection) {
        for (Iterator it=mStatementsMap.keySet().iterator();it.hasNext();)
            for (Iterator jt=mStatementsMap.get(it.next()).iterator();jt.hasNext();) {
                    PooledPreparedStatement statement = (PooledPreparedStatement)jt.next();
                    try { statement.close(); } catch(SQLException sqle) {}
                    statement.setInvalid();
                }
        try { connection.close(); } catch(SQLException sqle) {}
    }


	/** clear statements on exit
	 */
	protected void finalize() {
        clear();
	}

	/** debug - get usage statistics
	 *
	 * @return an int array : [nb of statements in use , total nb of statements]
	 */
	protected int[] getUsageStats() {
		int [] stats = new int[] {0,0};
		for (Iterator it=mStatementsMap.keySet().iterator();it.hasNext();)
			for (Iterator jt=mStatementsMap.get(it.next()).iterator();jt.hasNext();)
				if (!((PooledPreparedStatement)jt.next()).isInUse())
					stats[0]++;
		stats[1]=mStatementsMap.size();
		return stats;
	}

    /** connection pool
     */
    protected ConnectionPool mConnectionPool;

	/** statements count
	 */
	protected int mCount = 0;
	/** map queries -> statements
	 */
	protected HashMultiMap mStatementsMap = new HashMultiMap(); // query -> PooledPreparedStatement
	/** running thread
	 */
	protected Thread mCheckTimeoutThread = null;
	/** true if running
	 */
	protected boolean mRunning = true;

	/** check delay
	 */
	protected static final long sCheckDelay = 30*1000;
	/** after this timeout, statements are recycled even if not closed
	 */
	protected static final long sTimeout = 60*60*1000;
	/** max number of statements
	 */
	protected static final int sMaxStatements = 50;
}
