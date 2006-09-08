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

import java.sql.SQLException;
import java.util.*;

import velosurf.model.Action;
import velosurf.model.Attribute;
import velosurf.model.Entity;
import velosurf.sql.Database;
import velosurf.sql.DataAccessor;
import velosurf.sql.PooledPreparedStatement;
import velosurf.util.Logger;
import velosurf.util.StringLists;
import velosurf.tools.Localizer;

/** An Instance provides field values by their name.
 *
 * @author Claude Brisson
 */
public class Instance extends TreeMap implements DataAccessor
{
	/** Build an empty instance for the given entity
	 *
	 * @param inEntity Entity this instance is a realisation of
     * @deprecated As of Velosurf 1.4, please use the default constructor and call initialize(Entity) thereafter.
	 */
	public Instance(Entity inEntity) {
        initialize(inEntity);
	}

    /** Build an empty instance that must be latter initialized via the initialize(Entity) method.
     *
     */
    public Instance() {
    }


	 /** Meant to be overloaded if needed
	  */
	 public void initialize(Entity inEntity) {
         mEntity = inEntity;
         mDB = mEntity.getDB();
	 }

    /** Get this Instance's Entity.
	 *
	 * @return this Instance's Entity.
	 */
	public EntityReference getEntity() {
        return new EntityReference(mEntity,mLocalizer.get());
	}

    /** Get the name of the table mapped by this Instance's Entity.
     *
     * @return The name of the table mapped by this Instance's Entity.
     */
    protected String getTable() {
        if (mEntity != null) return mEntity.getTableName();
        return null;
    }

	/** @deprecated As of Velosurf version 0.9, replaced by getPrimaryKey
     * Returns an ArrayList of two-entries maps ('name' & 'value'), meant to be use in a #foreach loop to build form fields, like:<p>
	 * <code>
	 * #foreach ($field in $product.keys)<p>
	 * &nbsp;&nbsp;&lt;input type=hidden name='$field.name' value='$field.value'&gt;
	 * #end</code><p>
	 *
	 * @return an ArrayList of two-entries maps ('name' & 'value')
	 */
	public List getKeys() {
        return getPrimaryKey();
	}

    /** Returns an ArrayList of two-entries maps ('name' & 'value'), meant to be use in a #foreach loop to build form fields, like:<p>
     * <code>
     * #foreach ($field in $product.primaryKey)<p>
     * &nbsp;&nbsp;&lt;input type=hidden name='$field.name' value='$field.value'&gt;
     * #end</code><p>
     *
     * @return an ArrayList of two-entries maps ('name' & 'value')
     */
    public List getPrimaryKey() {
        List result = new ArrayList();
        if (mEntity!=null) {
            for (Iterator i=mEntity.getKeys().iterator();i.hasNext();) {
                String key = (String)i.next();
                HashMap map = new HashMap();
                map.put("name",key);
                map.put("value",getInternal(key));
                result.add(map);
            }
        }
        return result;
    }

	/** Generic getter, used to access this instance properties by their name.<p>
	 * Asked property is first searched in the Map, then among Attributes defined for the entity.
	 *
	 * @param inKey key of the property to be returned
	 * @return a String, an Instance, an AttributeReference or null if an error
	 *      occurs
	 */
	public Object get(Object inKey) {
        String property = mDB.adaptCase((String)inKey);
		Object result = null;
		try {
			result = super.get(property);
			if (result == null) {
				if (mEntity!=null) {
                    Attribute attribute = mEntity.getAttribute(property);
                    if (attribute != null)
                        switch (attribute.getType()) {
                            case Attribute.ROWSET:
                                result = new AttributeReference(this,attribute);
                                // then cache it in the map, so that order and refinement will work later in the same context
                                super.put(inKey,result);
                                break;
                            case Attribute.ROW:
                                result = attribute.fetch(this);
                                break;
                            case Attribute.SCALAR:
                                result = attribute.evaluate(this);
                                break;
                            default:
                                Logger.error("Unknown attribute type for "+mEntity.getName()+"."+inKey+"!");
                        }
                    else {
                    	Action action = mEntity.getAction(property);
                    	if (action != null) result = new Integer(action.perform(this));
                    }
				}
			} else if (mLocalize && mEntity.isLocalized((String)inKey)) {

            }
		}
		catch (SQLException e) {
			mDB.setError(e.getMessage());
			Logger.log(e);
		}
		return result;
	}

