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



package velosurf.model;

import java.lang.reflect.Constructor;
import java.io.Serializable;
import java.math.BigInteger;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

import velosurf.cache.Cache;
import velosurf.context.EntityListener;
import velosurf.context.Instance;
import velosurf.context.RowIterator;
import velosurf.context.ExternalObjectWrapper;
import velosurf.sql.Database;
import velosurf.sql.PooledPreparedStatement;
import velosurf.sql.SqlUtil;
import velosurf.util.Logger;
import velosurf.util.SlotMap;
import velosurf.util.StringLists;
import velosurf.util.UserContext;
import velosurf.validation.FieldConstraint;
import org.apache.commons.lang.StringEscapeUtils;

/** The Entity class represents an entity in the data model.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 */
public class Entity implements Serializable, EntityListener
{
    /**
     * Constructor reserved for the framework.
     *
     * @param db database connection
     * @param name entity name
     * @param readOnly access mode (read-write or read-only)
     * @param cachingMethod caching method to be used
     */
    public Entity(Database db,String name,boolean readOnly,int cachingMethod)
    {
        this.db = db;
        this.name = name;
        table = name; // default mapped table has same name
        this.readOnly = readOnly;
        this.cachingMethod = cachingMethod;
        if (this.cachingMethod != Cache.NO_CACHE)
        {
            cache = new Cache(this.cachingMethod);
        }
        instanceClass = Instance.class;
    }

    /**
     * Add a column at the end of the sequential list of named columns. Called during the reverse engeenering of the database.
     *
     * @param colName column name
     */
    public void addColumn(String colName, int sqlType, String typeName)
    {
        colName = db.adaptCase(colName);
        columns.add(colName);
        types.put(colName,sqlType);
        /* if (colnames as aliases) */ aliases.put(colName,colName);

        /* column marker for PostgreSQL enums needs to contain type name */
        if (getDB().getDriverInfo().getJdbcTag().equals("postgresql") &&  sqlType == Types.VARCHAR && !typeName.equalsIgnoreCase("varchar"))
        {
            colMarkers.put(colName, "?::" + typeName);
        }
        else
        {
            colMarkers.put(colName, "?");
        }
    }

    /**
     * Add a column alias.
     * @param alias alias
     * @param column column
     */
    public void addAlias(String alias, String column)
    {
        alias = db.adaptCase(alias);
        column = db.adaptCase(column);
        Logger.trace("added alias "+name+"."+alias+" -> "+name+"."+column);
        aliases.put(alias,column);
    }

    /**
     * Translates an alias to its column name.
     * @param alias alias
     * @return column name
     */
    public String resolveName(String alias)
    {
        alias = db.adaptCase(alias);
        String name = aliases.get(alias);
        return name == null ? alias : name;
    }

    /**
     * Add a key column to the sequential list of the key columns. Called during the reverse-engeenering of the database.
     *
     * @param colName name of the key column
     */
    public void addPKColumn(String colName)
    {
        /* remember the alias */
        keyCols.add(colName);
    }

    /**
     * Add a new attribute.
     * @param attribute attribute
     */
    public void addAttribute(Attribute attribute)
    {
        String name = attribute.getName();
        if(attributeMap.containsKey(name))
        {
            Logger.debug("Explicit supersedes implicit for definition of attribute "+getName()+"."+name);
        }
        else
        {
            attributeMap.put(db.adaptCase(name),attribute);
            Logger.trace("defined attribute "+this.name+"."+name+" = "+attribute);
        }
    }

    /**
     * Get a named attribute.
     *
     * @param property attribute name
     * @return the attribute
     */
    public Attribute getAttribute(String property)
    {
        return (Attribute)attributeMap.get(db.adaptCase(property));
    }

    /**
     * Add an action.
     * @param action action
     */
    public void addAction(Action action)
    {
        String name = action.getName();
        actionMap.put(db.adaptCase(name),action);
        Logger.trace("defined action "+this.name+"."+name+" = "+action);
    }

    /**
     * Get an action.
     *
     * @param property action name
     * @return the action
     */
    public Action getAction(String property)
    {
        return (Action)actionMap.get(db.adaptCase(property));
    }

    /**
     * Specify a custom class to use when instanciating this entity.
     *
     * @param className the java class name
     */
    public void setInstanceClass(String className)
    {
        try
        {
            instanceClass = Class.forName(className);
        }
        catch (Exception e)
        {
            Logger.log(e);
        }
    }

    /**
     * Specify the caching method. See {@link Cache} for allowed constants.
     *
     * @param caching Caching method
     */
    public void setCachingMethod(int caching)
    {
        if (cachingMethod != caching)
        {
            cachingMethod = caching;
            if (cachingMethod == Cache.NO_CACHE)
            {
                cache = null;
            }
            else
            {
                cache = new Cache(cachingMethod);
            }
        }
    }

