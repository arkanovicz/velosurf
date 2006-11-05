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
import velosurf.sql.ReadOnlyMap;
import velosurf.sql.Database;
import velosurf.sql.ReadOnlyWrapper;
import velosurf.sql.PooledPreparedStatement;
import velosurf.sql.SqlUtil;
import velosurf.util.Logger;
import velosurf.util.StringLists;
import velosurf.util.UserContext;
import velosurf.validation.FieldConstraint;

import org.jdom.Element;
import org.apache.commons.lang.StringEscapeUtils;

/** The Entity class represents an entity in the data model. It should not be constructed directly.
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 */
public class Entity
{
    /** Constructor reserved for the framework
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

    /** add a column at the end of the sequential list of named columns (called during the reverse engeenering of the database)
     *
     * @param colName column name
     */
    public void addColumn(String colName) {
        /* remember the alias */
        columns.add(columnToAlias(colName));
    }

    public void addAlias(String alias, String column) {
        aliasByColumn.put(column,alias);
        columnByAlias.put(alias,column);
    }

    public String aliasToColumn(String alias) {
        String col = columnByAlias.get(alias);
        return col == null ? alias : col;
    }

    public List<String> aliasToColumn(List<String> aliases) {
        if(columnByAlias.size() > 0) {
            List<String> ret = new ArrayList<String>();
            for(String alias:aliases) {
                ret.add(aliasToColumn(alias));
            }
            return ret;
        } else return aliases;
    }

    public String columnToAlias(String column) {
        String alias = aliasByColumn.get(column);
        return alias == null ? column : alias;
    }


    /** adds a key column to the sequential list of the key columns (called during the reverse-engeenering of the database)
     *
     * @param colName name of the key column
     */
    public void addPKColumn(String colName) {
        /* remember the alias */
        keyCols.add(columnToAlias(colName));
    }

    /** add a new attribute
     *
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

    /** Get a named attribute of this entity
     *
     * @param property attribute name
     * @return the attribute
     */
    public Attribute getAttribute(String property) {
        return (Attribute)attributeMap.get(db.adaptCase(property));
    }

    public void addAction(Action action) {
        String name = action.getName();
        actionMap.put(db.adaptCase(name),action);
        Logger.debug("action "+this.name+"."+name+" = "+action);
    }

    /** get the named action from this entity
     *
     * @param property action name
     * @return the action
     */
    public Action getAction(String property) {
        return (Action)actionMap.get(db.adaptCase(property));
    }

    /** Specify a custom class to use when instanciating this entity
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

    /** Specify the caching method, see {@link Cache} for allowed constants.
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

    public void addConstraint(String column,FieldConstraint constraint) {;
        Logger.trace("adding constraint on column "+Database.adaptContextCase(getName())+"."+column+": "+constraint);
        constraints.add(new FieldConstraintInfo(column,constraint));
    }

    /** Used by the framework to notify this entity that its reverse enginering is over
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

    /** Clear the cache
     */
    public void clearCache() {
        if (cache != null) cache.clear();
    }

    /** create a new realisation of this entity
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

    /** builds a new instance from values contained in a Map
     *
     * @param values the Map containing the values
     * @return the newly created instance
     */
    public Instance newInstance(Map values) {
        return newInstance(new ReadOnlyWrapper(values));
    }