    /** Generic setter<p>
     *
     * @param key key of the property to be set
     * @param value corresponding value
     * @return previous value, or null
     */
    public synchronized Object put(Object key, Object value)
    {
        String property = mDB.adaptCase((String)key);
        return super.put(property,value);
    }

    /** Internal getter: first tries on the external object then on the Map interface
     *
     * @param inKey key of the property to be returned
     * @return a String, an Instance, an AttributeReference or null if not found or if an error
     *      occurs
     */
    public Object getInternal(Object inKey) {
        Object ret = getExternal(inKey);
        if (ret == null) ret = super.get(inKey);
        return ret;
    }

    /** External getter: meant to be overloaded in ExternalObjectWrapper
     *
     * @param inKey key of the property to be returned
     * @return a String, an Instance, an AttributeReference or null if not found or if an error
     *      occurs
     */
    public Object getExternal(Object inKey) {
        return null;
    }

    public boolean equals(Object o) {
        if (o != null && o.getClass() == getClass()) {
            // compare keys
            List keys = mEntity.getKeys();
            Instance other = (Instance)o;
            if (keys.size() > 0) {
                for(Iterator i = keys.iterator();i.hasNext();) {
                    if (getInternal(i.next()) != other.getInternal(i.next()))
                        return false;
                }
                return true;
            }
        }
        return super.equals(o);
    }

	/** Update the row associated with this Instance from passed values<p>
	 * Velosurf will ensure all key columns are specified, to avoid an accidental massive update.
	 *
	 * @return <code>true</code> if successfull, <code>false</code> if an error
	 *     occurs (in which case $db.lastError can be checked).
	 */
	public synchronized boolean update() {
		return update(null);
	}

	// update from passed values
	/** Update the row associated with this Instance from actual values<p>
	 * Velosurf will ensure all key columns are specified, to avoid an accidental massive update.
	 *
	 * @param inValues values to be used for the update
	 * @return <code>true</code> if successfull, <code>false</code> if an error
	 *      occurs (in which case $db.lastError can be checked).
	 */
	public synchronized boolean update(Map inValues) {
		try {
			Map values = new HashMap();
            for(Iterator it = keySet().iterator();it.hasNext();) {
                String key = (String)it.next();
                values.put(mDB.adaptCase(key),getInternal(key));
            }
            if (inValues != null && inValues != this) {
                for(Iterator it = inValues.keySet().iterator();it.hasNext();) {
                    String key = (String)it.next();
                    values.put(mDB.adaptCase(key),inValues.get(key));
                }
            }
			if (mEntity == null) throw new SQLException("Entity is null!");
			List updateClause = new ArrayList();
			List whereClause = new ArrayList();
			List params = new ArrayList();
			ArrayList cols = new ArrayList(mEntity.getColumns());
			cols.removeAll(mEntity.getKeys());
			for (Iterator i=cols.iterator();i.hasNext();) {
				String col = (String)i.next();
				Object value = values.get(col);
				if (value!=null) {
					updateClause.add(col+"=?");
                    if (mEntity.isObfuscated(col)) value = mEntity.deobfuscate(value);
					params.add(value);
				}
			}
			for (Iterator i = mEntity.getKeys().iterator();i.hasNext();) {
				String col = (String)i.next();
				Object value = values.get(col);
				if (value == null) throw new SQLException("field '"+col+"' belongs to primary key and cannot be null!");
                if (mEntity.isObfuscated(col)) value = mEntity.deobfuscate(value);
//                if (mEntity.isLocalized(col)) value = mEntity.unlocalize(value);
				whereClause.add(col+"=?");
				params.add(value);
			}
			String query = "update "+getTable()+" set "+StringLists.join(updateClause,",")+" where "+StringLists.join(whereClause," and ");
			PooledPreparedStatement statement = mDB.prepare(query);
			int nb = statement.update(params);
			if (nb==0) {
				Logger.warn("query \""+query+"\" affected 0 row...");
			}
			else if (nb>1) // ?!?! Referential integrities on key columns should avoid this...
				throw new SQLException("query \""+query+"\" affected more than 1 rows!");
			return true;
		}
		catch (SQLException sqle) {
			mDB.setError(sqle.getMessage());
			Logger.log(sqle);
			return false;
		}
	}

