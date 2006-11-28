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
import java.sql.SQLException;
import java.util.*;

import velosurf.cache.Cache;
import velosurf.context.Instance;
import velosurf.context.RowIterator;
import velosurf.context.ExternalObjectWrapper;
import velosurf.sql.Database;
import velosurf.sql.PooledPreparedStatement;
import velosurf.sql.SqlUtil;
import velosurf.util.Logger;
import velosurf.util.StringLists;
import velosurf.util.UserContext;
import velosurf.validation.FieldConstraint;

import org.apache.commons.lang.StringEscapeUtils;

/** The Entity class represents an entity in the data model.
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 */
public class Entity
{
    /** Constructor reserved for the framework.
     *
     * @param db database connection
     * @param name entity name
     * @param readOnly access mode (read-write or read-only)
     * @param cachingMethod caching method to be used
     */
    public Entity(Database db,String name,boolean readOnly,int cachingMethod) {
        this.db = db;
        this.name = name;
        table = name; // default mapped table has same name
        this.readOnly = readOnly;
        this.cachingMethod = cachingMethod;
        if (this.cachingMethod != Cache.NO_CACHE) cache = new Cache(this.cachingMethod);
        instanceClass = Instance.class;
    }

    /** Add a column at the end of the sequential list of named columns. Called during the reverse engeenering of the database.
     *
     * @param colName column name
     */
    public void addColumn(String colName) {
        /* remember the alias */
        columns.add(columnToAlias(colName));
    }

    /**
     * Add a column alis.
     * @param alias alias
     * @param column column
     */
    public void addAlias(String alias, String column) {
        aliasByColumn.put(column,alias);
        columnByAlias.put(alias,column);
    }

    /**
     * Translates an alias to its column name.
     * @param alias alias
     * @return column name
     */
    public String aliasToColumn(String alias) {
        String col = columnByAlias.get(alias);
        return col == null ? alias : col;
    }

    /**
     * Translate a list of aliases to a list of column names.
     * @param aliases list of aliases
     * @return list of column names
     */
    public List<String> aliasToColumn(List<String> aliases) {
        if(columnByAlias.size() > 0) {
            List<String> ret = new ArrayList<String>();
            for(String alias:aliases) {
                ret.add(aliasToColumn(alias));
            }
            return ret;
        } else return aliases;
    }

    /**
     * Translate a column name to an alias.
     * @param column column
     * @return alias
     */
    public String columnToAlias(String column) {
        String alias = aliasByColumn.get(column);
        return alias == null ? column : alias;
    }

    /** Add a key column to the sequential list of the key columns. Called during the reverse-engeenering of the database.
     *
     * @param colName name of the key column
     */
    public void addPKColumn(String colName) {
        /* remember the alias */
        keyCols.add(columnToAlias(colName));
    }

    /** Add a new attribute.
     * @param attribute attribute
     */
    public void addAttribute(Attribute attribute) {
        String name = attribute.getName();
        if(attributeMap.containsKey(name)) {
            Logger.warn("Ignoring second definition for attribute "+name+"!");
        } else {
            attributeMap.put(db.adaptCase(name),attribute);
            Logger.debug("defined attribute "+this.name+"."+name+" = "+attribute);
        }
    }

    /** Get a named attribute.
     *
     * @param property attribute name
     * @return the attribute
     */
    public Attribute getAttribute(String property) {
        return (Attribute)attributeMap.get(db.adaptCase(property));
    }

    /**
     * Add an action.
     * @param action action
     */
    public void addAction(Action action) {
        String name = action.getName();
        actionMap.put(db.adaptCase(name),action);
        Logger.debug("action "+this.name+"."+name+" = "+action);
    }

    /** get an action.
     *
     * @param property action name
     * @return the action
     */
    public Action getAction(String property) {
        return (Action)actionMap.get(db.adaptCase(property));
    }

    /** Specify a custom class to use when instanciating this entity.
     *
     * @param className the java class name
     */
    public void setInstanceClass(String className) {
        try {
            instanceClass = Class.forName(className);
        }
        catch (Exception e) {
            Logger.log(e);
        }
    }

