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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import velosurf.model.Entity;
import velosurf.model.Attribute;
import velosurf.util.Logger;
import velosurf.util.SlotHashMap;
import velosurf.util.SlotMap;
import velosurf.util.UserContext;

/**
 * Context wrapper for an entity.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 */
public class EntityReference implements Iterable, Serializable
{
    /**
     * Builds a new EntityReference.
     *
     * @param entity the wrapped entity
     */
    public EntityReference(Entity entity)
    {
        this.entity = entity;
    }

    /**
     * gets the name of the wrapped entity
     */
    public String getName()
    {
        return entity.getName();
    }

    /**
     * Insert a new row in this entity's table.
     *
     * @param values col -> value map
     * @return <code>true</code> if successfull, <code>false</code> if an error occurs (in which case $db.error can be checked).
     */
    public boolean insert(SlotMap values)
    {
        try
        {
            return entity.insert(values);
        }
        catch(SQLException sqle)
        {
            Logger.log(sqle);
            entity.getDB().setError(sqle.getMessage());
            return false;
        }
    }

    /**
     * Insert a new row in this entity's table.
     *
     * @param values col -> value map
     * @return <code>true</code> if successfull, <code>false</code> if an error occurs (in which case $db.error can be checked).
     * @deprecated
     */
    @Deprecated
    public boolean insert(Map<String,Object> values)
    {
        SlotMap v = new SlotHashMap();
        for(Map.Entry<String,Object> entry: (Set<Map.Entry<String,Object>>)values.entrySet())
        {
            v.put(entry.getKey(), (Serializable)entry.getValue());
        }
	boolean ret = insert(v);
	if (ret)
	{
	    List<String> pk = entity.getPKCols();
	    if (pk.size() == 1)
	    {
		Object keyval = v.get(pk.get(0));
		if (keyval != null)
		{
		    try
		    {
			values.put(pk.get(0), keyval);
		    }
		    catch(Exception e)
		    {
			Logger.warn("insert: encountered "+e.getMessage()+" while setting last inserted id value (insert was successful)");
		    }
		}
	    }
	}
	return ret;
    }

    /**
     * Returns the ID of the last inserted row (obfuscated if needed).
     *
     * @return last insert ID
     */
    public Serializable getLastInsertID()
    {
        long id = entity.getDB().getUserContext().getLastInsertedID(entity);
        return entity.filterID(id);
    }

    /**
     * <p>Update a row in this entity's table.</p>
     *
     * <p>Velosurf will ensure all key columns are specified, to avoid an accidental massive update.</p>
     *
     * @param values col -> value map
     * @return <code>true</code> if successfull, <code>false</code> if an error occurs (in which case $db.error can be checked).
     */
    public boolean update(SlotMap values)
    {
        try
        {
            return entity.update(values);
        }
        catch(SQLException sqle)
        {
            Logger.log(sqle);
            entity.getDB().setError(sqle.getMessage());
            return false;
        }
    }

    /**
     * <p>Update a row in this entity's table.</p>
     *
     * <p>Velosurf will ensure all key columns are specified, to avoid an accidental massive update.</p>
     *
     * @param values col -> value map
     * @return <code>true</code> if successfull, <code>false</code> if an error occurs (in which case $db.error can be checked).
     */
    @Deprecated
    public boolean update(Map<String,Object> values)
    {
        SlotMap v = new SlotHashMap();
        for(Map.Entry<String,Object> entry: (Set<Map.Entry<String,Object>>)values.entrySet())
        {
            v.put(entry.getKey(), (Serializable)entry.getValue());
        }
        return update(v);
    }

    /**
     * <p>Upsert a row in this entity's table.</p>
     *
     * <p>Primary key must be a single column.</p>
     *
     * @param values col -> value map
     * @return <code>true</code> if successfull, <code>false</code> if an error occurs (in which case $db.error can be checked).
     */
    public boolean upsert(SlotMap values)
    {
        try
        {
            return entity.upsert(values);
        }
        catch(SQLException sqle)
        {
            Logger.log(sqle);
            entity.getDB().setError(sqle.getMessage());
            return false;
        }
    }

