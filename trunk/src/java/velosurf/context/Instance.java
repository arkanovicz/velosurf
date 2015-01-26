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

import java.io.Serializable;
import java.sql.SQLException;
import java.util.*;
import velosurf.model.Action;
import velosurf.model.Attribute;
import velosurf.model.Entity;
import velosurf.sql.Database;
import velosurf.sql.PooledPreparedStatement;
import velosurf.util.Logger;
import velosurf.util.SlotHashMap;
import velosurf.util.SlotMap;
import velosurf.util.SlotTreeMap;
import velosurf.util.StringLists;
import velosurf.util.UserContext;

/**
 * An Instance provides field values by their name.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 */
public class Instance extends SlotTreeMap implements HasParametrizedGetter
{

    /**
     * Build an empty instance for the given entity.
     *  The method initialize(Entity) should be called afterwards.
     */
    public Instance()
    {
    }

    /**
     * Build an empty instance for the given entity.
     * This is the only constructor that will keep columns in their natural order
     * (others will sort columns alphabetically)
     *
     * @param entity Entity this instance is a realisation of
     */
    public Instance(Entity entity)
    {
        super(entity.getColumnOrderComparator());
        initialize(entity);
    }

    /**
     * Builds a generic instance using <code>values</code>.
     * @param values
     */
    public Instance(SlotMap values)
    {
        this(values,null);
    }

    /**
     * Builds a generic instance using <code>values</code>.
     * @param values
     * @param db
     */
      public Instance(SlotMap values, Database db)
      {
          this.db = db;
          for(Serializable key:values.keySet())
          {
              put(Database.adaptContextCase((String)key),values.get(key));
          }
      }

     /**
      * Initialization. Meant to be overloaded if needed.
      * @param entity
      */
     public void initialize(Entity entity)
     {
         this.entity = entity;
         db = this.entity.getDB();
         localized = this.entity.hasLocalizedColumns();
         dirtyFlags = new ArrayList();
         for(int i=0; i<entity.getUpdatableColumns().size();i++)
         {
             dirtyFlags.add(false);
         }
     }

    /**
     * Get this Instance's Entity.
     *
     * @return this Instance's Entity.
     */
    public EntityReference getEntity()
    {
        return new EntityReference(entity);
    }

    /**
     * <p>Returns an ArrayList of two-entries maps ('name' & 'value'), meant to be use in a #foreach loop to build form fields.</p>
     * <p>Example:</p>
     * <code>
     * #foreach ($field in $product.primaryKey)<br>
     * &nbsp;&nbsp;&lt;input type=hidden name='$field.name' value='$field.value'&gt;<br>
     * #end</code>
     * <p>Please note that this method won't be of any help if you are using column aliases.</p>
     *
     * @return an ArrayList of two-entries maps ('name' & 'value')
     */
    public List getPrimaryKey()
    {
        List<SlotMap> result = new ArrayList<SlotMap>();
        if (entity!=null)
        {
            for (Iterator i=entity.getPKCols().iterator();i.hasNext();)
            {
                String key = (String)i.next();
                SlotMap map = new SlotTreeMap();
                map.put("name",key);
                map.put("value",getInternal(key));
                result.add(map);
            }
        }
        return result;
    }

    /**
     * <p>Generic getter, used to access this instance properties by their name.</p>
     * <p>Asked property is first searched in the Map, then among Attributes defined for the entity.</p>
     *
     * @param k key of the property to be returned
     * @return a String, an Instance, an AttributeReference or null if an error
     *      occurs
     */
    public Serializable get(Object k)
    {
        String key = resolveName((String)k);
        Serializable result = null;
        try
        {
            result = super.get(key);
            if (result == null)
            {
                if (entity!=null)
                {
                    Attribute attribute = entity.getAttribute(key);
                    if (attribute != null)
                    {
                        switch (attribute.getType())
                        {
                            case Attribute.ROWSET:
                                result = new AttributeReference(this,attribute);
                                // then cache it in the map, so that order and refinement will work later in the same context
                                super.put(key,result);
                                break;
                            case Attribute.ROW:
                                result = attribute.fetch(this);
                                if(attribute.getCaching())
                                {
                                  super.put(key,result);
                                }
                                break;
                            case Attribute.SCALAR:
                                result = attribute.evaluate(this);
                                if(attribute.getCaching())
                                {
                                  super.put(key,result);
                                }
                                break;
                            default:
                                Logger.error("Unknown attribute type for "+entity.getName()+"."+key+"!");
                        }
                    }
                    else
                    {
                        Action action = entity.getAction(key);
                        if (action != null)
                        {
                            result = action.perform(this);
                        }
                    }
                }
            }
            else if (localized && entity.isLocalized(key))
            {
                result = db.getUserContext().localize(result.toString());
            }
        }
        catch (SQLException sqle)
        {
            handleSQLException(sqle);
        }
        return result;
    }