	/** Delete the row associated with this Instance.<p>
	 * Velosurf will ensure all key columns are specified, to avoid an accidental massive update.
	 *
	 * @return <code>true</code> if successfull, <code>false</code> if an error
	 *     occurs (in which case $db.lastError can be checked).
	 */
	public synchronized boolean delete() {
		try {
			if (mEntity == null) throw new SQLException("Instance.delete: Error: Entity is null!");
			List whereClause = new ArrayList();
			List params = new ArrayList();
			for (Iterator i = mEntity.getKeys().iterator();i.hasNext();) {
				String col = (String)i.next();
				Object value = getInternal(col);
				if (value == null) throw new SQLException("Instance.delete: Error: field '"+col+"' belongs to primary key and cannot be null!");
                if (mEntity.isObfuscated(col)) value = mEntity.deobfuscate(value);
				whereClause.add(col+"=?");
				params.add(value);
			}
			String query = "delete from "+getTable()+" where "+StringLists.join(whereClause," and ");
			PooledPreparedStatement statement = mDB.prepare(query);
			int nb = statement.update(params);
			if (nb==0) {
				Logger.warn("query \""+query+"\" affected 0 row...");
			}
			else if (nb>1) // ?!?! Referential integrities on key columns should avoid this...
				throw new SQLException("query \""+query+"\" affected more than 1 rows!");
			return true;
		}
		catch (SQLException sqle) {
			mDB.setError(sqle.getMessage());
			Logger.log(sqle);
			return false;
		}
	}

	/** Insert a new row corresponding to this Instance.
	 *
	 * @return <code>true</code> if successfull, <code>false</code> if an error
	 *     occurs (in which case $db.lastError can be checked).
	 */
	public synchronized boolean insert() {
		try {
			if (mEntity == null) throw new SQLException("Instance.insert: Error: Entity is null!");
			List colsClause = new ArrayList();
			List valsClause = new ArrayList();
			List params = new ArrayList();
			List cols = mEntity.getColumns();
			for (Iterator i=cols.iterator();i.hasNext();) {
				String col = (String)i.next();
				Object value = getInternal(col);
				if (value!=null) {
					colsClause.add(col);
					valsClause.add("?");
                    if (mEntity.isObfuscated(col)) value = mEntity.deobfuscate(value);
					params.add(value);
				}
			}
			String query = "insert into "+getTable()+" ("+StringLists.join(colsClause,",")+") values ("+StringLists.join(valsClause,",")+")";
			PooledPreparedStatement statement = mDB.prepare(query);
			statement.update(params);
            List keys = mEntity.getKeys();
            if (keys.size() == 1) {
                // is this call valid if the id is not autoincremented ?
                mEntity.setLastInsertID(statement.getLastInsertID());
                //... how to check for autoincrements ? => metadata!
            }
			return true;
		}
		catch (SQLException sqle) {
			mDB.setError(sqle.getMessage());
			Logger.log(sqle);
			return false;
		}
	}

    /** set this instance localizer (thread local)
     *
     */

    public void setLocalizer(Localizer localizer) {
        if (!mEntity.hasLocalizedColumns()) return;
        mLocalize = true;
        mLocalizer.set(localizer);
    }

    /** this Instance's Entity
	 */
	protected Entity mEntity = null;
	/** the main database connection
	 */
	protected Database mDB = null;

    /** whether to localize a column
     *
     */
    protected boolean mLocalize = false;
    /** the localizer object to be used to resolve localizer columns (thread local)
     *
     */
    protected ThreadLocal<Localizer> mLocalizer = new ThreadLocal<Localizer>();
}