    /**
     * <p>Upsert a row in this entity's table.</p>
     *
     * <p>Primary key must be a single column.</p>
     *
     * @param values col -> value map
     * @return <code>true</code> if successfull, <code>false</code> if an error occurs (in which case $db.error can be checked).
     * @deprecated
     */
     @Deprecated
     public boolean upsert(Map<String,Object> values)
     {
        SlotMap v = new SlotHashMap();
        for(Map.Entry<String,Object> entry: (Set<Map.Entry<String,Object>>)values.entrySet())
        {
            v.put(entry.getKey(), (Serializable)entry.getValue());
        }
	boolean ret = upsert(v);
	if (ret)
	{
	    List<String> pk = entity.getPKCols();
	    if (pk.size() == 1)
	    {
		Object keyval = v.get(pk.get(0));
		if (keyval != null)
		{
		    try
		    {
			values.put(pk.get(0), keyval);
		    }
		    catch(Exception e)
		    {
			Logger.warn("upsert: encountered "+e.getMessage()+" while setting last inserted id value (insert was successful)");
		    }
		}
	    }
	}
        return ret;
     }

    /**
     * <p>Detele a row from this entity's table.</p>
     *
     * <p>Velosurf will ensure all key columns are specified, to avoid an accidental massive update.</p>
     *
     * @param values col -> value map
     * @return <code>true</code> if successfull, <code>false</code> if an error occurs (in which case $db.error can be checked).
     */
      public boolean delete(SlotMap values)
      {
        try
        {
            return entity.delete(values);
        }
        catch(SQLException sqle)
        {
            Logger.log(sqle);
            entity.getDB().setError(sqle.getMessage());
            return false;
        }
     }

    /**
     * <p>Detele a row from this entity's table.</p>
     *
     * <p>Velosurf will ensure all key columns are specified, to avoid an accidental massive update.</p>
     *
     * @param values col -> value map
     * @return <code>true</code> if successfull, <code>false</code> if an error occurs (in which case $db.error can be checked).
     * @deprecated
     */
      @Deprecated
      public boolean delete(Map<String,Object> values)
      {
        SlotMap v = new SlotHashMap();
        for(Map.Entry<String,Object> entry: (Set<Map.Entry<String,Object>>)values.entrySet())
        {
            v.put(entry.getKey(), (Serializable)entry.getValue());
        }
        return delete(v);
      }

    /**
     * <p>Detele a row from this entity's table, specifying the value of its unique key column.</p>
     *
     * @param keyValue key value
     * @return <code>true</code> if successfull, <code>false</code> if an error occurs (in which case $db.error can be checked).
     */
    public boolean delete(String keyValue)
    {
        try
        {
            return entity.delete(keyValue);
        }
        catch(SQLException sqle)
        {
            Logger.log(sqle);
            entity.getDB().setError(sqle.getMessage());
            return false;
        }
    }

    /**
     * <p>Detele a row from this entity's table, specifying the value of its unique key column.</p>
     *
     * <p>Velosurf will ensure all key columns are specified, to avoid an accidental massive update.</p>
     *
     * @param keyValue key value
     * @return <code>true</code> if successfull, <code>false</code> if an error occurs (in which case $db.error can be checked).
     */
    public boolean delete(Number keyValue)
    {
        try
        {
            return entity.delete(keyValue);
        }
        catch(SQLException sqle)
        {
            Logger.log(sqle);
            entity.getDB().setError(sqle.getMessage());
            return false;
        }
    }

    /**
     * Fetch an Instance of this entity, specifying the values of its key columns in their natural order.
     *
     * @param values values of the key columns
     * @return an Instance, or null if an error occured (in which case
     *     $db.error can be checked)
     */
    public Instance fetch(List<Object> values)
    {
        try
        {
            Instance instance = entity.fetch(values);

            return instance;
        }
        catch(SQLException sqle)
        {
            Logger.log(sqle);
            entity.getDB().setError(sqle.getMessage());
            return null;
        }
    }