    /**
     * Default method handler, called by Velocity when it did not find the specified method.
     *
     * @param key asked key
     * @param params passed parameters
     * @see HasParametrizedGetter
     */
	public Serializable getWithParams(String key, SlotMap params) // CB TODO - instance should not be modified by params!!!!!
    {
        for(Map.Entry<String,Serializable> entry: (Set<Map.Entry<String,Serializable>>)params.entrySet())
        {
            put(db.adaptCase(entry.getKey()),entry.getValue());
        }
        return get(db.adaptCase(key));
    }

    /**
     * Default method handler, called by Velocity when it did not find the specified method.
     *
     * @param key asked key
     * @param params passed parameters
     * @see HasParametrizedGetter
     * @deprecated
     */
    @Deprecated
    public Serializable getWithParams(String key, Map params)
    {
        SlotMap p = new SlotHashMap();
        for(Map.Entry entry: (Set<Map.Entry>)params.entrySet())
        {
            p.put((String)entry.getKey(), (Serializable)entry.getValue());
        }
        return getWithParams(key, p);
    }

    /**
     * Generic setter.
     *
     * @param key key of the property to be set
     * @param value corresponding value
     * @return previous value, or null
     */
    public synchronized Serializable put(String key, Serializable value)
    {
        key = resolveName(key);
        int index;
        if (entity != null)
        {
            value = entity.filterIncomingValue(key,value);
            if ( (index = entity.getUpdatableColumnIndex(key) ) != -1)
            {
                dirtyFlags.set(index,true);
            }
        }
        return super.put(key,value);
    }

    public synchronized boolean isDirty()
    {
        if(dirtyFlags != null)
        {
            for(int i=0; i<dirtyFlags.size(); i++)
            {
						    if (dirtyFlags.get(i)) return false;
            }
        }
				return true;
    }

    public synchronized void setClean()
    {
        if(dirtyFlags != null)
        {
            for(int i=0;i<dirtyFlags.size();i++)
            {
                dirtyFlags.set(i,false);
            }
        }
    }

    /**
     * Global setter that will only set values the correspond to actual
     * columns (otherwise, use putAll(Map values)).
     *
     * @param values corresponding values
     */

    public synchronized void setColumnValues(SlotMap values)
    {
        if(entity == null)
        {
            Logger.warn("instance.setColumnValues(map) cannot be used when entity is null");
            return;
        }
        int index;
        for(Map.Entry<String,Serializable> entry:values.entrySet())
        {
            if( entity.isColumn(entity.resolveName(entry.getKey())))
            {
                put(entry.getKey(),entry.getValue());
            }
        }
    }

    /**
     * Global setter that will only set values the correspond to actual
     * columns (otherwise, use putAll(Map values)).
     *
     * @param values corresponding values
     * @deprecated
     */
    @Deprecated
    public synchronized void setColumnValues(Map<String,Object> values)
    {
        SlotMap v = new SlotHashMap();
        for(Map.Entry<String,Object> entry: (Set<Map.Entry<String,Object>>)values.entrySet())
        {
            v.put(entry.getKey(), (Serializable)entry.getValue());
        }
        setColumnValues(v);
    }

    /**
     * Internal getter. First tries on the external object then on the Map interface.
     *
     * @param key key of the property to be returned
     * @return a String, an Instance, an AttributeReference or null if not found or if an error
     *      occurs
     */
    public Serializable getInternal(Object key)
    {
        Serializable ret = getExternal(key);
        if (ret == null) ret = super.get(key);
        return ret;
    }

    /**
     * External getter. Meant to be overloaded in ExternalObjectWrapper.
     *
     * @param key key of the property to be returned
     * @return a String, an Instance, an AttributeReference or null if not found or if an error
     *      occurs
     */
    public Serializable getExternal(Object key)
    {
        return null;
    }

    /**
     * Test equality of two instances.
     * @param o other instance
     * @return equality status
     */
    public boolean equals(Object o)
    {
        return super.equals(o);
    }