    /** Specify the caching method. See {@link Cache} for allowed constants.
     *
     * @param caching Caching method
     */
    public void setCachingMethod(int caching) {
        if (cachingMethod != caching) {
            cachingMethod = caching;
            if (cachingMethod == Cache.NO_CACHE) cache = null;
            else cache = new Cache(cachingMethod);
        }
    }

    /**
     * Add a constraint.
     * @param column column name
     * @param constraint constraint
     */
    public void addConstraint(String column,FieldConstraint constraint) {;
        Logger.trace("adding constraint on column "+Database.adaptContextCase(getName())+"."+column+": "+constraint);
        constraints.add(new FieldConstraintInfo(column,constraint));
    }

    /** Used by the framework to notify this entity that its reverse enginering is over.
     */
    public void reverseEnginered() {
        if (obfuscate && keyCols.size()>0) {
            keyColObfuscated = new boolean[keyCols.size()];
            Iterator key = keyCols.iterator();
            int i=0;
            for (;key.hasNext();i++)
                keyColObfuscated[i] = obfuscatedColumns.contains(key.next());
        }
        /* fills the cache for the full caching method */
        if(cachingMethod == Cache.FULL_CACHE) {
            try {
                query().getRows();
            } catch(SQLException sqle) {
                Logger.error("full caching for entity "+getName()+": could not fill the cache!");
                Logger.log(sqle);
            }
        }
    }

    /** Clear the cache.
     */
    public void clearCache() {
        if (cache != null) cache.clear();
    }

    /** Create a new realisation of this entity.
     *
     * @return the newly created instance
     */
    public Instance newInstance() {
        Instance result = null;
        try {
            if (Instance.class.isAssignableFrom(instanceClass)) {
                try {
                    result = (Instance)instanceClass.newInstance();
                    result.initialize(this);
                } catch (Exception e) {
                    Constructor instanceConstructor = instanceClass.getConstructor(new Class[] {Entity.class} );
                    result = (Instance)instanceConstructor.newInstance(new Object[] { this });
                }
            } else {
                result = new ExternalObjectWrapper(this,instanceClass.newInstance());
            }
        }
        catch (Exception e) {
            Logger.error("could not create a new instance for entity "+getName());
            Logger.log(e);
            result = null;
        }
        return result;
    }

