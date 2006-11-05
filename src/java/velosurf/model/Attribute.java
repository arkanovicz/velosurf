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
        this.entity = entity;
        db = entity.getDB();
        this.name = name;
    }

    public void setResultType(int type) {
        this.type = type;
    }

    public String getResultEntity() {
        return resultEntity;
    }

    public void setResultEntity(String entityName) {
        resultEntity = entityName;
    }

    public void setForeignKeyColumn(String col) {
        foreignKey = col;
    }

    public void addParamName(String name) {
        paramNames.add(name);
    }

    public void setQuery(String query) {
        this.query = query;
    }

    /** fetch the row result of this attribute
     *
     * @param source source object
     * @exception SQLException when thrown by the database
     * @return instance fetched
     */
    public Object fetch(ReadOnlyMap source) throws SQLException {
        if (type != ROW) throw new SQLException("cannot call fetch: result of attribute '"+name+"' is not a row");
        return db.prepare(getQuery()).fetch(buildArrayList(source),db.getEntity(resultEntity));
    }

    /** query the resultset for this multivalued attribute
     *
     * @param source the source object
     * @exception SQLException when thrown from the database
     * @return the resulting row iterator
     */
    public RowIterator query(ReadOnlyMap source) throws SQLException {
        return query(source,null,null);
    }

    /** query the rowset for this attribute
     *
     * @param source source object
     * @param refineCriteria refine criteria
     * @param order order clause
     * @exception SQLException when thrown by the database
     * @return the resulting row iterator
     */
    public RowIterator query(ReadOnlyMap source,List refineCriteria,String order) throws SQLException {
        if (type != ROWSET) throw new SQLException("cannot call query: result of attribute '"+name+"' is not a rowset");
        String query = getQuery();
        if (refineCriteria != null) query = SqlUtil.refineQuery(query,refineCriteria);
        if (order != null && order.length()>0) query = SqlUtil.orderQuery(query,order);
        return db.prepare(query).query(buildArrayList(source),db.getEntity(resultEntity));
    }


    /** evaluate this scalar attribute
     *
     * @param source source object
     * @exception SQLException when thrown from the database
     * @return the resulting scalar
     */
    public Object evaluate(ReadOnlyMap source) throws SQLException {
        if (type != SCALAR) throw new SQLException("cannot call evaluate: result of attribute '"+name+"' is not a scalar");
        return db.prepare(getQuery()).evaluate(buildArrayList(source));
    }

    /** gets the type of this attribute
     *
     * @return this attribute's type
     */
    public int getType() {
        return type;
    }

    /** builds the list of parameter values - do not use directly
     *
     * @param source source object
     * @exception SQLException thrown by the database engine
     * @return the built list
     */
    public List buildArrayList(ReadOnlyMap source) throws SQLException {
        ArrayList result = new ArrayList();
        if (source!=null)
            for (Iterator i = paramNames.iterator();i.hasNext();) {
                String paramName = (String)i.next();
                Object value = source.get(paramName);
                if (entity.isObfuscated(paramName)) value = db.deobfuscate(value);
                if (value == null) Logger.warn("Query "+query+": param "+paramName+" is null!");
                result.add(value);
            }
        return result;
    }

    /** gets the name of the attribute
     *
     * @return name of the attribute
     */
    public String getName() {
        return name;
    }

    /** debug method
     *
     * @return the definition string of this attribute
     */
    public String toString() {
        String result = "";
        switch (type) {
            case SCALAR:
                result += "!";
                break;
            case ROWSET:
                result += "*";
                break;
        }
        if (resultEntity!=null) result += resultEntity;
        if (foreignKey != null) result += ": foreign key ("+foreignKey+")";
        else {
            if (paramNames.size()>0) result += "("+StringLists.join(paramNames,",")+")";
            result+=": "+query;
        }
        return result;
    }

    protected String getQuery() throws SQLException
    {
        return query == null ? db.getEntity(resultEntity).getFetchQuery() : query;
    }

    /** gets the database connection
     *
     * @return database connection
     */
    public Database getDB() {
        return db;
    }


    /** database connection
     */
    protected Database db = null;
    /** name
     */
    protected String name = null;

    /** parent entity
     */
    protected Entity entity;

    /** for row and rowset attributes, the resulting entity (if specified)
     */
    protected String resultEntity;

    /** if used, name of the foreign key
     */
    protected String foreignKey = null;

    /** list of the parameter names
     */
    protected List<String> paramNames = new ArrayList<String>();
    /** attribute query
     */
    protected String query = null;
    /** attribute type
     */
    protected int type = UNDEFINED;
}
