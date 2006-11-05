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

package velosurf.context;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import velosurf.model.Attribute;
import velosurf.model.Entity;
import velosurf.sql.ReadOnlyMap;
import velosurf.sql.Pooled;
import velosurf.sql.SqlUtil;
import velosurf.util.Logger;
import velosurf.util.UserContext;

/** This class is a context wrapper for ResultSets, and provides an iteration mecanism for #foreach loops, as long as getters for values of the current row.
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 */
public class RowIterator implements Iterator,ReadOnlyMap {

    /** Build a new RowIterator
     *
     * @param pooledStatement the sql statement
     * @param resultSet the resultset
     * @param resultEntity the resulting entity (may be null)
     */
    public RowIterator(Pooled pooledStatement,ResultSet resultSet,Entity resultEntity) {
        this.pooledStatement = pooledStatement;
        this.resultSet = resultSet;
        this.resultEntity = resultEntity;
    }

    /** Returns true if the iteration has more elements.
     *
     * @return <code>true</code> if the iterator has more elements.
     */
    public boolean hasNext() {
        try {
            pooledStatement.getConnection().enterBusyState();
            boolean ret = !resultSet.isLast();
            pooledStatement.getConnection().leaveBusyState();
            if (!ret) pooledStatement.notifyOver();
            return ret;
        } catch (SQLException e) {
            Logger.log(e);
            pooledStatement.notifyOver();
            return false;
        }
    }

    /** Returns the next element in the iteration.<p>
     *
     * @return an Instance if a resulting entity has been specified, or a
     *     reference to myself
     */
    public Object next() {
        try {
            if(!resultSet.next()) {
                return null;
            }
            if (resultEntity != null) {
                Instance row = null;
                row = resultEntity.getInstance((ReadOnlyMap)this);
                if (userContext != null) {
                    row.setUserContext(userContext);
                }
                return row;
            }
            else return new Instance(this);
        } catch(SQLException sqle) {
            Logger.log(sqle);
            pooledStatement.notifyOver();
            return null;
        }
    }

    // for Iterator interface, but RO (why? -> positionned updates and deletes => TODO)
    /** not implemented.
     */
    public void remove() {
        Logger.warn("'remove' not implemented");
    }

    // generic getter
    /** generic getter for values of the current row. If no column corresponds to the specified name and a resulting entity has been specified, search among this entity's attributes.
     * Note that this method is the only getter of RowIterator that cares about obfuscation - other specialized getters
     * won't do any obfuscation.
     *
     * @param key the name of an existing column or attribute
     * @return an entity, an attribute reference, an instance, a string or null
     */
    public Object get(Object key) {
        String property = (String)key;
        Object result = null;
        boolean shouldNotifyOver = false;
        try {
            if (!dataAvailable()) return null;
            if (resultEntity!=null) {
                property = resultEntity.aliasToColumn(property);
                Attribute attribute = resultEntity.getAttribute(property);
                if (attribute != null)
                        switch (attribute.getType()) {
                            case Attribute.ROWSET:
                                result = attribute.query(this);
                                if (result != null && userContext != null && result instanceof RowIterator) {
                                    ((RowIterator)result).setUserContext(userContext);
                                }
                                break;
                            case Attribute.ROW:
                                result = attribute.fetch(this);
                                if (result != null && userContext != null && result instanceof Instance) {
                                    ((Instance)result).setUserContext(userContext);
                                }
                                break;
                            case Attribute.SCALAR:
                                result = attribute.evaluate(this);
                                break;
                            default:
                                Logger.error("Unknown attribute type for "+resultEntity.getName()+"."+property+"!");
                        }
            }
            if (result == null) {
                if (resultEntity != null && resultEntity.isObfuscated(property))
                    result = resultEntity .obfuscate(resultSet.getObject(property));
                else
                    result = resultSet.getObject(property);
            }
        }
        catch (SQLException e) {
            Logger.log(e);
        }

        if (shouldNotifyOver) pooledStatement.notifyOver();
        return result;
    }

    /** gets all the rows in a list of maps
     *
     * @return a list of all the rows
     */
    public List getRows() {
        try {
            List ret = new ArrayList();
            pooledStatement.getConnection().enterBusyState();
            if(resultEntity != null) {
                while (!resultSet.isAfterLast() && resultSet.next()) {
                    Instance i = resultEntity.getInstance((ReadOnlyMap)this);
                    if (/* i != null && */userContext != null) {
                        i.setUserContext(userContext);
                    }
                    ret.add(i);
                }
            } else {
                while (!resultSet.isAfterLast() && resultSet.next()) {
                    Instance i = new Instance(this);
                    if (userContext != null) {
                        i.setUserContext(userContext);
                    }
                    ret.add(i);
                }
            }
            pooledStatement.getConnection().leaveBusyState();
            return ret;
        } catch(SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }

    public Set keySet() {
        try {
            return  new HashSet(SqlUtil.getColumnNames(resultSet));
        } catch(SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }

    /** check if some data is available
     *
     * @exception SQLException if the internal ResultSet is not happy
     * @return <code>true</code> if some data is available (ie the internal
     *     ResultSet is not empty, and not before first row neither after last
     *     one)
     */
    protected boolean dataAvailable() throws SQLException {
        if (resultSet.isBeforeFirst()) {
            pooledStatement.getConnection().enterBusyState();
            boolean hasNext = resultSet.next();
            pooledStatement.getConnection().leaveBusyState();
            if (!hasNext) {
                pooledStatement.notifyOver();
                return false;
            }
//            pooledStatement.notifyOver();
        }
        if (resultSet.isAfterLast()) {
            pooledStatement.notifyOver();
            return false;
        }
        return true;
    }

    /** set the localizer to be used to build instances
     *
     */
    public void setUserContext(UserContext context) {
        userContext = context;
    }

    /** the statement
     */
    protected Pooled pooledStatement = null;
    /** the result set
     */
    protected ResultSet resultSet = null;
    /** the resulting entity
     */
    protected Entity resultEntity = null;

    /** user context
     */
    protected UserContext userContext = null;
}