    /**
     * Add a constraint.
     * @param column column name
     * @param constraint constraint
     */
    public void addConstraint(String column,FieldConstraint constraint)
    {
        column = resolveName(column);
        Logger.trace("adding constraint on column "+Database.adaptContextCase(getName())+"."+column+": "+constraint);
        List<FieldConstraint> list = constraints.get(column);
        if (list == null)
        {
            list = new ArrayList<FieldConstraint>();
            constraints.put(column,list);
        }
        list.add(constraint);
    }

    /**
     * Used by the framework to notify this entity that its reverse enginering is over.
     */
    public void reverseEnginered()
    {
        if (obfuscate && keyCols.size()>0)
        {
            keyColObfuscated = new boolean[keyCols.size()];
            Iterator key = keyCols.iterator();
            int i=0;
            for (;key.hasNext();i++)
            {
                keyColObfuscated[i] = obfuscatedColumns.contains(key.next());
            }
        }
        /* fills the cache for the full caching method */
        if(cachingMethod == Cache.FULL_CACHE && cache != null)
        {
            try
            {
                query().getRows();
            }
            catch(SQLException sqle)
            {
                Logger.error("full caching for entity "+getName()+": could not fill the cache!");
                Logger.log(sqle);
            }
        }
    }

    /**
     * Clear the cache (not used for now).
     */
    protected void clearCache()
    {
        if (cache != null) cache.clear();
    }

    /**
     * Create a new realisation of this entity.
     *
     * @return the newly created instance
     */
    public Instance newInstance()
    {
        Instance result = null;
        try
        {
            if( instanceClass.equals(Instance.class))
            {
                result = new Instance(this);
            }
            else if (Instance.class.isAssignableFrom(instanceClass))
            {
                try
                {
                    result = (Instance)instanceClass.newInstance();
                    result.initialize(this);
                }
                catch (Exception e)
                {
                    Constructor instanceConstructor = instanceClass.getConstructor(new Class[] {Entity.class} );
                    result = (Instance)instanceConstructor.newInstance(new Object[] { this });
                }
            }
            else
            {
                result = new ExternalObjectWrapper(this,instanceClass.newInstance());
            }
        }
        catch (Exception e)
        {
            Logger.error("could not create a new instance for entity "+getName());
            Logger.log(e);
            result = null;
        }
        return result;
    }

    /**
     * Build a new instance from a Map object.
     *
     * @param values the Map object containing the values
     * @return the newly created instance
     */
    public Instance newInstance(SlotMap values)
    {
        return newInstance(values,false);
    }