    /** Build a new instance from a Map object.
     *
     * @param values the Map object containing the values
     * @return the newly created instance
     */
    public Instance newInstance(Map<String,Object> values) {
        try {
            Instance result = newInstance();
            extractColumnValues(values,result);
            if (cachingMethod != Cache.NO_CACHE) cache.put(buildKey(values),result);
            return result;
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }


    /** Get an instance from its values contained in a Map object.
     * By default, update all fields based on the values in the Map if the instance has been found in the cache.
     *
     * @param values the Map object containing the values
     * @return the instance
     */
    public Instance getInstance(Map<String,Object> values) {
        Instance ret = null;
        try {
            if (cachingMethod != Cache.NO_CACHE)
                // try in cache
                ret = (Instance)cache.get(buildKey(values));
            if (ret == null) ret = newInstance(values);
            return ret;
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }

    /**
     * Invalidate an instance in the cache.
     * @param instance instance
     * @throws SQLException
     */
    public void invalidateInstance(Map<String,Object> instance) throws SQLException {
        if (cachingMethod != Cache.NO_CACHE) {
            cache.invalidate(buildKey(instance));
        }
    }

    /** Extract column values from an input Map source and store result in target.
     *
     * @param source Map source object
     * @param target Map target object
     */
    private void extractColumnValues(Map<String,Object> source,Map<String,Object> target) throws SQLException {
        /* TODO: cache a case-insensitive version of the columns list and iterate on source keys, with equalsIgnoreCase (or more efficient) funtion */
        for(Iterator i=columns.iterator();i.hasNext();) {
            String col = (String)i.next();
            Object val = source.get(col);
            if (val == null) {
                switch(db.getCaseSensivity()) {
                    /* for now, only try with different letter case... */
                    case Database.UPPERCASE:
                        val = source.get(col.toLowerCase());
                        break;
                    case Database.LOWERCASE:
                        val = source.get(col.toUpperCase());
                        break;
                }
            }
            /* avoid null and multivalued attributes */
            if (val != null && !(val.getClass().isArray())) {
                target.put(col,val);
            }
        }
    }

    /** Build the key for the Cache from a Map.
     *
     * @param values the Map containing all values
     * @exception SQLException the getter of the Map throws an
     *     SQLException
     * @return an array containing all key values
     */
    private Object buildKey(Map<String,Object> values) throws SQLException {

        // build key
        Object [] key = new Object[keyCols.size()];
        int c=0;
        for(Iterator i = keyCols.iterator(); i.hasNext();)
            key[c++] = values.get(i.next());
        return key;
    }


    /** Getter for the name of this entity.
     *
     * @return the name of the entity
     */
    public String getName() { return name; }

    /** Getter for the list of key column names.
     *
     * @return the list of key column names
     */
    public List<String> getPKCols() {
        return keyCols;
    }

    /** Getter for the list of column names.
     *
     * @return the list of column names
     */
    public List<String> getColumns() {
        return columns;
    }

    /** Insert a new row based on values of a map.
     *
     * @param values the  Map object containing the values
     * @return success indicator
     */
    public boolean insert(Map<String,Object> values) throws SQLException {
        if (readOnly) {
            Logger.error("Error: Entity "+getName()+" is read-only!");
            return false;
        }
        Instance instance = newInstance(values);
        /* if found in cache because it exists, driver will issue a SQLException */
        return instance.insert();
    }

    /** Update a row based on a set of values that must contain key values.
     *
     * @param values the Map object containing the values
     * @return success indicator
     */
    public boolean update(Map<String,Object> values) throws SQLException {
        if (readOnly) {
            Logger.error("Error: Entity "+getName()+" is read-only!");
            return false;
        }
        Instance instance = newInstance(values);
        return instance.update();
    }

    /** Delete a row based on (key) values.
     *
     * @param values the Map containing the values
     * @return success indicator
     */
    public boolean delete(Map<String,Object> values) throws SQLException {
        if (readOnly) {
            Logger.error("Error: Entity "+getName()+" is read-only!");
            return false;
        }
        Instance instance = newInstance(values);
        return instance.delete();
    }

    /** Delete a row based on the unique key string value.
     *
     * @param keyValue key value
     * @return success indicator
     */
    public boolean delete(String keyValue) throws SQLException {
        if (readOnly) {
            Logger.error("Error: Entity "+getName()+" is read-only!");
            return false;
        }
        if (keyCols.size()!=1) {
            if (keyCols.size()==0) throw new SQLException("Entity.delete: Error: Entity '"+name+"' has no primary key!");
            else throw new SQLException("Entity.delete: Error: Entity '"+name+"' has a multi-column primary key!");
        }
        Instance instance = newInstance();
        instance.put(keyCols.get(0),keyValue);
        return instance.delete();
    }

    /** Delete a row based on the unique key string value.
     *
     * @param keyValue key value
     * @return success indicator
     */
    public boolean delete(Number keyValue) throws SQLException {
        if (readOnly) {
            Logger.error("Error: Entity "+getName()+" is read-only!");
            return false;
        }
        if (keyCols.size()!=1) {
            if (keyCols.size()==0) throw new SQLException("Entity.delete: Error: Entity '"+name+"' has no primary key!");
            else throw new SQLException("Entity.delete: Error: Entity '"+name+"' has a multi-column primary key!");
        }
        Instance instance = newInstance();
        instance.put(keyCols.get(0),keyValue);
        return instance.delete();
    }


    /** Fetch an instance from key values stored in a List in natural order.
     *
     * @param values the List containing the key values
     * @return the fetched instance
     */
    public Instance fetch(List<Object> values) throws SQLException {
        if (values.size() != keyCols.size()) throw new SQLException("Entity.fetch: Error: Wrong number of values for '"+name+"' primary key!");
        Instance instance = null;
        // try in cache
        if (cachingMethod != Cache.NO_CACHE)
            instance = (Instance)cache.get(values.toArray());
        if (instance == null) {
            if (fetchQuery == null) buildFetchQuery();
            PooledPreparedStatement statement = db.prepare(fetchQuery);
            if (obfuscate) {
                values = new ArrayList<Object>(values);
                for(int col=0;col<keyColObfuscated.length;col++)
                    if(keyColObfuscated[col])
                        values.set(col,deobfuscate(values.get(col)));
            }
            instance = (Instance)statement.fetch(values,this);
        }
        return instance;
    }

    /** Fetch an instance from key values stored in a Map.
     *
     * @param values the Map containing the key values
     * @return the fetched instance
     */
    public Instance fetch(Map<String,Object> values) throws SQLException {
        Instance instance = null;
        /* check key values are present */
        for(String keyCol:keyCols) {
            if (!values.containsKey(keyCol)) {
                Logger.debug("tried to fetch an instance without key value '"+keyCol+"'...");
                return null;
            }
        }
        if (cachingMethod != Cache.NO_CACHE)
            // try in cache
            instance = (Instance)cache.get(buildKey(values));
        if (instance == null) {
            if (fetchQuery == null) buildFetchQuery();
            PooledPreparedStatement statement = db.prepare(fetchQuery);
            if (obfuscate) {
                Map<String,Object> map =  new HashMap<String,Object>();
                for(Object key:values.keySet()) {
                    Object value = values.get(key);
                    map.put( Database.adaptContextCase((String)key), isObfuscated((String)key) ? deobfuscate(value) : value );
                }
            }
            instance = (Instance)statement.fetch(values,this);
        }
        return instance;
    }

    /** Fetch an instance from its key value as a string.
     *
     * @param keyValue the key
     * @return the fetched instance
     */
    public Instance fetch(String keyValue) throws SQLException {
        if (keyCols.size()!=1) {
            if (keyCols.size()==0) throw new SQLException("Entity.fetch: Error: Entity '"+name+"' has no primary key!");
            else throw new SQLException("Entity.fetch: Error: Entity '"+name+"' has a multi-column primary key!");
        }
        Instance instance = null;
        // try in cache
        if (cachingMethod != Cache.NO_CACHE)
            // try in cache
            instance = (Instance)cache.get(new Object[] { keyValue });
        if (instance == null) {
            if (fetchQuery == null) buildFetchQuery();
            PooledPreparedStatement statement = db.prepare(fetchQuery);
            if (obfuscate && keyColObfuscated[0]) {
                keyValue = deobfuscate(keyValue);
            }
            List<String> params = new ArrayList<String>();
            params.add(keyValue);
            instance = (Instance)statement.fetch(params,this);
        }
        return instance;
    }

    /** Fetch an instance from its key value specified as a Number.
     *
     * @param keyValue the key
     * @return the fetched instance
     */
    public Instance fetch(Number keyValue) throws SQLException {
        if (keyCols.size()!=1) {
            if (keyCols.size()==0) throw new SQLException("Entity.fetch: Error: Entity '"+name+"' has no primary key!");
            else throw new SQLException("Entity.fetch: Error: Entity '"+name+"' has a multi-column primary key!");
        }
        Instance instance = null;
        // try in cache
        if (cachingMethod != Cache.NO_CACHE)
            // try in cache
            instance = (Instance)cache.get(new Object[] { keyValue });
        if (instance == null) {
            if (fetchQuery == null) buildFetchQuery();
            PooledPreparedStatement statement = db.prepare(fetchQuery);
            List<Number> params = new ArrayList<Number>();
            params.add(keyValue);
            if(obfuscate && keyColObfuscated[0]) {
                Logger.warn("fetch: column '"+columns.get(0)+"' is obfuscated, please use $db."+name+".fetch($db.obfuscate("+keyValue+"))");
                return null;
            }
            instance = (Instance)statement.fetch(params,this);
        }
        return instance;
    }

    /** Get the SQL query string used to fetch one instance of this query.
     *
     * @return the SLQ query
     */
    public String getFetchQuery() {
        if (fetchQuery == null) buildFetchQuery();
        return fetchQuery;
    }

    /** Build the SQL query used to fetch one instance of this query.
     */
    private void buildFetchQuery() {
        List<String> whereClause = new ArrayList<String>();
        for(String column:keyCols) {
            whereClause.add(aliasToColumn(column)+"=?");
        }
        fetchQuery = "select * from "+table+" where "+StringLists.join(whereClause," and ");
    }

    /** Issue a query to iterate though all instances of this entity.
     *
     * @return the resulting RowIterator
     */
    public RowIterator query() throws SQLException {
        return query(null,null);
    }

    /** Issue a query to iterate thought instances of this entity, with a facultative refining criteria and a facultative order by clause.
     *
     * @param refineCriteria a refining criteria or null to get all instances
     * @param order an 'order by' clause or null to get instances in their
     *     natural order
     * @return the resulting RowIterator
     */
    public RowIterator query(List refineCriteria,String order) throws SQLException {
        String query = "select * from "+ table;
        if (refineCriteria!=null) query = SqlUtil.refineQuery(query,refineCriteria);
        if (order!=null && order.length()>0) query = SqlUtil.orderQuery(query,order);
        return db.query(query,this);
    }

    /** Get the database connection.
     *
     * @return the database connection
     */
    public Database getDB() { return db; }

    /** Is this entity read-only or read-write?
     *
     * @return whether this entity is read-only or not
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    /** Set this entity to be read-only or read-write.
     *
     * @param readOnly the mode to switch to : true for read-only, false for
     *     read-write
     */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    /** set the name of the table mapped by this entity.
     *
     * @param table the table mapped by this entity
     *     read-write
     */
    public void setTableName(String table) {
        this.table = table;
    }

    /** Get the name of the mapped table.
     *
     * @return name of the mapped table
     */
    public String getTableName() {
        return table;
    }

    /** Indicates a column as being obfuscated.
     * @param columns list of obfuscated columns
     */
    public void setObfuscated(List<String> columns)
    {
        obfuscate = true;
        obfuscatedColumns = columns;
    }

    /** Returns whether the given column is obfuscated.
     * @param column the name of the column
     * @return a boolean indicating whether this column is obfuscated
     */
    public boolean isObfuscated(String column)
    {
        return obfuscate && obfuscatedColumns.contains(db.adaptCase(column));
    }

    /** Obfuscate given value.
     * @param value value to obfuscate
     *
     * @return obfuscated value
     */
    public String obfuscate(Object value)
    {
        return db.obfuscate(value);
    }

    /** Obfuscate this id value if needed.
     *  @param id id value
     *  @return filtered id value (that is, obfuscated if needed)
     */
    public Object filterID(Long id) {
        if (keyCols.size() == 1 && isObfuscated((String)keyCols.get(0))) return obfuscate(Long.valueOf(id));
        return Long.valueOf(id);
    }



    /** De-obfuscate given value.
     * @param value value to de-obfuscate
     *
     * @return obfuscated value
     */
    public String deobfuscate(Object value)
    {
        return db.deobfuscate(value);
    }

    /** Indicates a column as being localized.
     * @param columns list of localized columns
     */
    public void setLocalized(List columns)
    {
        localizedColumns = columns;
    }

    /** Returns whether the given column is obfuscated.
     * @param column the name of the column
     * @return a boolean indicating whether this column is obfuscated
     */
    public boolean isLocalized(String column)
    {
        return localizedColumns != null && localizedColumns.contains(db.adaptCase(column));
    }

    /** Does this entity have localized columns?
     */
    public boolean hasLocalizedColumns() {
        return localizedColumns != null && localizedColumns.size() > 0;
    }

    /**
     * Truncate validation error messages to a maximum number of characters.
     */
    private static final int MAX_DATA_DISPLAY_LENGTH = 40;

    /** Validate a set of values.
     */
    public boolean validate(Map<String,Object> row) throws SQLException {
        boolean ret = true;

        UserContext userContext = db.getUserContext();
        /* FIXME Is it a good choice to clear the user context now?
           We may want to validate several entities
           before displaying errors to the user.
           */
        userContext.clearValidationErrors();
        for(FieldConstraintInfo info:constraints) {
            Object data = row.get(info.column);
            if (!info.constraint.validate(data, userContext.getLocale())) {
                if(userContext != null) {
                    String stringData = data.toString();
                    String formatted = (data==null || stringData.length() == 0 ? "empty value" : stringData);
                    if (formatted.length() > MAX_DATA_DISPLAY_LENGTH) {
                        formatted = formatted.substring(0,MAX_DATA_DISPLAY_LENGTH)+"...";
                    }
                    formatted = StringEscapeUtils.escapeHtml(formatted);
                    userContext.addValidationError(userContext.localize(info.constraint.getMessage(),info.column,formatted));
                }
                ret = false;
            }
        }
        return ret;
    }

    /**
     * Check for the existence of an imported key with the same columns.
     * @param pkEntity primary key entity
     * @param fkCols foreign key columns
     * @return previously defined imported key, if any
     */
    public ImportedKey findImportedKey(Entity pkEntity,List<String> fkCols) {
        for(Map.Entry<String,Attribute> entry:attributeMap.entrySet()) {
            Attribute attribute = entry.getValue();
            if (!(attribute instanceof ImportedKey)) {
                continue;
            }
            ImportedKey imported = (ImportedKey)attribute;
            if (imported.getResultEntity().equals(pkEntity.getName())
                && ( imported.getFKCols() == null || imported.getFKCols().equals(fkCols)) ) {
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
    public ExportedKey findExportedKey(Entity fkEntity,List<String> fkCols) {
        for(Map.Entry<String,Attribute> entry:attributeMap.entrySet()) {
            Attribute attribute = entry.getValue();
            if (!(attribute instanceof ExportedKey)) {
                continue;
            }
            ExportedKey exported = (ExportedKey)attribute;
            if (exported.getResultEntity().equals(fkEntity.getName())
                && ( exported.getFKCols() == null || exported.getFKCols().equals(fkCols) )) {
                return exported;
            }
        }
        return null;
    }

    /** Name.
     */
    private String name = null;
    /** Table.
     */
    private String table = null;
    /** Column names in natural order.
     */
    private List<String> columns = new ArrayList<String>(); // list<String>
    /** Key column names in natural order.
     */
    private List<String> keyCols = new ArrayList<String>();
    /** Whether to obfuscate something.
     */
    private boolean obfuscate = false;
    /** Names of obfuscated columns.
     */
    private List<String> obfuscatedColumns = null;
    /** Obfuscation status of key columns.
     */
    private boolean keyColObfuscated[] = null;
    /** Localized columns.
     */
    private List localizedColumns = null;
    /** Attributes of this entity.
     */

    /**
     * Alias by column map.
     */
    private Map<String,String> aliasByColumn = new HashMap<String,String>();

    /**
     * Column by alias map.
     */
    private Map<String,String> columnByAlias = new HashMap<String,String>();

    /** Attribute map.
     *
     */
    private Map<String,Attribute> attributeMap = new HashMap<String,Attribute>();
    /** action map.
     */
    private Map<String,Action> actionMap = new HashMap<String,Action>();
    /** the java class to use to realize this instance.
     */
    private Class instanceClass = null;
    /** the SQL query used to fetch one instance of this entity.
     */
    private String fetchQuery = null;
    /** whether this entity is read-only or not.
     */
    private boolean readOnly;
    /** the database connection.
     */
    private Database db = null;
    /** the caching method.
     */
    private int cachingMethod = 0;
    /** the cache.
     */
    private Cache cache = null;

    /** constraints.
     */

    private class FieldConstraintInfo {
       /** Field constraint info constructor.
        *
        * @param col column name
        * @param constr constraint
        */
        private FieldConstraintInfo(String col,FieldConstraint constr) {
            column = col;
            constraint = constr;
        }
        /**
         * column.
         */
        private String column;
        /**
         * constraint.
         */
        private FieldConstraint constraint;
    }

    /**
     * Constraint by column name map.
     */
    private List<FieldConstraintInfo> constraints = new ArrayList<FieldConstraintInfo>();
}
