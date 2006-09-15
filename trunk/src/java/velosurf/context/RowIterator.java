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
import java.sql.ResultSetMetaData;

import java.util.*;

import velosurf.context.Instance;
import velosurf.model.Attribute;
import velosurf.model.Entity;
import velosurf.sql.DataAccessor;
import velosurf.sql.Pooled;
import velosurf.util.Logger;
import velosurf.local.Localizer;

/** This class is a context wrapper for ResultSets, and provides an iteration mecanism for #foreach loops, as long as getters for values of the current row.
 *
 * @author Claude Brisson
 */
public class RowIterator implements Iterator,DataAccessor {

	/** Build a new RowIterator
	 *
	 * @param inPooledStatement the sql statement
	 * @param inResultSet the resultset
	 * @param inResultEntity the resulting entity (may be null)
	 */
	public RowIterator(Pooled inPooledStatement,ResultSet inResultSet,Entity inResultEntity) {
		mPooledStatement = inPooledStatement;
		mResultSet = inResultSet;
		mResultEntity = inResultEntity;
	}

	// iterator interface
	// WARNING : Big assumption here : hasNext() is called only once by loop iteration...
	// TODO: correct this!
	/** Returns true if the iteration has more elements.
	 *
	 * @return <code>true</code> if the iterator has more elements.
	 */
	public boolean hasNext() {
		try {
            mPooledStatement.getConnection().enterBusyState();
			boolean ret = (!mResultSet.isLast() && mResultSet.next());
            mPooledStatement.getConnection().leaveBusyState();
			if (!ret) mPooledStatement.notifyOver();
			return ret;
		} catch (SQLException e) {
			Logger.log(e);
			mPooledStatement.notifyOver();
			return false;
		}
	}

	/** Returns the next element in the iteration.<p>
	 *
	 * @return an Instance if a resulting entity has been specified, or a
	 *     reference to myself
	 */
	public Object next() {
		Instance row = null;
		if (mResultEntity != null) {
            row = mResultEntity.getInstance((DataAccessor)this);
            if (mLocalizer != null) row.setLocalizer(mLocalizer);
            return row;
        }
		else return this;
	}

	// for Iterator interface, but RO
	/** not implemented.
	 */
	public void remove() {
	}

	// generic getter
	/** generic getter for values of the current row. If no column corresponds to the specified name and a resulting entity has been specified, search among this entity's attributes.
     * Note that this method is the only getter of RowIterator that cares about obfuscation - other specialized getters
     * won't do any obfuscation.
	 *
	 * @param inKey the name of an existing column or attribute
	 * @return an entity, an attribute reference, an instance, a string or null
	 */
	public Object get(Object inKey) {
		String property = (String)inKey;
		Object result = null;
		boolean shouldNotifyOver = false;
		try {
			if (!dataAvailable()) return null;
			if (mResultEntity!=null) {
				Attribute attribute = mResultEntity.getAttribute(property);
				if (attribute != null)
						switch (attribute.getType()) {
							case Attribute.ROWSET:
								result = attribute.query(this);
                                if (mLocalizer != null) ((RowIterator)result).setLocalizer(mLocalizer);
								break;
							case Attribute.ROW:
								result = attribute.fetch(this);
                                if (mLocalizer != null) ((Instance)result).setLocalizer(mLocalizer);
								break;
							case Attribute.SCALAR:
								result = attribute.evaluate(this);
								break;
							default:
								Logger.error("Unknown attribute type for "+mResultEntity.getName()+"."+inKey+"!");
						}
			}
			if (result == null) {
                if (mResultEntity != null && mResultEntity.isObfuscated(property))
                    result = mResultEntity .obfuscate(mResultSet.getObject(property));
                else
                    result = mResultSet.getObject(property);
            }
		}
		catch (SQLException e) {
			Logger.log(e);
		}

		if (shouldNotifyOver) mPooledStatement.notifyOver();
		return result;
	}