    /**
     * Fetch an Instance of this entity, specifying the values of its key columns in the map.
     *
     * @param values key=>value map
     * @return an Instance, or null if an error occured (in which case
     *     $db.error can be checked)
     */
    public Instance fetch(SlotMap values)
    {
        try
        {
            Instance instance = entity.fetch(values);

            return instance;
        }
        catch(SQLException sqle)
        {
            Logger.log(sqle);
            entity.getDB().setError(sqle.getMessage());
            return null;
        }
    }

    /**
     * Fetch an Instance of this entity, specifying the values of its key columns in the map.
     *
     * @param values key=>value map
     * @return an Instance, or null if an error occured (in which case
     *     $db.error can be checked)
     * @deprecated
     */
    @Deprecated
    public Instance fetch(Map<String,Object> values)
    {
        SlotMap v = new SlotHashMap();
        for(Map.Entry<String,Object> entry: (Set<Map.Entry<String,Object>>)values.entrySet())
        {
            v.put(entry.getKey(), (Serializable)entry.getValue());
        }
        return fetch(v);
    }

    /**
     * Fetch an Instance of this entity, specifying the value of its unique key column as a string
     *
     * @param keyValue value of the key column
     * @return an Instance, or null if an error occured (in which case
     *     $db.error can be checked)
     */
    public Instance fetch(String keyValue)
    {
        try
        {
            Instance instance = entity.fetch(keyValue);

            return instance;
        }
        catch(SQLException sqle)
        {
            Logger.log(sqle);
            entity.getDB().setError(sqle.getMessage());
            return null;
        }
    }

    /**
     * Fetch an Instance of this entity, specifying the value of its unique key column as an integer
     *
     * @param keyValue value of the key column
     * @return an Instance, or null if an error occured (in which case
     *     $db.error can be checked)
     */
    public Instance fetch(Number keyValue)
    {
        try
        {
            Instance instance = entity.fetch(keyValue);

            return instance;
        }
        catch(SQLException sqle)
        {
            Logger.log(sqle);
            entity.getDB().setError(sqle.getMessage());
            return null;
        }
    }

    /**
     * Called by the #foreach directive.
     *
     * @return a RowIterator on all instances of this entity, possibly previously
     *      refined or ordered.
     */
    public Iterator iterator()
    {
        try
        {
            RowIterator iterator = entity.query(refineCriteria, order);

            return iterator;
        }
        catch(SQLException sqle)
        {
            Logger.log(sqle);
            entity.getDB().setError(sqle.getMessage());
            return null;
        }
    }

    /**
     * Get all the rows in a list of maps.
     *
     * @return a list of all the rows
     */
    public List getRows()
    {
        try
        {
            RowIterator iterator = entity.query(refineCriteria, order);

            return iterator.getRows();
        }
        catch(SQLException sqle)
        {
            Logger.log(sqle);
            entity.getDB().setError(sqle.getMessage());
            return null;
        }
    }

    /**
     * <p>Refines this entity reference querying result. The provided criterium will be added to the 'where' clause (or a 'where' clause will be added).</p>
     *
     * <p>This method can be called several times, thus allowing a field-by-field handling of an html search form.</p>
     *
     * <p>All criteria will be merged with the sql 'and' operator (if there is an initial where clause, it is wrapped into parenthesis).</p>
     *
     * <p>Example: if we issue the following calls from inside the template:</p>
     * <blockquote>
     * $person.refine("age>30")
     * <br>
     * $person.refine("salary>3000")
     * </blockquote>
     * <p>the resulting query that will be issed is:</p>
     *
     * <p><code>select * from person where (age>30) and (salary>3000)</code></p>
     *
     * @param criterium a valid sql condition
     */
    public void refine(String criterium)
    {
        Logger.trace("refine: " + criterium);

        /* protect from SQL query injection */

        // FIXME: check that there is an even number of "'"
        if( /* criterium.indexOf('\'') != -1 || */criterium.indexOf(';') != -1 || criterium.indexOf("--") != -1)
        {
            Logger.error("bad refine string: " + criterium);
        }
        else
        {
            if(refineCriteria == null)
            {
                refineCriteria = new ArrayList();
            }
            refineCriteria.add(criterium);
        }
    }