    /** build a new instance from a DataAccessor object
     *
     * @param values the DataAccessor object containing the values
     * @return the newly created instance
     */
    public Instance newInstance(ReadOnlyMap values) {
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


    /** get an instance whose values are in a map (by default, do not update instance values with map values if the instance is found in the cache)
     *
     * @param values the map containing the key values
     * @return the instance
     */
    public Instance getInstance(Map values) {
        return getInstance(new ReadOnlyWrapper(values),false);
    }

    /** get an instance whose values are in a map
     *
     * @param values the map containing the values
     * @param updateValues whether the instance should be updated from the
     *      map if found in the cache
     * @return the instance
     */
    public Instance getInstance(Map values,boolean updateValues) {
        return getInstance(new ReadOnlyWrapper(values),updateValues);
    }

    /** get an instance from its values contained in a DataAccessor object (by default, update all fields based on the values in the DataAccessor if the instance has been found in the cache)
     *
     * @param values the DataAccessor object containing the values
     * @return the instance
     */
    public Instance getInstance(ReadOnlyMap values) {
        return getInstance(values,false);
    }

    /** get an instance from a DataAccessor object
     *
     * @param values the DataAccessor object containing the values
     * @param updateValues whether all values are to be read from the
     *     ReadOnlyMap if the instance has been found in the cache
     * @return the instance
     */
    public Instance getInstance(ReadOnlyMap values,boolean updateValues) {
        Instance ret = null;
        try {
            if (cachingMethod != Cache.NO_CACHE)
                // try in cache
                ret = (Instance)cache.get(buildKey(values));
            if (ret == null) ret = newInstance(values);
            else if    (updateValues) extractColumnValues(values,ret);
            return ret;
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }

    public void invalidateInstance(ReadOnlyMap instance) throws SQLException {
        if (cachingMethod != Cache.NO_CACHE) {
            cache.invalidate(buildKey(instance));
        }
    }

    /** extract column values from an input DataAccessor source and store result in target
     *
     * @param source ReadOnlyMap source object
     * @param target Map target object
     */
    protected void extractColumnValues(ReadOnlyMap source,Map target) throws SQLException {
        for(Iterator i=columns.iterator();i.hasNext();) {
            String col = (String)i.next();
            Object val = source.get(col);
            if (val == null) {
                switch(db.getCaseSensivity()) {
                    // try with different letter case
                    /* TODO review */
                    case Database.UPPERCASE:
                        val = source.get(col.toLowerCase());
                        break;
                    case Database.LOWERCASE:
                        val = source.get(col.toUpperCase());
                        break;
                }
            }
            // avoid null and multivalued attributes
            if (val != null && !(val.getClass().isArray())) {
                target.put(col,val);
            }
        }
    }

    /** build the key for the Cache from a DataAccessor
     *
     * @param values the DataAccessor containing all values
     * @exception SQLException the getter of the DataAccessor throws an
     *     SQLException
     * @return an array containing all key values
     */
    protected Object buildKey(ReadOnlyMap values) throws SQLException {

        // build key
        Object [] key = new Object[keyCols.size()];
        int c=0;
        for(Iterator i = keyCols.iterator(); i.hasNext();)
            key[c++] = values.get(i.next());
        return key;
    }


    /** getter for the name of this entity
     *
     * @return the name of the entity
     */
    public String getName() { return name; }

    /** set the name of this entity ** DO NOT USE DIRECTLY **
     *
     * @param name new name
     */
    public void rename(String name) { this.name = name; }

    /** getter for the list of key column names
     *
     * @return the list of key column names
     */
    public List<String> getPKCols() {
        return keyCols;
    }

    /** getter for the list of column names
     *
     * @return the list of column names
     */
    public List<String> getColumns() {
        return columns;
    }

    /** insert a new row based on values of a map
     *
     * @param values the  Map object containing the values
     * @return success indicator
     */
    public boolean insert(ReadOnlyMap values) throws SQLException {
        if (readOnly) {
            Logger.error("Error: Entity "+getName()+" is read-only!");
            return false;
        }
        // don't use newInstance since we don't want user classes or cache
        Instance instance = new Instance(this);
        extractColumnValues(values,instance);
        return instance.insert();
    }

    /** update a row based on a set of values that must contain kety values
     *
     * @param values the Map object containing the values
     * @return success indicator
     */
    public boolean update(ReadOnlyMap values) {
        if (readOnly) {
            Logger.error("Error: Entity "+getName()+" is read-only!");
            return false;
        }
        Instance instance = newInstance(values);
        return instance.update();
    }

    /** delete a row based on (key) values
     *
     * @param values the Map containing the values
     * @return success indicator
     */
    public boolean delete(ReadOnlyMap values) {
        if (readOnly) {
            Logger.error("Error: Entity "+getName()+" is read-only!");
            return false;
        }
        Instance instance = newInstance(values);
        return instance.delete();
    }

    /** fetch an instance from key values stored in a List in natural order
     *
     * @param values the List containing the key values
     * @return the fetched instance
     */
    public Instance fetch(List values) throws SQLException {
        if (values.size() != keyCols.size()) throw new SQLException("Entity.fetch: Error: Wrong number of values for '"+name+"' primary key!");
        Instance instance = null;
        // try in cache
        if (cachingMethod != Cache.NO_CACHE)
            instance = (Instance)cache.get(values.toArray());
        if (instance == null) {
            if (fetchQuery == null) buildFetchQuery();
            PooledPreparedStatement statement = db.prepare(fetchQuery);
            if (obfuscate) {
                values = new ArrayList(values);
                for(int col=0;col<keyColObfuscated.length;col++)
                    if(keyColObfuscated[col])
                        values.set(col,deobfuscate(values.get(col)));
            }
            instance = (Instance)statement.fetch(values,this);
        }
        return instance;
    }

    /** fetch an instance from key values stored in a Map
     *
     * @param values the Map containing the key values
     * @return the fetched instance
     */
    public Instance fetch(ReadOnlyMap values) throws SQLException {
        Instance instance = null;
        // try in cache
        if (cachingMethod != Cache.NO_CACHE)
            // try in cache
            instance = (Instance)cache.get(buildKey(values));
        if (instance == null) {
            if (fetchQuery == null) buildFetchQuery();
            PooledPreparedStatement statement = db.prepare(fetchQuery);
            if (obfuscate) {
                Map map =  new HashMap();
                for(Object key:values.keySet()) {
                    Object value = values.get(key);
                    map.put( Database.adaptContextCase((String)key), isObfuscated((String)key) ? deobfuscate(value) : value );
                }
                values = new ReadOnlyWrapper(map);
            }
            instance = (Instance)statement.fetch(values,this);
        }
        return instance;
    }

    /** fetch an instance from its key value as a string
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
            ArrayList params = new ArrayList();
            params.add(keyValue);
            instance = (Instance)statement.fetch(params,this);
        }
        return instance;
    }

    /** fetch an instance from its key value specified as a Number
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
            ArrayList params = new ArrayList();
            params.add(keyValue);
            if(obfuscate && keyColObfuscated[0]) {
                Logger.warn("fetch: column '"+columns.get(0)+"' is obfuscated, please use $db."+name+".fetch($db.obfuscate("+keyValue+"))");
                return null;
            }
            instance = (Instance)statement.fetch(params,this);
        }
        return instance;
    }

    /** get the SQL query string used to fetch one instance of this query
     *
     * @return the SLQ query
     */
    public String getFetchQuery() {
        if (fetchQuery == null) buildFetchQuery();
        return fetchQuery;
    }

    /** build the SQL query used to fetch one instance of this query
     */
    protected void buildFetchQuery() {
        ArrayList whereClause = new ArrayList();
        for(String column:keyCols) {
            whereClause.add(aliasToColumn(column)+"=?");
        }
        fetchQuery = "select * from "+table+" where "+StringLists.join(whereClause," and ");
    }

    /** issue a query to iterate though all instances of this entity
     *
     * @return the resulting RowIterator
     */
    public RowIterator query() throws SQLException {
        return query(null,null);
    }

    /** issue a query to iterate thought instances of this entity, with a facultative refining criteria and a facultative order by clause
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

//    public boolean hasColumn
//    public List getColumns

    /** get the database connection
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

    /** set this entity to be read-only or read-write
     *
     * @param readOnly the mode to switch to : true for read-only, false for
     *     read-write
     */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    /** set the name of the table mapped by this entity
     *
     * @param table the table mapped by this entity
     *     read-write
     */
    public void setTableName(String table) {
        this.table = table;
    }

    /** get the name of the mapped table
     *
     * @return name of the mapped table
     */
    public String getTableName() {
        return table;
    }

    /** indicates a column as being obfuscated
     * @param columns list of obfuscated columns
     */
    public void setObfuscated(List columns)
    {
        obfuscate = true;
        obfuscatedColumns = columns;
    }

    /** returns whether the given column is obfuscated
     * @param column the name of the column
     * @return a boolean indicating whether this column is obfuscated
     */
    public boolean isObfuscated(String column)
    {
        return obfuscate && obfuscatedColumns.contains(db.adaptCase(column));
    }

    /** obfuscate given value
     * @param value value to obfuscate
     *
     * @return obfuscated value
     */
    public String obfuscate(Object value)
    {
        return db.obfuscate(value);
    }

    /** obfuscate this id value if needed
     *
     */
    public Object filterID(Long id) {
        if (keyCols.size() == 1 && isObfuscated((String)keyCols.get(0))) return obfuscate(Long.valueOf(id));
        return Long.valueOf(id);
    }



    /** de-obfuscate given value
     * @param value value to de-obfuscate
     *
     * @return obfuscated value
     */
    public String deobfuscate(Object value)
    {
        return db.deobfuscate(value);
    }

    /** indicates a column as being localized
     * @param columns list of localized columns
     */
    public void setLocalized(List columns)
    {
        localizedColumns = columns;
    }

    /** returns whether the given column is obfuscated
     * @param column the name of the column
     * @return a boolean indicating whether this column is obfuscated
     */
    public boolean isLocalized(String column)
    {
        return localizedColumns != null && localizedColumns.contains(db.adaptCase(column));
    }

    /** does this entity have localized columns ?
     */
    public boolean hasLocalizedColumns() {
        return localizedColumns != null && localizedColumns.size() > 0;
    }

    /** validate a set of values
     */
    public boolean validate(ReadOnlyMap row,UserContext userContext) throws SQLException {
        boolean ret = true;
        if(userContext != null) {
            /* Is it a good choice to clear it now?
               We may want to validate several entities
               before displaying errors to the user.
               */
            userContext.clearValidationErrors();
        }
        for(FieldConstraintInfo info:constraints) {
            Object data = row.get(info.column);
            if (!info.constraint.validate(data, userContext.getLocale())) {
                if(userContext != null) {
                    /* TODO: parametrized localization */
                    /* TODO: move formatting elsewhere! Define 30 in a constant! donnot call toString() twice on data!*/
                    String formatted = (data==null || data.toString().length() == 0 ? "empty value" : data.toString());
                    if (formatted.length()>30) {
                        formatted = formatted.substring(0,30)+"...";
                    }
                    formatted = StringEscapeUtils.escapeHtml(formatted);
                    userContext.addValidationError(userContext.localize(info.constraint.getMessage(),info.column,formatted));
                }
                ret = false;
            }
        }
        return ret;
    }

    public ImportedKey findImportedKey(Entity pkEntity,List<String> fkCols) {
        for(Map.Entry entry:(Set<Map.Entry>)attributeMap.entrySet()) {
            Attribute attribute = (Attribute)entry.getValue();
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

    public ExportedKey findExportedKey(Entity fkEntity,List<String> fkCols) {
        for(Map.Entry entry:(Set<Map.Entry>)attributeMap.entrySet()) {
            Attribute attribute = (Attribute)entry.getValue();
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

    /** name
     */
    protected String name = null;
    /** table
     */
    protected String table = null;
    /** column names in natural order
     */
    protected List<String> columns = new ArrayList(); // list<String>
    /** key column names in natural order
     */
    protected List<String> keyCols = new ArrayList();
    /** whether to obfuscate something
     */
    protected boolean obfuscate = false;
    /** names of obfuscated columns
     */
    protected List<String> obfuscatedColumns = null;
    /** obfuscation status of key columns
     */
    protected boolean keyColObfuscated[] = null;
    /** localized columns
     */
    protected List localizedColumns = null;
    /** attributes of this entity
     */

    protected Map<String,String> aliasByColumn = new HashMap<String,String>();

    protected Map<String,String> columnByAlias = new HashMap<String,String>();

    protected Map attributeMap = new HashMap(); // map<Name,Attribute>
    /** actions of this entity
     */
    protected Map actionMap = new HashMap();
    /** the java class to use to realize this instance
     */
    protected Class instanceClass = null;
    /** the SQL query used to fetch one instance of this entity
     */
    protected String fetchQuery = null;
    /** whether this entity is read-only or not
     */
    protected boolean readOnly;
    /** the database connection
     */
    protected Database db = null;
    /** the caching method
     */
    protected int cachingMethod = 0;
    /** the cache
     */
    protected Cache cache = null;

    /** constraints
     */

    private class FieldConstraintInfo {
        protected FieldConstraintInfo(String col,FieldConstraint constr) {
            column = col;
            constraint = constr;
        }
        protected String column;
        protected FieldConstraint constraint;
    }

    private List<FieldConstraintInfo> constraints = new ArrayList<FieldConstraintInfo>();
}