    /**
     * Build a new instance from a Map object.
     *
     * @param values the Map object containing the values
     * @param useSQLnames map keys use SQL column names that must be translated to aliases
     * @return the newly created instance
     */
    public Instance newInstance(SlotMap values,boolean useSQLnames)
    {
        try
        {
            Instance result = newInstance();
            extractColumnValues(values,result,useSQLnames);
            if (cachingMethod != Cache.NO_CACHE && cache != null)
            {
                String key = buildKey(result);
                if (key != null)
                {
                    cache.put(key,result);
                }
            }
            return result;
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }

    /**
     * Cache an instance.
     * @param instance instance
     * @throws SQLException
     */
    public void cacheInstance(SlotMap instance) throws SQLException
    {
        if (cachingMethod != Cache.NO_CACHE && cache != null)
        {
            String key = buildKey(instance);
            if(key != null)
            {
                cache.put(key, instance);
            }
        }
    }

    /**
     * Invalidate an instance in the cache.
     * @param instance instance
     * @throws SQLException
     */
    public void invalidateInstance(SlotMap instance) throws SQLException
    {
        if (cachingMethod != Cache.NO_CACHE && cache != null)
        {
            String key = buildKey(instance);
            if(key != null)
            {
                cache.invalidate(key);
            }
        }
    }

    /**
     * Extract column values from an input Map source and store result in target.
     *
     * @param source Map source object
     * @param target Map target object
     * @param SQLNames the source uses SQL names
     */
    private void extractColumnValues(SlotMap source,SlotMap target,boolean SQLNames) throws SQLException
    {
        /* TODO: cache a case-insensitive version of the columns list and iterate on source keys, with equalsIgnoreCase (or more efficient) funtion */
        /* We use keySet and not entrySet here because if the source map is a ReadOnlyMap, entrySet is not available */
        for(String key:source.keySet())
        {
            /* resove anyway */
            String col = resolveName(key);
            /* this is more or less a hack: we do filter columns
               only when SQLNames is false. The purpose of this
               is to allow additionnal fields in SQL attributes
               returning rowsets of entities. */

            if(!SQLNames && !isColumn(col))
            {
                continue;
            }

            Serializable val = source.get(key);
	        if(val != null && val.getClass().isArray())
            {
		        Logger.error("array cell values not supported");
		        val = null;
	        }

            if (val != null)
            {
                target.put(col, val);
            }
        }
    }

    /**
     * Build the key for the Cache from a Map.
     *
     * @param values the Map containing all values (unaliased)
     * @exception SQLException the getter of the Map throws an
     *     SQLException
     * @return a concatenation all key values
     */
    private String buildKey(SlotMap values) throws SQLException
    {
        if(keyCols.size() == 0)
        {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String column : keyCols)
        {
            Object keyval = values.get(column);
            if (keyval == null) return null;
            builder.append(keyval.toString());
        }
        return builder.toString();
    }

    /**
     * Build the key for the Cache from a List
     *
     * @param values the Map containing all values (unaliased)
     * @exception SQLException the getter of the Map throws an
     *     SQLException
     * @return an array containing all key values
     */
    private String buildKey(List<Object> values) throws SQLException
    {
        if(keyCols.size() == 0)
        {
            return null;
        }
        StringBuilder builder = new StringBuilder();

        for(Object v:values)
        {
            if ( v == null )
            {
                return null;
            }

            builder.append(String.valueOf(v));

        }
        return builder.toString();
    }

    /**
     * Build the key for the Cache from a Number
     *
     * @param value the key value
     * @exception SQLException the getter of the Map throws an
     *     SQLException
     * @return an array containing all key values
     */
    private String buildKey(Number value) throws SQLException
    {
        return String.valueOf(value);
    }


    /**
     * Getter for the name of this entity.
     *
     * @return the name of the entity
     */
    public String getName()
    {
        return name; 
    }

    /**
     * Getter for the list of key column names.
     *
     * @return the list of key column names
     */
    public List<String> getPKCols()
    {
        return keyCols;
    }

    /**
     * Getter for the list of column names.
     *
     * @return the list of column names
     */
    public List<String> getColumns()
    {
        return columns;
    }

    public List<String> getUpdatableColumns()
    {
        if(updatableCols == null)
        {
            updatableCols = new ArrayList(columns);
            updatableCols.removeAll(keyCols);
        }
        return updatableCols;
    }
    
    public boolean isColumn(String name)
    {
        return columns.contains(name);
    }

    public int getColumnIndex(String name)
    {
        return columns.indexOf(name);
    }

    public int getUpdatableColumnIndex(String name)
    {
        return updatableCols.indexOf(name);
    }

    /**
     * Check if the provided map contains all key columns
     *
     * @param values map of values to check
     * @return true if all key columns are present
     */

/* unused     
    public boolean hasKey(Map<String,Object> values) {
        if(keyCols.size() == 0) {
            return false; // could be 'true' but indicates that 'fetch' cannot be called
        }
        List<String> cols = new ArrayList<String>();
        for(String key:values.keySet()) {
            cols.add(resolveName(key));
        }
        return (cols.containsAll(keyCols));
    }
*/

    /**
     * Insert a new row based on values of a map.
     *
     * @param values the  Map object containing the values
     * @return success indicator
     */
    public boolean insert(SlotMap values) throws SQLException
    {
        if (readOnly)
        {
            Logger.error("Error: Entity " + getName() + " is read-only!");
            return false;
        }
        Instance instance = newInstance(values);
        /* if found in cache because it exists, driver will issue a SQLException TODO review this*/
        boolean success = instance.insert();
        if (success && keyCols.size() == 1)
        {
            /* update last insert id */
            try
            {
                String pk = keyCols.get(0);
                values.put(pk,instance.get(pk));
            }
            catch(Exception e)
            {
                Logger.warn("insert: encountered "+e.getMessage()+" while setting last inserted id value (insert was successful)");
            }
        }
        /* try again to put it in the cache since previous attempt may have failed
           in case there are auto-incremented columns */
        if (success && cachingMethod != Cache.NO_CACHE && cache != null)
        {
            String key = buildKey(instance);
            if (key != null)
            {
                cache.put(key,instance);
            }
        }
        return success;
    }

    /**
     * Update a row based on a set of values that must contain key values.
     *
     * @param values the Map object containing the values
     * @return success indicator
     */
    public boolean update(SlotMap values) throws SQLException
    {
        if (readOnly)
        {
            Logger.error("Error: Entity "+getName()+" is read-only!");
            return false;
        }
        Instance instance = newInstance(values);
        return instance.update();
    }

    /**
     * Upsert a row based on a set of values (entity's primary key must be one column long - it can be omitted from provided values)
     *
     * @param values the Map object containing the values
     * @return success indicator
     */
    public boolean upsert(SlotMap values) throws SQLException
    {
        if (readOnly)
        {
            Logger.error("Error: Entity "+getName()+" is read-only!");
            return false;
        }
        Instance instance = newInstance(values);
        boolean success = instance.upsert();
        if (success && keyCols.size() == 1)
        {
            /* update last insert id */
            try
            {
                String pk = keyCols.get(0);
                values.put(pk,instance.get(pk));
            }
            catch(Exception e)
            {
                Logger.warn("upsert: encountered "+e.getMessage()+" while setting last inserted id value (insert was successful)");
            }
        }
	return success;
    }

    /**
     * Delete a row based on (key) values.
     *
     * @param values the Map containing the values
     * @return success indicator
     */
    public boolean delete(SlotMap values) throws SQLException
    {
        if (readOnly)
        {
            Logger.error("Error: Entity " + getName() + " is read-only!");
            return false;
        }
        Instance instance = newInstance(values);
        return instance.delete();
    }

    /**
     * Delete a row based on the unique key string value.
     *
     * @param keyValue key value
     * @return success indicator
     */
    public boolean delete(String keyValue) throws SQLException
    {
        if (readOnly)
        {
            Logger.error("Error: Entity "+getName()+" is read-only!");
            return false;
        }
        if (keyCols.size()!=1)
        {
            if (keyCols.size()==0)
            {
                throw new SQLException("Entity.delete: Error: Entity '"+name+"' has no primary key!");
            }
            else
            {
              throw new SQLException("Entity.delete: Error: Entity '"+name+"' has a multi-column primary key!");
            }
        }
        Instance instance = newInstance();
        instance.put(keyCols.get(0),keyValue);
        return instance.delete();
    }

    /**
     * Delete a row based on the unique key string value.
     *
     * @param keyValue key value
     * @return success indicator
     */
    public boolean delete(Number keyValue) throws SQLException
    {
        if (readOnly)
        {
            Logger.error("Error: Entity "+getName()+" is read-only!");
            return false;
        }
        if (keyCols.size()!=1)
        {
            if (keyCols.size()==0)
            {
                throw new SQLException("Entity.delete: Error: Entity '"+name+"' has no primary key!");
            }
            else
            {
                throw new SQLException("Entity.delete: Error: Entity '"+name+"' has a multi-column primary key!");
            }
        }
        Instance instance = newInstance();
        instance.put(keyCols.get(0),keyValue);
        return instance.delete();
    }


    /**
     * Fetch an instance from key values stored in a List in natural order.
     *
     * @param values the List containing the key values
     * @return the fetched instance
     */
    public Instance fetch(List<Object> values) throws SQLException
    {
        if (values.size() != keyCols.size())
        {
            throw new SQLException("Entity.fetch: Error: Wrong number of values for '"+name+"' primary key! Got "+values.size()+", was expecting "+keyCols.size()+" for key list: "+StringLists.join(keyCols,","));
        }
        Instance instance = null;
        // try in cache
        if (cachingMethod != Cache.NO_CACHE && cache != null)
        {
            instance = (Instance)cache.get(buildKey(values));
        }
        if (instance == null)
        {
            PooledPreparedStatement statement = db.prepare(getFetchQuery(), false);
            if (obfuscate)
            {
                values = new ArrayList<Object>(values);
                for(int col=0;col<keyColObfuscated.length;col++)
                {
                    if(keyColObfuscated[col])
                    {
                        values.set(col,deobfuscate(values.get(col)));
                    }
                }
            }
            instance = (Instance)statement.fetch(values,this);
        }
        if(instance != null)
        {
            instance.setClean();
        }
        return instance;
    }
        
    /**
     * Fetch an instance from key values stored in a Map.
     *
     * @param values the Map containing the key values
     * @return the fetched instance
     */
    public Instance fetch(SlotMap values) throws SQLException
    {
        if(keyCols.size() == 0)
        {
            throw new SQLException("entity "+name+": cannot fetch an instance for an entity without key!");
        }
        Instance instance = null;
        /* extract key values */
        Object key[] = new Object[keyCols.size()];
        int n = 0;
        for(Map.Entry<String,Serializable> entry:values.entrySet())
        {
            String col = resolveName(entry.getKey());
            int i = keyCols.indexOf(col);
            if (i != -1)
            {
                key[i] = entry.getValue();
                n++;
            }
        }
        if (n != keyCols.size() )
        {
            String missing = "";
            for(int c=0;c<key.length;c++)
            {
                if(key[c] == null)
                {
                    missing += keyCols.get(c)+" ";
                }
            }
            throw new SQLException("entity "+name+".fetch(): missing key values! Missing values: "+missing);
        }
        List keyValues = Arrays.asList(key);
        if (cachingMethod != Cache.NO_CACHE && cache != null)
        {
            // try in cache
            instance = (Instance)cache.get(buildKey(keyValues));
        }
        if (instance == null)
        {
            PooledPreparedStatement statement = db.prepare(getFetchQuery(), false);
            if (obfuscate)
            {
                for(int c=0;c<keyCols.size();c++)
                {
                    if (isObfuscated(keyCols.get(c)))
                    {
                        key[c] = deobfuscate(key[c]);
                    }
                }
            }
            instance = (Instance)statement.fetch(keyValues,this);
        }
        if(instance != null)
        {
            instance.setClean();
        }
        return instance;
    }

    /**
     * Fetch an instance from its key value as a string.
     *
     * @param keyValue the key
     * @return the fetched instance
     */
    public Instance fetch(String keyValue) throws SQLException
    {
        if (keyCols.size()!=1)
        {
            if (keyCols.size()==0)
            {
                throw new SQLException("Entity.fetch: Error: Entity '"+name+"' has no primary key!");
            }
            else
            {
                throw new SQLException("Entity.fetch: Error: Entity '"+name+"' has a multi-column primary key!");
            }
        }
        Instance instance = null;

        // try in cache
        if (cachingMethod != Cache.NO_CACHE && cache != null)
        {
            instance = (Instance)cache.get(keyValue);
        }

        if (instance == null)
        {
            PooledPreparedStatement statement = db.prepare(getFetchQuery(), false);
            if (obfuscate && keyColObfuscated[0])
            {
                keyValue = deobfuscate(keyValue);
            }
            List<String> params = new ArrayList<String>();
            params.add(keyValue);
            instance = (Instance)statement.fetch(params,this);
        }
        if(instance != null)
        {
            instance.setClean();
        }
        return instance;
    }

    /**
     * Fetch an instance from its key value specified as a Number.
     *
     * @param keyValue the key
     * @return the fetched instance
     */
    public Instance fetch(Number keyValue) throws SQLException
    {
        if (keyCols.size()!=1)
        {
            if (keyCols.size()==0)
            {
                throw new SQLException("Entity.fetch: Error: Entity '"+name+"' has no primary key!");
            }
            else
            {
                throw new SQLException("Entity.fetch: Error: Entity '"+name+"' has a multi-column primary key!");
            }
        }
        Instance instance = null;

        // try in cache
        if (cachingMethod != Cache.NO_CACHE && cache != null)
        {
            instance = (Instance)cache.get(buildKey(keyValue));
        }

        if (instance == null)
        {
            PooledPreparedStatement statement = db.prepare(getFetchQuery(), false);
            List<Number> params = new ArrayList<Number>();
            params.add(keyValue);
            if(obfuscate && keyColObfuscated[0])
            {
                Logger.warn("fetch: column '"+columns.get(0)+"' is obfuscated, please use $db."+name+".fetch($db.obfuscate("+keyValue+"))");
                return null;
            }
            instance = (Instance)statement.fetch(params,this);
        }
        if(instance != null)
        {
            instance.setClean();
        }
        return instance;
    }

    /**
     * Get the SQL query string used to fetch one instance of this query.
     *
     * @return the SLQ query
     */
    public String getFetchQuery()
    {
        if (fetchQuery == null)
        {
            buildFetchQuery();
        }
        return fetchQuery;
    }

    /**
     * Build the SQL query used to fetch one instance of this query.
     */
    private void buildFetchQuery()
    {
        List<String> whereClause = new ArrayList<String>();
        for(String column:keyCols)
        {
            whereClause.add(column+"=?");
        }
        fetchQuery = "select * from "+table+" where "+StringLists.join(whereClause," and ");
    }

    /**
     * Issue a query to iterate though all instances of this entity.
     *
     * @return the resulting RowIterator
     */
    public RowIterator query() throws SQLException
    {
        return query(null,null);
    }

    /**
     * Issue a query to iterate thought instances of this entity, with a facultative refining criteria and a facultative order by clause.
     *
     * @param refineCriteria a refining criteria or null to get all instances
     * @param order an 'order by' clause or null to get instances in their
     *     natural order
     * @return the resulting RowIterator
     */
    public RowIterator query(List refineCriteria,String order) throws SQLException
    {
        String query = "select * from "+ table;
        if (refineCriteria!=null)
        {
            query = SqlUtil.refineQuery(query,refineCriteria);
        }
        if (order!=null && order.length()>0)
        {
            query = SqlUtil.orderQuery(query,order);
        }
        return db.query(query,this);
    }

    public long getCount()
    {
        return getCount(null);
    }

    public long getCount(List refineCriteria)
    {
        String query = "select count(*) from "+ table;
        if (refineCriteria!=null)
        {
            query = SqlUtil.refineQuery(query,refineCriteria);
        }
        return (Long)db.evaluate(query);
    }

    /**
     * Get the database connection.
     *
     * @return the database connection
     */
    public Database getDB()
    {
        return db;
    }

    /**
     * Is this entity read-only or read-write?
     *
     * @return whether this entity is read-only or not
     */
    public boolean isReadOnly()
    {
        return readOnly;
    }

    /**
     * Set this entity to be read-only or read-write.
     *
     * @param readOnly the mode to switch to : true for read-only, false for
     *     read-write
     */
    public void setReadOnly(boolean readOnly)
    {
        this.readOnly = readOnly;
    }

    /**
     * Set the name of the table mapped by this entity.
     *
     * @param table the table mapped by this entity
     *     read-write
     */
    public void setTableName(String table)
    {
        this.table = table;
    }

    /**
     * Get the name of the mapped table.
     *
     * @return name of the mapped table
     */
    public String getTableName()
    {
        return table;
    }

    /**
     * Indicates a column as being obfuscated.
     * @param columns list of obfuscated columns
     */
    public void setObfuscated(List<String> columns)
    {
        obfuscate = true;
        obfuscatedColumns = columns;
    }

    /**
     * Returns whether the given column is obfuscated.
     * @param column the name of the column
     * @return a boolean indicating whether this column is obfuscated
     */
    public boolean isObfuscated(String column)
    {
        return obfuscate && obfuscatedColumns.contains(db.adaptCase(column));
    }

    /**
     * Obfuscate given value.
     * @param value value to obfuscate
     *
     * @return obfuscated value
     */
    public String obfuscate(Object value)
    {
        return db.obfuscate(value);
    }

    /**
     * Obfuscate this id value if needed.
     *  @param id id value
     *  @return filtered id value (that is, obfuscated if needed)
     */
    public Serializable filterID(Long id)
    {
        if (keyCols.size() == 1 && isObfuscated((String)keyCols.get(0)))
        {
            return obfuscate(Long.valueOf(id));
        }
        return Long.valueOf(id);
    }

    /**
     * De-obfuscate given value.
     * @param value value to de-obfuscate
     *
     * @return obfuscated value
     */
    public String deobfuscate(Object value)
    {
        return db.deobfuscate(value);
    }

    /**
     * Indicates a column as being localized.
     * @param columns list of localized columns
     */
    public void setLocalized(List columns)
    {
        localizedColumns = columns;
    }

    /**
     * Returns whether the given column is obfuscated.
     * @param column the name of the column
     * @return a boolean indicating whether this column is obfuscated
     */
    public boolean isLocalized(String column)
    {
        return localizedColumns != null && localizedColumns.contains(db.adaptCase(column));
    }

    /**
     * Does this entity have localized columns?
     */
    public boolean hasLocalizedColumns()
    {
        return localizedColumns != null && localizedColumns.size() > 0;
    }

    /**
     * Truncate validation error messages to a maximum number of characters.
     */
    private static final int MAX_DATA_DISPLAY_LENGTH = 40;

    /**
     * Validate a set of values.
     */
    public boolean validate(SlotMap row) throws SQLException
    {
        boolean ret = true;

        UserContext userContext = db.getUserContext();
        /* FIXME Is it a good choice to clear the user context now?
           We may want to validate several entities
           before displaying errors to the user.
           */
        userContext.clearValidationErrors();
        List<ValidationError> errors = new ArrayList<ValidationError>();
        for(Map.Entry<String,Serializable> entry:row.entrySet())
        {
            String col = resolveName(entry.getKey());
            Serializable data = entry.getValue();
            List<FieldConstraint> list = constraints.get(col);
            if(list != null)
            {
                for(FieldConstraint constraint:list)
                {
                    if (!constraint.validate(data, userContext.getLocale()))
                    {
                        String stringData = data.toString();
                        String formatted = (data==null || stringData.length() == 0 ? "empty value" : stringData);
                        if (formatted.length() > MAX_DATA_DISPLAY_LENGTH)
                        {
                            formatted = formatted.substring(0,MAX_DATA_DISPLAY_LENGTH)+"...";
                        }
                        formatted = StringEscapeUtils.escapeHtml(formatted);
                        errors.add(new ValidationError(col,userContext.localize(constraint.getMessage(),Database.adaptContextCase(col),formatted)));
                        ret = false;
                    }
                }
            }
        }
        if(errors.size()>0)
        {
            /* sort in columns natural order... better than nothing.
               The ideal ordering would be the order of the form fields,
               but it is unreachable. */
            Collections.sort(errors);
            for(ValidationError error:errors)
            {
                Logger.trace("validation: new message: "+error.message);
                userContext.addValidationError(error.message);
            }
        }
        return ret;
    }

    public class ColumnOrderComparator implements Comparator<String>, Serializable
    {
        public int compare(String o1, String o2)
        {
            String col1 = resolveName(o1);
            String col2 = resolveName(o2);
            int i1 = columns.indexOf(col1);
            int i2 = columns.indexOf(col2);
	    if(i1 == -1 && i2 == -1)
            {
                return o1.compareTo(o2);
            }
	    else if (i1 == -1)
            {
                return -1;
            }
	    else if (i2 == -1)
            {
                return 1;
            }
            else
            {
                return i1 - i2;
            }
        }
    }

    public Comparator<String> getColumnOrderComparator()
    {
        return new ColumnOrderComparator();
    }

    class ValidationError implements Comparable<ValidationError>
    {
        ValidationError(String column,String message)
        {
            index = columns.indexOf(column);
            this.message = message;
        }

        public int compareTo(ValidationError cmp)
        {
            return index - cmp.index;
        }

        String message;
        int index;
    }
    

    /**
     * Check for the existence of an imported key with the same columns.
     * @param pkEntity primary key entity
     * @param fkCols foreign key columns
     * @return previously defined imported key, if any
     */
    public ImportedKey findImportedKey(Entity pkEntity,List<String> fkCols)
    {
        for(Map.Entry<String,Attribute> entry:attributeMap.entrySet())
        {
            Attribute attribute = entry.getValue();
            if (!(attribute instanceof ImportedKey))
            {
                continue;
            }
            ImportedKey imported = (ImportedKey)attribute;
            if (imported.getResultEntity().equals(pkEntity.getName())
                && ( imported.getFKCols() == null || imported.getFKCols().equals(fkCols)) )
            {
                return imported;
            }
        }
        return null;
    }

    /**
     * Check for the existence of an exported key with the same columns.
     * @param fkEntity foreign key entity
     * @param fkCols foreign key columns
     * @return previously defined exported key, if any
     */
    public ExportedKey findExportedKey(Entity fkEntity,List<String> fkCols)
    {
        for(Map.Entry<String,Attribute> entry:attributeMap.entrySet())
        {
            Attribute attribute = entry.getValue();
            if (!(attribute instanceof ExportedKey))
            {
                continue;
            }
            ExportedKey exported = (ExportedKey)attribute;
            if (exported.getResultEntity().equals(fkEntity.getName())
                && ( exported.getFKCols() == null || exported.getFKCols().equals(fkCols) ))
            {
                return exported;
            }
        }
        return null;
    }

    public Serializable filterIncomingValue(String column, Serializable value) throws SQLException
    {
        if(value == null)
        {
            return null;
        }
        /* for now, only filter boolean values */
        Integer type = types.get(column);
        if (type == null)
        {
            return value;
        }
        if(type == Types.BOOLEAN || type == Types.BIT)
        {
            if(String.class.isAssignableFrom(value.getClass()))
            {
                String s = (String)value;
                if("true".equalsIgnoreCase(s) || "on".equalsIgnoreCase(s) || "1".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s))
                {
                    value = new Boolean(true);
                }
                else
                {
                    value = new Boolean(false);
                }
            }
        }
        else if (getDB().getDriverInfo().getPedanticColumnTypes())
        {
            Class expected = sqlTypeToClass.get(type);
            if (expected == null) Logger.warn("sql type " + type + " is not handled");
            else
            {
                value = (Serializable)getDB().convert(value, expected);
            }
        }
        return value;
    }

