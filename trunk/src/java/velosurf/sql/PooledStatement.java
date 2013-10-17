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
import java.util.List;

//CB TODO useOver is deprecated - update doc

/**
 * This abstract class represents a pooled object.<p>
 * It has two booleans : inUse and useOver (understand : usageOver).<p>
 * The cycle of those two booleans is the following :<p>
 * states (inUse - useOver) : (false-false) -> (true-false) -> (true-true) -> [delay] (false-false)
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 */
public abstract class PooledStatement implements RowHandler, Serializable
{
    /**
     * build a new pooled object.
     */
    public PooledStatement()
    {
        tagTime = System.currentTimeMillis();
    }

    /**
     * get the time tag of this pooled object.
     *
     * @return the time tag
     */
    public long getTagTime()
    {
        return tagTime;
    }

    /**
     * reset the time tag.
     */
    public void resetTagTime()
    {
        tagTime = System.currentTimeMillis();
    }

    /**
     * notify this object that it is in use.
     */
    public void notifyInUse()
    {
        inUse = true;
        resetTagTime();
    }

    /**
     * notify this object that it is no more in use.
     */
    public void notifyOver()
    {
        try
        {
            if(resultSet != null &&!resultSet.isClosed())
            {
                resultSet.close();
                resultSet = null;
            }
        }
        catch(SQLException sqle) {}    // ignore
        resultSet = null;
        inUse = false;
    }

    /**
     * check whether this pooled object is in use.
     *
     * @return whether this object is in use
     */
    public boolean isInUse()
    {
        return inUse;
    }

    /**
     * check whether this pooled object is marked as valid or invalid.
     * (used in the recovery process)
     *
     * @return whether this object is in use
     */
    public boolean isValid()
    {
        return valid;
    }

    /**
     * definitely mark this statement as meant to be deleted.
     */
    public void setInvalid()
    {
        valid = false;
    }

    /**
     * get the connection used by this statement.
     *
     * @return the connection used by this statement
     */
    public abstract ConnectionWrapper getConnection();

    /**
     * close this pooled object.
     *
     * @exception SQLException when thrown by the database engine
     */
    public abstract void close() throws SQLException;

    /**
     * time tag.
     */
    private long tagTime = 0;

    // states (inUse - useOver) : (false-false) -> (true-false) -> (true-true) -> [delay] (false-false)

    /**
     * valid statement?
     */
    private boolean valid = true;

    /**
     * is this object in use?
     */
    private boolean inUse = false;

    /**
     * database connection.
     */
    protected ConnectionWrapper connection = null;

    /**
     * result set.
     */
    protected transient ResultSet resultSet = null;

    /**
     * column names in natural order.
     */
    protected List<String> columnNames = null;
}