    /**
     * <p>Update the row associated with this Instance from passed values.</p>
     * <p>Velosurf will ensure all key columns are specified, to avoid an accidental massive update.</p>
     *
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *     occurs (in which case $db.error can be checked).
     */
    public synchronized boolean update()
    {
        try
        {
            if (entity == null)
            {
                throw new SQLException("Cannot update an instance whose Entity is null.");
            }
            if (entity.isReadOnly())
            {
                throw new SQLException("Entity "+entity.getName()+" is read-only.");
            }
            
            List<String> updateClause = new ArrayList<String>();
            List<String> whereClause = new ArrayList<String>();
            List<Object> params = new ArrayList<Object>();
            List<String> cols = entity.getUpdatableColumns();
            for (int c = 0; c < cols.size(); c++)
            {
                String col = cols.get(c);
                if(dirtyFlags.get(c))
                {
                    Object value = getInternal(col);
                    if (value!=null)
                    {
                        updateClause.add(col+"=?");
                        if (entity.isObfuscated(col))
                        {
                            value = entity.deobfuscate(value);
                        }
                        params.add(value);
                    } // TODO else " = null " ? May be a configuration option.
                }
            }
            if(updateClause.size() ==0)
            {
                Logger.warn("update of instance '"+entity.getName()+"' all non-key columns are null or non-dirty - no update will be performed");
                // return true anyway ?
                return true;
            }
            for (String col:entity.getPKCols())
            {
                Object value = getInternal(col);
                if (value == null) throw new SQLException("field '"+col+"' belongs to primary key and cannot be null!");
                if (entity.isObfuscated(col)) value = entity.deobfuscate(value);
//                if (entity.isLocalized(col)) value = entity.unlocalize(value); ???
                whereClause.add(col+"=?");
                params.add(value);
            }
            String query = "update "+entity.getTableName()+" set "+StringLists.join(updateClause,",")+" where "+StringLists.join(whereClause," and ");
            PooledPreparedStatement statement = db.prepare(query);
            int nb = statement.update(params);
            if (nb==0)
            {
                Logger.warn("query \""+query+"\" affected 0 row...");
            }
            else if (nb>1)
            { // ?!?! Referential integrities on key columns should avoid this...
                throw new SQLException("query \""+query+"\" affected more than 1 rows!");
            }
            else
            {
                /* invalidate cache */
                if (entity != null)
                {
                    entity.invalidateInstance(this);
                }
            }
            setClean();
            return true;
        }
        catch (SQLException sqle)
        {
            handleSQLException(sqle);
            return false;
        }
    }

    /**
     * <p>Update the row associated with this Instance from actual values.</p>
     * <p>Velosurf will ensure all key columns are specified, to avoid an accidental massive update.</p>
     *
     * @param values values to be used for the update
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *      occurs (in which case $db.error can be checked).
     */
    public synchronized boolean update(SlotMap values)
    {
        if (values != null && values != this)
        {
            setColumnValues(values);
        }
        return update();
    }

    /**
     * <p>Update the row associated with this Instance from actual values.</p>
     * <p>Velosurf will ensure all key columns are specified, to avoid an accidental massive update.</p>
     *
     * @param values values to be used for the update
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *      occurs (in which case $db.error can be checked).
     * @deprecated
     */
    @Deprecated
    public synchronized boolean update(Map<String,Object> values)
    {
        SlotMap v = new SlotHashMap();
        for(Map.Entry<String,Object> entry: (Set<Map.Entry<String,Object>>)values.entrySet())
        {
            v.put(entry.getKey(), (Serializable)entry.getValue());
        }
        return update(v);
    }

    /**
     * <p>Delete the row associated with this Instance.</p>
     * <p>Velosurf will ensure all key columns are specified, to avoid an accidental massive update.</p>
     *
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *     occurs (in which case $db.error can be checked).
     */
    public synchronized boolean delete()
    {
        try
        {
            if (entity == null)
            {
              throw new SQLException("Instance.delete: Error: Entity is null!");
            }
            List<String> whereClause = new ArrayList<String>();
            List<Object> params = new ArrayList<Object>();
            for (String col:entity.getPKCols())
            {
                Object value = getInternal(col);
                if (value == null) throw new SQLException("Instance.delete: Error: field '"+col+"' belongs to primary key and cannot be null!");
                if (entity.isObfuscated(col)) value = entity.deobfuscate(value);
                whereClause.add(col+"=?");
                params.add(value);
            }
            String query = "delete from "+entity.getTableName()+" where "+StringLists.join(whereClause," and ");
            PooledPreparedStatement statement = db.prepare(query);
            int nb = statement.update(params);
            if (nb==0)
            {
                Logger.warn("query \""+query+"\" affected 0 row...");
            }
            else if (nb>1) // ?!?! Referential integrities on key columns should avoid this...
            {
                throw new SQLException("query \""+query+"\" affected more than 1 rows!");
            }
            else
            {
                /* invalidate cache */
                if (entity != null)
                {
                    entity.invalidateInstance(this);
                }
            }
            return true;
        }
        catch (SQLException sqle)
        {
            handleSQLException(sqle);
            return false;
        }
    }