	/** returns the value of a column specified by its order (starting at 1, as for ResultSet.get())
	 *
	 * @param inCol the index of the wanted column (starting at 1)
	 * @return the value of the specified column, as a String
	 */
	public Object get(int inCol) {
		try { return dataAvailable()?mResultSet.getObject(inCol):null;
		} catch (SQLException e) {	Logger.log(e);	return null; }
	}

	/** returns the value of a column specified by its name
	 *
	 * @param inKey the name of the wanted column
	 * @return the value of the specified column, as an int
	 */
	public int getInt(Object inKey) {
		try { return dataAvailable()?mResultSet.getInt((String)inKey):0;
		} catch (SQLException e) {	Logger.log(e);	return 0; }
	}

	// int getter by col num
	/** returns the value of a column specified by its order (starting at 1, as for ResultSet.get())
	 *
	 * @param inCol the index of the wanted column (starting at 1)
	 * @return the value of the specified column, as an int
	 */
	public int getInt(int inCol) {
		try { return dataAvailable()?mResultSet.getInt(inCol):0;
		} catch (SQLException e) {	Logger.log(e);	return 0; }
	}

    // string getter
	/** returns the value of a column specified by its name
	 *
	 * @param inKey the name of the wanted column
	 * @return the value of the specified column, as a string
	 */
    public String getString(Object inKey) {
        try { return dataAvailable()?mResultSet.getString((String)inKey):null;
        } catch (SQLException e) {    Logger.log(e);    return null; }
    }

    // string getter by col num
	/** returns the value of a column specified by its order (starting at 1, as for ResultSet.get())
	 *
	 * @param inCol the index of the wanted column (starting at 1)
	 * @return the value of the specified column, as a string
	 */
    public String getString(int inCol) {
        try { return dataAvailable()?mResultSet.getString(inCol):null;
        } catch (SQLException e) {    Logger.log(e);    return null; }
    }

    /** gets all the rows in a list of maps
     *
     * @return a list of all the rows
     */
    public List getRows() throws SQLException {
        List ret = new ArrayList();
        mPooledStatement.getConnection().enterBusyState();
        ResultSetMetaData meta = mResultSet.getMetaData();
        int cols = meta.getColumnCount();
        boolean hasNext = false;
        while (!mResultSet.isAfterLast() && (hasNext = (mResultSet.next()))) {
            Instance i = mResultEntity.getInstance((DataAccessor)this);
            if (mResultEntity.hasLocalizedColumns() && mLocalizer != null) i.setLocalizer(mLocalizer);
            ret.add(i);
        }
        mPooledStatement.getConnection().leaveBusyState();
        return ret;
    }

	/** check if some data is available
	 *
	 * @exception SQLException if the internal ResultSet is not happy
	 * @return <code>true</code> if some data is available (ie the internal
	 *     ResultSet is not empty, and not before first row neither after last
	 *     one)
	 */
	protected boolean dataAvailable() throws SQLException {
		if (mResultSet.isBeforeFirst()) {
            mPooledStatement.getConnection().enterBusyState();
            boolean hasNext = mResultSet.next();
            mPooledStatement.getConnection().leaveBusyState();
			if (!hasNext) {
				mPooledStatement.notifyOver();
				return false;
			}
//			mPooledStatement.notifyOver();
		}
		if (mResultSet.isAfterLast()) {
			mPooledStatement.notifyOver();
			return false;
		}
		return true;
	}

    /** set the localizer to be used to build instances
     *
     */
    public void setLocalizer(Localizer localizer) {
        mLocalizer = localizer;
    }

	/** the statement
	 */
	protected Pooled mPooledStatement = null;
	/** the result set
	 */
	protected ResultSet mResultSet = null;
	/** the resulting entity
	 */
	protected Entity mResultEntity = null;

	/** Column names in sequential order
	 */
    protected List mColumnNames = null;

    /** localizer to be used to construct instances
     *
     */
    protected Localizer mLocalizer = null;
}
