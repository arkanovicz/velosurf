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
     * @param inDB database connection
     * @param inName entity name
     * @param readOnly access mode (read-write or read-only)
     * @param inCachingMethod caching method to be used
     */
    public Entity(Database inDB,String inName,boolean readOnly,int inCachingMethod) {
        mDB = inDB;
        mName = inName;
        mTable = inName; // default mapped table has same name
        mReadOnly = readOnly;
        mCachingMethod = inCachingMethod;
        if (mCachingMethod != Cache.NO_CACHE) mCache = new Cache(mCachingMethod);
        mInstanceClass = Instance.class;
    }

    /** add a column at the end of the sequential list of named columns (called during the reverse engeenering of the database)
     *
     * @param inColName column name
     */
    public void addColumn(String inColName) {
        /* remember the alias */
        mColumns.add(columnToAlias(inColName));
    }

    public void addAlias(String alias, String column) {
        mAliasByColumn.put(column,alias);
        mColumnByAlias.put(alias,column);
    }

    public String aliasToColumn(String alias) {
        String col = mColumnByAlias.get(alias);
        return col == null ? alias : col;
    }

    public List<String> aliasToColumn(List<String> aliases) {
        if(mColumnByAlias.size() > 0) {
            List<String> ret = new ArrayList<String>();
            for(String alias:aliases) {
                ret.add(aliasToColumn(alias));
            }
            return ret;
        } else return aliases;
    }

    public String columnToAlias(String column) {
        String alias = mAliasByColumn.get(column);
        return alias == null ? column : alias;
    }


    /** adds a key column to the sequential list of the key columns (called during the reverse-engeenering of the database)
     *
     * @param inColName name of the key column
     */
    public void addPKColumn(String inColName) {
        /* remember the alias */
        mKeys.add(columnToAlias(inColName));
    }

    /** add a new attribute
     *
     */
    public void addAttribute(Attribute attribute) {
        String name = attribute.getName();
        if(mAttributeMap.containsKey(name)) {
            Logger.warn("Ignoring second definition for attribute "+name+"!");
        } else {
            mAttributeMap.put(mDB.adaptCase(name),attribute);
            Logger.debug("defined attribute "+mName+"."+name+" = "+attribute);
        }
    }

    /** Get a named attribute of this entity
     *
     * @param inProperty attribute name
     * @return the attribute
     */
    public Attribute getAttribute(String inProperty) {
        return (Attribute)mAttributeMap.get(mDB.adaptCase(inProperty));
    }

    public void addAction(Action action) {
        String name = action.getName();
        mActionMap.put(mDB.adaptCase(name),action);
        Logger.debug("action "+mName+"."+name+" = "+action);
    }

    /** get the named action from this entity
     *
     * @param inProperty action name
     * @return the action
     */
    public Action getAction(String inProperty) {
        return (Action)mActionMap.get(mDB.adaptCase(inProperty));
    }

    /** Specify a custom class to use when instanciating this entity
     *
     * @param inClassName the java class name
     */
    public void setInstanceClass(String inClassName) {
        try {
            mInstanceClass = Class.forName(inClassName);
        }
        catch (Exception e) {
            Logger.log(e);
        }
    }

    /** Specify the caching method, see {@link Cache} for allowed constants.
     *
     * @param inCaching Caching method
     */
    public void setCachingMethod(int inCaching) {
        if (mCachingMethod != inCaching) {
            mCachingMethod = inCaching;
            if (mCachingMethod == Cache.NO_CACHE) mCache = null;
            else mCache = new Cache(mCachingMethod);
        }
    }

    public void addConstraint(String column,FieldConstraint constraint) {;
        Logger.trace("adding constraint on column "+Database.adaptContextCase(getName())+"."+column+": "+constraint);
        mConstraints.add(new FieldConstraintInfo(column,constraint));
    }

    /** Used by the framework to notify this entity that its reverse enginering is over
     */
    public void reverseEnginered() {
        if (mObfuscate && mKeys.size()>0) {
            mKeyColObfuscated = new boolean[mKeys.size()];
            Iterator key = mKeys.iterator();
            int i=0;
            for (;key.hasNext();i++)
                mKeyColObfuscated[i] = mObfuscatedColumns.contains(key.next());
        }
        /* fills the cache for the full caching method */
        if(mCachingMethod == Cache.FULL_CACHE) {
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
        if (mCache != null) mCache.clear();
    }

    /** create a new realisation of this entity
     *
     * @return the newly created instance
     */
    public Instance newInstance() {
        Instance result = null;
        try {
            if (Instance.class.isAssignableFrom(mInstanceClass)) {
                try {
                    result = (Instance)mInstanceClass.newInstance();
                    result.initialize(this);
                } catch (Exception e) {
                    Constructor instanceConstructor = mInstanceClass.getConstructor(new Class[] {Entity.class} );
                    result = (Instance)instanceConstructor.newInstance(new Object[] { this });
                }
            } else {
                result = new ExternalObjectWrapper(this,mInstanceClass.newInstance());
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
     * @param inValues the Map containing the values
     * @return the newly created instance
     */
    public Instance newInstance(Map inValues) {
        return newInstance(new ReadOnlyWrapper(inValues));
    }

    /** build a new instance from a DataAccessor object
     *
     * @param inValues the DataAccessor object containing the values
     * @return the newly created instance
     */
    public Instance newInstance(ReadOnlyMap inValues) {
        try {
            Instance result = newInstance();
            extractColumnValues(inValues,result);
            if (mCachingMethod != Cache.NO_CACHE) mCache.put(buildKey(inValues),result);
            return result;
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }


    /** get an instance whose values are in a map (by default, do not update instance values with map values if the instance is found in the cache)
     *
     * @param inValues the map containing the key values
     * @return the instance
     */
    public Instance getInstance(Map inValues) {
        return getInstance(new ReadOnlyWrapper(inValues),false);
    }

    /** get an instance whose values are in a map
     *
     * @param inValues the map containing the values
     * @param inUpdateValues whether the instance should be updated from the
     *      map if found in the cache
     * @return the instance
     */
    public Instance getInstance(Map inValues,boolean inUpdateValues) {
        return getInstance(new ReadOnlyWrapper(inValues),inUpdateValues);
    }

    /** get an instance from its values contained in a DataAccessor object (by default, update all fields based on the values in the DataAccessor if the instance has been found in the cache)
     *
     * @param inValues the DataAccessor object containing the values
     * @return the instance
     */
    public Instance getInstance(ReadOnlyMap inValues) {
        return getInstance(inValues,false);
    }

    /** get an instance from a DataAccessor object
     *
     * @param inValues the DataAccessor object containing the values
     * @param inUpdateValues whether all values are to be read from the
     *     DataAccessor if the instance has been found in the cache
     * @return the instance
     */
    public Instance getInstance(ReadOnlyMap inValues,boolean inUpdateValues) {
        Instance ret = null;
        try {
            if (mCachingMethod != Cache.NO_CACHE)
                // try in cache
                ret = (Instance)mCache.get(buildKey(inValues));
            if (ret == null) ret = newInstance(inValues);
            else if    (inUpdateValues) extractColumnValues(inValues,ret);
            return ret;
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }

    public void invalidateInstance(ReadOnlyMap instance) throws SQLException {
        if (mCachingMethod != Cache.NO_CACHE) {
            mCache.invalidate(buildKey(instance));
        }
    }

    /** extract column values from an input DataAccessor source and store result in inTarget
     *
     * @param inSource DataAccessor source object
     * @param inTarget Map target object
     */
    protected void extractColumnValues(ReadOnlyMap inSource,Map inTarget) throws SQLException {
        for(Iterator i=mColumns.iterator();i.hasNext();) {
            String col = (String)i.next();
            Object val = inSource.get(col);
            if (val == null) {
                switch(mDB.getCaseSensivity()) {
                    // try with different letter case
                    /* TODO review */
                    case Database.UPPERCASE:
                        val = inSource.get(col.toLowerCase());
                        break;
                    case Database.LOWERCASE:
                        val = inSource.get(col.toUpperCase());
                        break;
                }
            }
            // avoid null and multivalued attributes
            if (val != null && !(val.getClass().isArray())) {
                inTarget.put(col,val);
            }
        }
    }

    /** build the key for the Cache from a DataAccessor
     *
     * @param inValues the DataAccessor containing all values
     * @exception SQLException the getter of the DataAccessor throws an
     *     SQLException
     * @return an array containing all key values
     */
    protected Object buildKey(ReadOnlyMap inValues) throws SQLException {

        // build key
        Object [] key = new Object[mKeys.size()];
        int c=0;
        for(Iterator i = mKeys.iterator(); i.hasNext();)
            key[c++] = inValues.get(i.next());
        return key;
    }


    /** getter for the name of this entity
     *
     * @return the name of the entity
     */
    public String getName() { return mName; }

    /** set the name of this entity ** DO NOT USE DIRECTLY **
     *
     * @param inName new name
     */
    public void rename(String inName) { mName = inName; }

    /** getter for the list of key column names
     *
     * @return the list of key column names
     */
    public List<String> getPKCols() {
        return mKeys;
    }

    /** getter for the list of column names
     *
     * @return the list of column names
     */
    public List<String> getColumns() {
        return mColumns;
    }

    /** insert a new row based on values of a map
     *
     * @param inValues the  Map object containing the values
     * @return success indicator
     */
    public boolean insert(ReadOnlyMap inValues) throws SQLException {
        if (mReadOnly) {
            Logger.error("Error: Entity "+getName()+" is read-only!");
            return false;
        }
        // don't use newInstance since we don't want user classes or cache
        Instance instance = new Instance(this);
        extractColumnValues(inValues,instance);
        return instance.insert();
    }

    /** update a row based on a set of values that must contain kety values
     *
     * @param inValues the Map object containing the values
     * @return success indicator
     */
    public boolean update(ReadOnlyMap inValues) {
        if (mReadOnly) {
            Logger.error("Error: Entity "+getName()+" is read-only!");
            return false;
        }
        Instance instance = newInstance(inValues);
        return instance.update();
    }

    /** delete a row based on (key) values
     *
     * @param inValues the Map containing the values
     * @return success indicator
     */
    public boolean delete(ReadOnlyMap inValues) {
        if (mReadOnly) {
            Logger.error("Error: Entity "+getName()+" is read-only!");
            return false;
        }
        Instance instance = newInstance(inValues);
        return instance.delete();
    }

    /** fetch an instance from key values stored in a List in natural order
     *
     * @param inValues the List containing the key values
     * @return the fetched instance
     */
    public Instance fetch(List inValues) throws SQLException {
        if (inValues.size() != mKeys.size()) throw new SQLException("Entity.fetch: Error: Wrong number of values for '"+mName+"' primary key!");
        Instance instance = null;
        // try in cache
        if (mCachingMethod != Cache.NO_CACHE)
            instance = (Instance)mCache.get(inValues.toArray());
        if (instance == null) {
            if (mFetchQuery == null) buildFetchQuery();
            PooledPreparedStatement statement = mDB.prepare(mFetchQuery);
            if (mObfuscate) {
                inValues = new ArrayList(inValues);
                for(int col=0;col<mKeyColObfuscated.length;col++)
                    if(mKeyColObfuscated[col])
                        inValues.set(col,deobfuscate(inValues.get(col)));
            }
            instance = (Instance)statement.fetch(inValues,this);
        }
        return instance;
    }

    /** fetch an instance from key values stored in a Map
     *
     * @param inValues the Map containing the key values
     * @return the fetched instance
     */
    public Instance fetch(ReadOnlyMap inValues) throws SQLException {
        Instance instance = null;
        // try in cache
        if (mCachingMethod != Cache.NO_CACHE)
            // try in cache
            instance = (Instance)mCache.get(buildKey(inValues));
        if (instance == null) {
            if (mFetchQuery == null) buildFetchQuery();
            PooledPreparedStatement statement = mDB.prepare(mFetchQuery);
            if (mObfuscate) {
                Map map =  new HashMap();
                for(Object key:inValues.keySet()) {
                    Object value = inValues.get(key);
                    map.put( Database.adaptContextCase((String)key), isObfuscated((String)key) ? deobfuscate(value) : value );
                }
                inValues = new ReadOnlyWrapper(map);
            }
            instance = (Instance)statement.fetch(inValues,this);
        }
        return instance;
    }

    /** fetch an instance from its key value as a string
     *
     * @param inKeyValue the key
     * @return the fetched instance
     */
    public Instance fetch(String inKeyValue) throws SQLException {
        if (mKeys.size()!=1) {
            if (mKeys.size()==0) throw new SQLException("Entity.fetch: Error: Entity '"+mName+"' has no primary key!");
            else throw new SQLException("Entity.fetch: Error: Entity '"+mName+"' has a multi-column primary key!");
        }
        Instance instance = null;
        // try in cache
        if (mCachingMethod != Cache.NO_CACHE)
            // try in cache
            instance = (Instance)mCache.get(new Object[] { inKeyValue });
        if (instance == null) {
            if (mFetchQuery == null) buildFetchQuery();
            PooledPreparedStatement statement = mDB.prepare(mFetchQuery);
            if (mObfuscate && mKeyColObfuscated[0]) {
                inKeyValue = deobfuscate(inKeyValue);
            }
            ArrayList params = new ArrayList();
            params.add(inKeyValue);
            instance = (Instance)statement.fetch(params,this);
        }
        return instance;
    }

    /** fetch an instance from its key value specified as a Number
     *
     * @param inKeyValue the key
     * @return the fetched instance
     */
    public Instance fetch(Number inKeyValue) throws SQLException {
        if (mKeys.size()!=1) {
            if (mKeys.size()==0) throw new SQLException("Entity.fetch: Error: Entity '"+mName+"' has no primary key!");
            else throw new SQLException("Entity.fetch: Error: Entity '"+mName+"' has a multi-column primary key!");
        }
        Instance instance = null;
        // try in cache
        if (mCachingMethod != Cache.NO_CACHE)
            // try in cache
            instance = (Instance)mCache.get(new Object[] { inKeyValue });
        if (instance == null) {
            if (mFetchQuery == null) buildFetchQuery();
            PooledPreparedStatement statement = mDB.prepare(mFetchQuery);
            ArrayList params = new ArrayList();
            params.add(inKeyValue);
            if(mObfuscate && mKeyColObfuscated[0]) {
                Logger.warn("fetch: column '"+mColumns.get(0)+"' is obfuscated, please use $db."+mName+".fetch($db.obfuscate("+inKeyValue+"))");
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
        if (mFetchQuery == null) buildFetchQuery();
        return mFetchQuery;
    }

    /** build the SQL query used to fetch one instance of this query
     */
    protected void buildFetchQuery() {
        ArrayList whereClause = new ArrayList();
        for(String column:mKeys) {
            whereClause.add(aliasToColumn(column)+"=?");
        }
        mFetchQuery = "select * from "+mTable+" where "+StringLists.join(whereClause," and ");
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
     * @param inRefineCriteria a refining criteria or null to get all instances
     * @param inOrder an 'order by' clause or null to get instances in their
     *     natural order
     * @return the resulting RowIterator
     */
    public RowIterator query(List inRefineCriteria,String inOrder) throws SQLException {
        String query = "select * from "+ mTable;
        if (inRefineCriteria!=null) query = SqlUtil.refineQuery(query,inRefineCriteria);
        if (inOrder!=null && inOrder.length()>0) query = SqlUtil.orderQuery(query,inOrder);
        return mDB.query(query,this);
    }

//    public boolean hasColumn
//    public List getColumns

    /** get the database connection
     *
     * @return the database connection
     */
    public Database getDB() { return mDB; }

    /** Is this entity read-only or read-write?
     *
     * @return whether this entity is read-only or not
     */
    public boolean isReadOnly() {
        return mReadOnly;
    }

    /** set this entity to be read-only or read-write
     *
     * @param inReadOnly the mode to switch to : true for read-only, false for
     *     read-write
     */
    public void setReadOnly(boolean inReadOnly) {
        mReadOnly = inReadOnly;
    }

    /** set the name of the table mapped by this entity
     *
     * @param inTable the table mapped by this entity
     *     read-write
     */
    public void setTableName(String inTable) {
        mTable = inTable;
    }

    /** get the name of the mapped table
     *
     * @return name of the mapped table
     */
    public String getTableName() {
        return mTable;
    }

    /** indicates a column as being obfuscated
     * @param inColumns list of obfuscated columns
     */
    public void setObfuscated(List inColumns)
    {
        mObfuscate = true;
        mObfuscatedColumns = inColumns;
    }

    /** returns whether the given column is obfuscated
     * @param inColumn the name of the column
     * @return a boolean indicating whether this column is obfuscated
     */
    public boolean isObfuscated(String inColumn)
    {
        return mObfuscate && mObfuscatedColumns.contains(mDB.adaptCase(inColumn));
    }

    /** obfuscate given value
     * @param value value to obfuscate
     *
     * @return obfuscated value
     */
    public String obfuscate(Object value)
    {
        return mDB.obfuscate(value);
    }

    /** obfuscate this id value if needed
     *
     */
    public Object filterID(Long id) {
        if (mKeys.size() == 1 && isObfuscated((String)mKeys.get(0))) return obfuscate(Long.valueOf(id));
        return Long.valueOf(id);
    }



    /** de-obfuscate given value
     * @param value value to de-obfuscate
     *
     * @return obfuscated value
     */
    public String deobfuscate(Object value)
    {
        return mDB.deobfuscate(value);
    }

    /** indicates a column as being localized
     * @param inColumns list of localized columns
     */
    public void setLocalized(List inColumns)
    {
        mLocalizedColumns = inColumns;
    }

    /** returns whether the given column is obfuscated
     * @param inColumn the name of the column
     * @return a boolean indicating whether this column is obfuscated
     */
    public boolean isLocalized(String inColumn)
    {
        return mLocalizedColumns != null && mLocalizedColumns.contains(mDB.adaptCase(inColumn));
    }

    /** does this entity have localized columns ?
     */
    public boolean hasLocalizedColumns() {
        return mLocalizedColumns != null && mLocalizedColumns.size() > 0;
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
        for(FieldConstraintInfo info:mConstraints) {
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
        for(Map.Entry entry:(Set<Map.Entry>)mAttributeMap.entrySet()) {
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
        for(Map.Entry entry:(Set<Map.Entry>)mAttributeMap.entrySet()) {
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
    protected String mName = null;
    /** table
     */
    protected String mTable = null;
    /** column names in natural order
     */
    protected List<String> mColumns = new ArrayList(); // list<String>
    /** key column names in natural order
     */
    protected List<String> mKeys = new ArrayList();
    /** whether to obfuscate something
     */
    protected boolean mObfuscate = false;
    /** names of obfuscated columns
     */
    protected List<String> mObfuscatedColumns = null;
    /** obfuscation status of key columns
     */
    protected boolean mKeyColObfuscated[] = null;
    /** localized columns
     */
    protected List mLocalizedColumns = null;
    /** attributes of this entity
     */

    protected Map<String,String> mAliasByColumn = new HashMap<String,String>();

    protected Map<String,String> mColumnByAlias = new HashMap<String,String>();

    protected Map mAttributeMap = new HashMap(); // map<Name,Attribute>
    /** actions of this entity
     */
    protected Map mActionMap = new HashMap();
    /** the java class to use to realize this instance
     */
    protected Class mInstanceClass = null;
    /** the SQL query used to fetch one instance of this entity
     */
    protected String mFetchQuery = null;
    /** whether this entity is read-only or not
     */
    protected boolean mReadOnly;
    /** the database connection
     */
    protected Database mDB = null;
    /** the caching method
     */
    protected int mCachingMethod = 0;
    /** the cache
     */
    protected Cache mCache = null;

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

    private List<FieldConstraintInfo> mConstraints = new ArrayList<FieldConstraintInfo>();
}