    /**
     * Insert a new row corresponding to this Instance.
     *
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *     occurs (in which case $db.error can be checked).
     */
    public synchronized boolean insert()
    {
        try
        {
            if (entity == null)
            {
                throw new SQLException("Instance.insert: Error: Entity is null!");
            }

            if (!entity.validate(this))
            {
                return false;
            }
            List<String> colsClause = new ArrayList<String>();
            List<String> valsClause = new ArrayList<String>();
            List<Object> params = new ArrayList<Object>();
            List<String> cols = entity.getColumns();
            for (String col:cols)
            {
                Object value = getInternal(col);
                if (value!=null)
                {
                    colsClause.add(col);
                    valsClause.add("?");
                    if (entity.isObfuscated(col))
                    {
                        value = entity.deobfuscate(value);
                    }
                    params.add(value);
                }
            }
            String query = "insert into "+entity.getTableName()+" ("+StringLists.join(colsClause,",")+") values ("+StringLists.join(valsClause,",")+")";
            PooledPreparedStatement statement = db.prepare(query);
            statement.update(params);
            List<String> keys = entity.getPKCols();
            if (keys.size() == 1)
            {
                /* What if the ID is not autoincremented? TODO check it. => reverse engineering of autoincrement, and set the value in the instance itself */
                String keycol = keys.get(0);
                long newid = statement.getLastInsertID();
                db.getUserContext().setLastInsertedID(entity,newid);
                if(getInternal(keycol) == null)
                {
                    put(keycol,entity.isObfuscated(keycol)?entity.obfuscate(newid):newid);
                }
            }
            setClean();
            return true;
        }
        catch (SQLException sqle)
        {
            handleSQLException(sqle);
            return false;
        }
    }

    /**
     * Validate this instance against declared contraints.
     * @return a boolean stating whether this instance data are valid in regard to declared constraints
     */
    public boolean validate()
    {
        try
        {
            return entity.validate(this);
        }
        catch(SQLException sqle)
        {
            handleSQLException(sqle);
            return false;
        }
    }

    /**
     * Handle an sql exception.
     *
     */
    private void handleSQLException(SQLException sqle)
    {
        Logger.log(sqle);
        db.setError(sqle.getMessage());
    }
    
    protected String resolveName(String name)
    {
        if(entity != null)
        {
            return entity.resolveName(name);
        }
        else if (db != null)
        {
            return db.adaptCase(name);
        }
        else
        {
            return name;
        }
    }

    /**
     * Check for a key
     *
     * @return whether or not this key is present
     */
    public boolean containsKey(Object key)
    {
        return super.containsKey(resolveName((String)key));
    }

    /**
     * Removes an association
     *
     * @return the removed object, or null
     */
    public Serializable remove(Object key)
    {
        return super.remove(resolveName((String)key));
    }

    /**
     * This Instance's Entity.
     */
    protected Entity entity = null;

    /**
     * Is there a column to localize?
     */
    private boolean localized = false;

    /**
     * The main database connection.
     */
    protected Database db = null;

    /**
     * Keep a dirty flag per column
     */
    protected List<Boolean> dirtyFlags = null;

    /**
      Inherit toString to avoid listing cached AttributeReference
     */
    public String toString()
    {
	StringBuffer ret = new StringBuffer("{");
	boolean comma = false;
	for(Map.Entry<String,Serializable> entry:super.entrySet())
        {
	    if(comma)
            {
		ret.append(", ");
	    }
            else
            {
              comma = true;
            }
	    if(!(entry.getValue() instanceof AttributeReference))
            {
		ret.append(entry.getKey());
		ret.append("=");
		ret.append(entry.getValue());
	    }
	}
	ret.append("}");
	return ret.toString();
    }

    /**
     * Insert or update, depending on whether or not a value for the id key is present and does exist
     *
     * @return success flag
     */
    public synchronized boolean upsert()
    {
	List<Map<String,Object>> primkey = getPrimaryKey();
	if(primkey.size() != 1)
        {
            Logger.error("Instance.upsert: singleton primary key expected"); // TODO CB - should throw/catch for homogeneity
	    return false;
	}
        Object keyVal = primkey.get(0).get("value");
	if(keyVal == null)
        {
	    return insert();
	}
        else
        {
            Instance previous = getEntity().fetch(String.valueOf(keyVal)); // CB  -TODO: there should be an Entity.fetch(Object) method
            return previous == null ? insert() : update();
	}
    }

    /**
     * Insert or update, depending on whether or not a value for the id key is present and does exist
     */
    public synchronized boolean upsert(SlotMap values)
    {
	if (values != null && values != this)
        {
	    setColumnValues(values);
	}
	return upsert();
    }

    /**
     * Insert or update, depending on whether or not a value for the id key is present and does exist
     * @deprecated
     */
    @Deprecated
    public synchronized boolean upsert(Map<String,Object> values)
    {
        SlotMap v = new SlotHashMap();
        for(Map.Entry<String,Object> entry: (Set<Map.Entry<String,Object>>)values.entrySet())
        {
            v.put(entry.getKey(), (Serializable)entry.getValue());
        }
        return upsert(v);
    }

}