    public Map<String,Attribute> getAttributes()
    {
        return attributeMap;
    }

    public Map<String,Action> getActions()
    {
        return actionMap;
    }

    public boolean isRootEntity()
    {
        return "velosurf.root".equals(name);
    }

    /**
     * Get the SQL type for the specified column 
     * @param column column name
     * @return the sql type
     */
    public int getColumnType(String column)
    {
        return types.get(column);
    }

    /**
     * Get column marker: either '?' or '?::<i>enum-type-name</i>' for PostgreSQL databases
     */
    public String getColumnMarker(String column)
    {
        return colMarkers.get(column);
    }

    /**
     * Name.
     */
    private String name = null;

    /**
     * Table.
     */
    private String table = null;

    /**
     * Column names in natural order.
     */
    private List<String> columns = new ArrayList<String>(); // list<String>

    /**
     * Column types
     */
    private Map<String,Integer> types = new HashMap<String,Integer>();

    /**
     * Column markers: either '?' or '?::<i>enum-type-name</i> for PostgreSQL enums
     */
    private Map<String, String> colMarkers = new HashMap<String, String>();


    /**
     * Key column names in natural order.
     */
    private List<String> keyCols = new ArrayList<String>();

    /**
     * Non-key column names in natural order
     */
    private List<String> updatableCols = null;

