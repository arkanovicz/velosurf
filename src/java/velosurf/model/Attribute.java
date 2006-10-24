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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import velosurf.context.RowIterator;
import velosurf.sql.Database;
import velosurf.sql.ReadOnlyMap;
import velosurf.sql.SqlUtil;
import velosurf.util.Logger;
import velosurf.util.StringLists;

/** This class represents an attribute in the object model
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 */
public class Attribute
{
    // attribute type constants
    /** constant meaning the return type is undefined
     */
    public static final int UNDEFINED = 0;
    /** constant meaning the result is a single row
     */
    public static final int ROW = 1;
    /** constant meaning the result is a rowset
     */
    public static final int ROWSET = 2;
    /** constant meaning the result is a scalar
     */
    public static final int SCALAR = 3;

    public Attribute(String name,Entity entity) {
        mEntity = entity;
        mDB = entity.getDB();
        mName = name;
    }

    public void setResultType(int type) {
        mType = type;
    }

    public String getResultEntity() {
        return mResultEntity;
    }

    public void setResultEntity(String entityName) {
        mResultEntity = entityName;
    }

    public void setForeignKeyColumn(String col) {
        mForeignKey = col;
    }

    public void addParamName(String name) {
        mParamNames.add(name);
    }

    public void setQuery(String query) {
        mQuery = query;
    }

    /** fetch the row result of this attribute
     *
     * @param inSource source object
     * @exception SQLException when thrown by the database
     * @return instance fetched
     */
    public Object fetch(ReadOnlyMap inSource) throws SQLException {
        if (mType != ROW) throw new SQLException("cannot call fetch: result of attribute '"+mName+"' is not a row");
        return mDB.prepare(getQuery()).fetch(buildArrayList(inSource),mDB.getEntity(mResultEntity));
    }

    /** query the resultset for this multivalued attribute
     *
     * @param inSource the source object
     * @exception SQLException when thrown from the database
     * @return the resulting row iterator
     */
    public RowIterator query(ReadOnlyMap inSource) throws SQLException {
        return query(inSource,null,null);
    }

    /** query the rowset for this attribute
     *
     * @param inSource source object
     * @param inRefineCriteria refine criteria
     * @param inOrder order clause
     * @exception SQLException when thrown by the database
     * @return the resulting row iterator
     */
    public RowIterator query(ReadOnlyMap inSource,List inRefineCriteria,String inOrder) throws SQLException {
        if (mType != ROWSET) throw new SQLException("cannot call query: result of attribute '"+mName+"' is not a rowset");
        String query = getQuery();
        if (inRefineCriteria != null) query = SqlUtil.refineQuery(query,inRefineCriteria);
        if (inOrder != null && inOrder.length()>0) query = SqlUtil.orderQuery(query,inOrder);
        return mDB.prepare(query).query(buildArrayList(inSource),mDB.getEntity(mResultEntity));
    }


    /** evaluate this scalar attribute
     *
     * @param inSource source object
     * @exception SQLException when thrown from the database
     * @return the resulting scalar
     */
    public Object evaluate(ReadOnlyMap inSource) throws SQLException {
        if (mType != SCALAR) throw new SQLException("cannot call evaluate: result of attribute '"+mName+"' is not a scalar");
        return mDB.prepare(getQuery()).evaluate(buildArrayList(inSource));
    }

    /** do an update via this attribute: deprecated, prefer to use an action instead
     *
     * @param inSource source object
     * @exception SQLException when thrown from the database
     * @return the numer of affected rows
     */
    public int update(ReadOnlyMap inSource) throws SQLException {
        if (mType != SCALAR) throw new SQLException("cannot call update: result of attribute '"+mName+"' is not a scalar");
        if (mForeignKey != null) throw new SQLException("cannot call update: attribute '"+mName+"' is a foreign key");
        return mDB.prepare(getQuery()).update(buildArrayList(inSource));
    }

    /** gets the type of this attribute
     *
     * @return this attribute's type
     */
    public int getType() {
        return mType;
    }

    /** builds the list of parameter values - do not use directly
     *
     * @param inSource source object
     * @exception SQLException thrown by the database engine
     * @return the built list
     */
    public List buildArrayList(ReadOnlyMap inSource) throws SQLException {
        ArrayList result = new ArrayList();
        if (inSource!=null)
            for (Iterator i = mParamNames.iterator();i.hasNext();) {
                String paramName = (String)i.next();
                Object value = inSource.get(paramName);
Logger.debug("### buildArrayList: "+paramName+" -> "+value);                
                if (mEntity.isObfuscated(paramName)) value = mDB.deobfuscate(value);
                if (value == null) Logger.warn("Query "+mQuery+": param "+paramName+" is null!");
                result.add(value);
            }
        return result;
    }

    /** gets the name of the attribute
     *
     * @return name of the attribute
     */
    public String getName() {
        return mName;
    }

    /** debug method
     *
     * @return the definition string of this attribute
     */
    public String toString() {
        String result = "";
        switch (mType) {
            case SCALAR:
                result += "!";
                break;
            case ROWSET:
                result += "*";
                break;
        }
        if (mResultEntity!=null) result += mResultEntity;
        if (mForeignKey != null) result += ": foreign key ("+mForeignKey+")";
        else {
            if (mParamNames.size()>0) result += "("+StringLists.join(mParamNames,",")+")";
            result+=": "+mQuery;
        }
        return result;
    }

    protected String getQuery() throws SQLException
    {
        return mQuery == null ? mDB.getEntity(mResultEntity).getFetchQuery() : mQuery;
    }

    /** gets the database connection
     *
     * @return database connection
     */
    public Database getDB() {
        return mDB;
    }


    /** database connection
     */
    protected Database mDB = null;
    /** name
     */
    protected String mName = null;

    /** parent entity
     */
    protected Entity mEntity;

    /** for row and rowset attributes, the resulting entity (if specified)
     */
    protected String mResultEntity;

    /** if used, name of the foreign key
     */
    protected String mForeignKey = null;

    /** list of the parameter names
     */
    protected List<String> mParamNames = new ArrayList<String>();
    /** attribute query
     */
    protected String mQuery = null;
    /** attribute type
     */
    protected int mType = UNDEFINED;
}
