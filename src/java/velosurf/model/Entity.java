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
import java.sql.Types;
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
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
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
    public void addColumn(String colName,int sqlType) {
        colName = db.adaptCase(colName);
        columns.add(colName);
        types.put(colName,sqlType);
        /* if (colnames as aliases) */ aliases.put(colName,colName);
    }

    /**
     * Add a column alias.
     * @param alias alias
     * @param column column
     */
    public void addAlias(String alias, String column) {
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
    public String resolveName(String alias) {
        alias = db.adaptCase(alias);
        String name = aliases.get(alias);
        return name == null ? alias : name;
    }

    /** Add a key column to the sequential list of the key columns. Called during the reverse-engeenering of the database.
     *
     * @param colName name of the key column
     */
    public void addPKColumn(String colName) {
        /* remember the alias */
        keyCols.add(colName);
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
            Logger.trace("defined attribute "+this.name+"."+name+" = "+attribute);
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
        column = resolveName(column);
        Logger.trace("adding constraint on column "+Database.adaptContextCase(getName())+"."+column+": "+constraint);
        List<FieldConstraint> list = constraints.get(column);
        if (list == null) {
            list = new ArrayList<FieldConstraint>();
            constraints.put(column,list);
        }
        list.add(constraint);
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
        return newInstance(values,false);
    }

    /** Build a new instance from a Map object.
     *
     * @param values the Map object containing the values
     * @param useSQLnames map keys use SQL column names that must be translated to aliases
     * @return the newly created instance
     */
    public Instance newInstance(Map<String,Object> values,boolean useSQLnames) {
        try {
            Instance result = newInstance();
            extractColumnValues(values,result,useSQLnames);
            if (cachingMethod != Cache.NO_CACHE) {
                cache.put(buildKey(result),result);
            }
            return result;
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
     * @param SQLNames the source uses SQL names
     */
    private void extractColumnValues(Map<String,Object> source,Map<String,Object> target,boolean SQLNames) throws SQLException {
        /* TODO: cache a case-insensitive version of the columns list and iterate on source keys, with equalsIgnoreCase (or more efficient) funtion */
        /* We use keySet and not entrySet here because if the source map is a ReadOnlyMap, entrySet is not available */
        for(String key:source.keySet()) {

            /* resove anyway */
            String col = resolveName(key);
            /* this is more or less a hack: we do filter columns
               only when SQLNames is fale. The purpose of this
               is to allow additionnal fields in SQL attributes
               returning rowsets of entities. */

            if(!SQLNames && !isColumn(col)) {
                continue;
            }

            Object val = source.get(key);
            if (val == null || (val.getClass().isArray())) {
                continue;
            }
            target.put(col,val);
        }
    }

    /** Build the key for the Cache from a Map.
     *
     * @param values the Map containing all values (unaliased)
     * @exception SQLException the getter of the Map throws an
     *     SQLException
     * @return an array containing all key values
     */
    private Object buildKey(Map<String,Object> values) throws SQLException {
        Object [] key = new Object[keyCols.size()];
        int c=0;
        for(String keycol:keyCols) {
            key[c++] = values.get(keycol);
        }
        return key;
    }

    /** Getter for the name of this entity.
     *
     * @return the name of the entity
     */
    public String getName() { 
        return name; 
    }

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
    
    public boolean isColumn(String name) {
        return columns.contains(name);
    }

    /** Check if the provided map contains all key columns
     *
     * @param values map of values to check
     * @return true if all key columns are present
     */
/* not used     
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
        if(keyCols.size() == 0) {
            throw new SQLException("entity "+name+": cannot fetch an instance for an entity without key!");
        }
        Instance instance = null;
        /* extract key values */
        Object key[] = new Object[keyCols.size()];
        int n = 0;
        for(Map.Entry<String,Object> entry:values.entrySet()) {
            String col = resolveName(entry.getKey());
            int i = keyCols.indexOf(col);
            if (i>0) {
                key[i] = entry.getValue();
                n++;
            }
        }
        if (n != keyCols.size() ) {
            throw new SQLException("entity "+name+".fetch(): missing key values!");
        }
        if (cachingMethod != Cache.NO_CACHE) {
            // try in cache
            instance = (Instance)cache.get(key);
        }
        if (instance == null) {
            if (fetchQuery == null) buildFetchQuery();
            PooledPreparedStatement statement = db.prepare(fetchQuery);
            if (obfuscate) {
                for(int c=0;c<keyCols.size();c++) {
                    if (isObfuscated(keyCols.get(c))) {
                        key[c] = deobfuscate(key[c]);
                    }
                }
            }
            instance = (Instance)statement.fetch(Arrays.asList(key),this);
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
            whereClause.add(column+"=?");
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
        List<ValidationError> errors = new ArrayList<ValidationError>();
        for(Map.Entry<String,Object> entry:row.entrySet()) {
            String col = resolveName(entry.getKey());
            Object data = entry.getValue();
            List<FieldConstraint> list = constraints.get(col);
            if(list != null) {
                for(FieldConstraint constraint:list) {
                    if (!constraint.validate(data, userContext.getLocale())) {
                        String stringData = data.toString();
                        String formatted = (data==null || stringData.length() == 0 ? "empty value" : stringData);
                        if (formatted.length() > MAX_DATA_DISPLAY_LENGTH) {
                            formatted = formatted.substring(0,MAX_DATA_DISPLAY_LENGTH)+"...";
                        }
                        formatted = StringEscapeUtils.escapeHtml(formatted);
                        errors.add(new ValidationError(col,userContext.localize(constraint.getMessage(),Database.adaptContextCase(col),formatted)));
                        ret = false;
                    }
                }
            }
        }
        if(errors.size()>0) {
            /* sort in columns natural order... better than nothing.
               The ideal ordering would be the order of the form fields,
               but it is unreachable. */
            Collections.sort(errors);
            for(ValidationError error:errors) {
                Logger.trace("validation: new message: "+error.message);
                userContext.addValidationError(error.message);
            }
        }
        return ret;
    }
    
    class ValidationError implements Comparable<ValidationError> {
        ValidationError(String column,String message) {
            index = columns.indexOf(column);
            this.message = message;
        }
        public int compareTo(ValidationError cmp) {
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

    public Object filterIncomingValue(String column,Object value) {
        if(value == null) {
            return null;
        }
        /* for now, only filter boolean values */
        Integer type = types.get(column);
        if (type == null) {
            return value;
        }
        if(type == Types.BOOLEAN || type == Types.BIT) {
            if(String.class.isAssignableFrom(value.getClass())) {
                String s = (String)value;
                if("true".equalsIgnoreCase(s) || "on".equalsIgnoreCase(s) || "1".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s)) {
                    value = new Boolean(true);
                } else {
                    value = new Boolean(false);
                }
            }
        }
        return value;
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
    /** Column types
     */
    private Map<String,Integer> types = new HashMap<String,Integer>();
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
     * Column by alias map.
     */
    private Map<String,String> aliases = new HashMap<String,String>();

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
    
    /** Constraint by column name map.
     */
    private Map<String,List<FieldConstraint>> constraints = new HashMap<String,List<FieldConstraint>>();
}