    /**
     * Whether to obfuscate something.
     */
    private boolean obfuscate = false;

    /**
     * Names of obfuscated columns.
     */
    private List<String> obfuscatedColumns = null;

    /**
     * Obfuscation status of key columns.
     */
    private boolean keyColObfuscated[] = null;

    /**
     * Localized columns.
     */
    private List localizedColumns = null;

    /**
     * Attributes of this entity.
     */

    /**
     * Column by alias map.
     */
    private Map<String,String> aliases = new HashMap<String,String>();

    /**
     * Attribute map.
     */
    private Map<String,Attribute> attributeMap = new HashMap<String,Attribute>();

    /**
     * Action map.
     */
    private Map<String,Action> actionMap = new HashMap<String,Action>();

    /**
     * The java class to use to realize this instance.
     */
    private Class instanceClass = null;

    /**
     * The SQL query used to fetch one instance of this entity.
     */
    private String fetchQuery = null;

    /**
     * Whether this entity is read-only or not.
     */
    private boolean readOnly;

    /**
     * The database connection.
     */
    private Database db = null;

    /**
     * The caching method.
     */
    private int cachingMethod = 0;

    /**
     * The cache.
     */
    private transient Cache cache = null;
    
    /**
     * Constraint by column name map.
     */
    private Map<String,List<FieldConstraint>> constraints = new HashMap<String,List<FieldConstraint>>();

