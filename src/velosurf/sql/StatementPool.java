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

import java.util.*;

import velosurf.util.Logger;

/** This class is a pool of PooledStatements
 */
public class StatementPool implements Runnable,Pool {

	/** builds a new pool
	 *
	 * @param inConnectionPool connection pool
	 */
	public StatementPool(ConnectionPool inConnectionPool) {
		mConnectionPool = inConnectionPool;
		mCheckTimeoutThread = new Thread(this);
//		mCheckTimeoutThread.start();
	}

	/** gets a valid statement
	 *
	 * @exception SQLException thrown by the database engine
	 * @return a valid statement
	 */
	public synchronized PooledStatement getStatement() throws SQLException {
		PooledStatement statement = null;
        ConnectionWrapper connection = null;
		for (Iterator it=mStatements.iterator();it.hasNext();) {
            statement = (PooledStatement)it.next();
			if (statement.isValid()) {
                if (!statement.isInUse() && !(connection = (ConnectionWrapper)statement.getConnection()).isBusy()) {
                    // check connection
                    if (connection.check()) {
                        return statement;
                    }
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
		if (mCount == sMaxStatements) throw new SQLException("Error: Too many opened statements!");
        connection = mConnectionPool.getConnection();
		statement = new PooledStatement(connection,connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY));
		mStatements.add(statement);
        statement.notifyInUse();
		return statement;
	}

	// timeout loop
	/** run the loop of statements checking and recycling
	 */
	public void run() {
		while (mRunning) {
			try {
				Thread.sleep(sCheckDelay);
			} catch (InterruptedException e) {}
			long now = System.currentTimeMillis();
			PooledStatement statement = null;
			for (Iterator it=mStatements.iterator();it.hasNext();) {
				statement = (PooledStatement)it.next();
				if (statement.isInUse() && now-statement.getTagTime() > sTimeout)
					statement.notifyOver();
			}
		}
	}

	/** debug - two ints long array containing nb of statements in use and total nb of statements
	 *
	 * @return 2 integers long array
	 */
	public int[] getUsageStats() {
		int [] stats = new int[] {0,0};
		for (Iterator it=mStatements.iterator();it.hasNext();)
			if (!((PooledStatement)it.next()).isInUse())
				stats[0]++;
		stats[1]=mStatements.size();
		return stats;
	}

	/** close all statements
	 */
    public void clear() {
        // close all statements
        for (Iterator it=mStatements.iterator();it.hasNext();)
            try {
                ((PooledStatement)it.next()).close();
            }
            catch (SQLException sqle) { // don't care now...
                Logger.log(sqle);
            }
        mStatements.clear();
	}

    /* drop all statements relative to a specific connection
     * @param connection the connection
     */
    protected void dropConnection(Connection connection) {
        for (Iterator it=mStatements.iterator();it.hasNext();) {
            PooledStatement statement = (PooledStatement)it.next();
            try { statement.close(); } catch(SQLException sqle) {}
            statement.setInvalid();
        }
        try { connection.close(); } catch(SQLException sqle) {}
    }

	/** close statements on exit
	 */
    protected void finalize() {
        clear();
    }

	/** Connection pool
	 */
	protected ConnectionPool mConnectionPool = null;

	/** number of statements
	 */
	protected int mCount = 0;
	/** statements
	 */
	protected List mStatements = new ArrayList();
	/** timeout checking thread
	 */
	protected Thread mCheckTimeoutThread = null;
	/** is the thread running ?
	 */
	protected boolean mRunning = true;

	/** delay between checks
	 */
	protected static final long sCheckDelay = 30*1000;
	/** timeout on which statements are automatically recycled if not used
	 */
	protected static final long sTimeout = 10*60*1000;
	/** maximum number of statements
	 */
	protected static final int sMaxStatements = 50;
}