    /**
     * Clears any refinement made on this entity.
     */
    public void clearRefinement()
    {
        refineCriteria = null;
    }

    /**
     * <p>Specify an 'order by' clause for this attribute reference result.</p>
     * <p>If an 'order by' clause is already present in the original query, the ew one is appended (but successive calls to this method overwrite previous ones).</p>
     * <p> postfix " DESC " to a column for descending order.</p>
     * <p>Pass it null or an empty string to clear any ordering.</p>
     *
     * @param order valid sql column names (separated by commas) indicating the
     *      desired order
     */
    public void setOrder(String order)
    {
        /* protect from SQL query injection */
        if(order.indexOf('\'') != -1 || order.indexOf(';') != -1 || order.indexOf("--") != -1)
        {
            Logger.error("bad order string: " + order);
        }
        else
        {
            this.order = order;
        }
    }

    /**
     * Create a new instance for this entity.
     *
     * @return the newly created instance
     */
    public Instance newInstance()
    {
        Instance instance = entity.newInstance();

        return instance;
    }

    /**
     * Build a new instance from a Map object.
     *
     * @param values the Map object containing the values
     * @return the newly created instance
     */
    public Instance newInstance(SlotMap values)
    {
        Instance instance = entity.newInstance(values);

        return instance;
    }

    /**
     * Build a new instance from a Map object.
     *
     * @param values the Map object containing the values
     * @return the newly created instance
     * @deprecated
     */
    @Deprecated
    public Instance newInstance(Map<String,Object> values)
    {
        SlotMap v = new SlotHashMap();
        for(Map.Entry<String,Object> entry: (Set<Map.Entry<String,Object>>)values.entrySet())
        {
            v.put(entry.getKey(), (Serializable)entry.getValue());
        }
        return newInstance(v);
    }

    /**
     * Validate values of this instance.
     * @param values
     * @return true if values are valid with respect to defined column constraints
     */
    public boolean validate(SlotMap values)
    {
        try
        {
            return entity.validate(values);
        }
        catch(SQLException sqle)
        {
            Logger.error("could not check data validity!");
            entity.getDB().getUserContext().addValidationError("internal errror");
            Logger.log(sqle);
            return false;
        }
    }

    /**
     * Getter for the list of column names.
     *
     * @return the list of column names
     */
    public List<String> getColumns()
    {
        return entity.getColumns();
    }

    public long getCount()
    {
        return entity.getCount(refineCriteria);
    }

    /**
     * The wrapped entity.
     */
    private Entity entity = null;

    /**
     * Specified order.
     */
    private String order = null;

    /**
     * Specified refining criteria.
     */
    private List<String> refineCriteria = null;

    /**
     * toString, used for debugging
     */
    public String toString()
    {
        return "[" + getName() + " with attributes: " + entity.getAttributes().keySet() + "]";
    }

    public void addListener(EntityListener listener) { entity.addListener(listener); }

    public int getAttributeType(String attrName)
    {
        Attribute attr = entity.getAttribute(attrName);
        if (attr != null) return attr.getType();
        else return -1;
    }

    public String getAttributeEntityName(String attrName)
    {

        Attribute attr = entity.getAttribute(attrName);
        //check attribute is of type row
        if (attr != null)
            {
                if(attr.getType() == Attribute.ROW)
                    return attr.getResultEntity();
                else return null;
            }
        else return null;

    }
    
}