    /**
     * java.sql.Types int to class (TODO - move it to a utility class)
     */
    static private Map<Integer, Class> sqlTypeToClass;

    /* CB TODO - a real mapping requires taking precision and scale into account! */
    static
    {
        sqlTypeToClass = new HashMap<Integer, Class>();
        sqlTypeToClass.put(Types.BIGINT, BigInteger.class);
        sqlTypeToClass.put(Types.BOOLEAN, Boolean.class);
        sqlTypeToClass.put(Types.CHAR, String.class);
        sqlTypeToClass.put(Types.DATE, java.sql.Date.class);
        sqlTypeToClass.put(Types.DECIMAL, Double.class);
        sqlTypeToClass.put(Types.DOUBLE, Double.class);
        sqlTypeToClass.put(Types.FLOAT, Float.class);
        sqlTypeToClass.put(Types.INTEGER, Integer.class);
        sqlTypeToClass.put(Types.LONGNVARCHAR, String.class);
        sqlTypeToClass.put(Types.LONGVARCHAR, String.class);
        sqlTypeToClass.put(Types.NCHAR, String.class);
        sqlTypeToClass.put(Types.NUMERIC, Double.class);
        sqlTypeToClass.put(Types.NVARCHAR, String.class);
        sqlTypeToClass.put(Types.REAL, Float.class);
        sqlTypeToClass.put(Types.ROWID, Long.class);
        sqlTypeToClass.put(Types.SMALLINT, Short.class);
        sqlTypeToClass.put(Types.TIME, java.sql.Time.class);
        sqlTypeToClass.put(Types.TIMESTAMP, java.sql.Timestamp.class);
        sqlTypeToClass.put(Types.TINYINT, Byte.class);
        sqlTypeToClass.put(Types.VARCHAR, String.class);
    }

    private Set<EntityListener> listeners = null;
    private EventsQueue eventQueue = null;

    ;

    public synchronized void addListener(EntityListener listener)
    {
        if (listeners == null) listeners = new HashSet<>();
        eventQueue = db.getEventsQueue();
        listeners.add(listener);
    }

    public boolean hasListeners() { return listeners != null && listeners.size() > 0; }

    public void dispatchEvent(EventsQueue.Event event)
    {
        switch (event.type)
        {
            case INSERT: for (EntityListener listener : listeners) listener.inserted(event.instance); break;
            case UPDATE: for (EntityListener listener : listeners) listener.updated(event.instance, event.fields); break;
            case DELETE: for (EntityListener listener : listeners) listener.deleted(event.instance); break;
        }
    }

    @Override
    public void inserted(Instance instance) { if (eventQueue != null) eventQueue.post(new EventsQueue.Event(EventsQueue.EventType.INSERT, instance)); }

    @Override
    public void deleted(Instance instance) { if (eventQueue != null) eventQueue.post(new EventsQueue.Event(EventsQueue.EventType.DELETE, instance)); }

    @Override
    public void updated(Instance instance, Set<String> fields) { if (eventQueue != null) eventQueue.post(new EventsQueue.Event(EventsQueue.EventType.INSERT, instance, fields)); }

}
