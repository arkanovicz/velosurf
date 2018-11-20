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

import java.io.Serializable;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import velosurf.context.RowIterator;
import velosurf.sql.Database;
import velosurf.sql.SqlUtil;
import velosurf.util.DynamicQueryBuilder;
import velosurf.util.Logger;
import velosurf.util.SlotMap;
import velosurf.util.StringLists;

/**
 * This class represents an attribute in the object model.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 */
public class Attribute implements Serializable
{
    /**
     * Constant meaning the return type is undefined.
     */
    public static final int UNDEFINED = 0;

    /**
     * Constant meaning the result is a single row.
     */
    public static final int ROW = 1;

    /**
     * Constant meaning the result is a rowset.
     */
    public static final int ROWSET = 2;

    /**
     * Constant meaning the result is a scalar.
     */
    public static final int SCALAR = 3;

    /**
     * Constructor.
     * @param name name of this attribute
     * @param entity parent entity
     */
    public Attribute(String name, Entity entity)
    {
        this.entity = entity;
        db = entity.getDB();
        this.name = name;
    }

    public List<String> getParamNames()
    {
        return paramNames;
    }
    
    /**
     * Sets the result type.
     * @param type
     */
    public void setResultType(int type)
    {
        this.type = type;
    }

    public int getResultType() { return type; }

    /**
     * Gets the parent entity
     * @return parent entity
     */
    public Entity getEntity()
    {
        return entity;
    }

    /**
     * Gets the result type.
     *
     * @return a string describing the result type.
     */
    public String getResultEntity()
    {
        return resultEntity;
    }

    /**
     * Sets the result entity.
     * @param entityName the name of the result entity.
     */
    public void setResultEntity(String entityName)
    {
        resultEntity = entityName;
    }

    /**
     * Declares this attribute as a foreign-key and specifies its foreign-key column.
     * @param col the foreign-key column.
     * @deprecated since Velosurf 2.0. Use a &lt;imported-key&gt; tag instead.
     */
    public void setForeignKeyColumn(String col)
    {
        foreignKey = col;
    }

    /**
     * Adds a parameter name.
     * @param name name of a parameter.
     */
    public void addParamName(String name) throws SQLException
    {
        if (name.equals(this.name)) throw new SQLException("Parameter name must differ from attribute name: " + name);
        paramNames.add(name);
    }

    /**
     * Sets the query.
     * @param query this attribute's query
     */
    public void setQuery(String query)
    {
        if (!db.getDriverInfo().hasColumnMarkers())
        {
            query = query.replaceAll("::[a-z]*", "");
        }
        this.query = query;
        this.dynamicQuery = DynamicQueryBuilder.isDynamic(query);
    }

    /**
     * Fetch a row.
     *
     * @param source source object
     * @exception SQLException when thrown by the database
     * @return instance fetched
     */
    public Serializable fetch(SlotMap source) throws SQLException
    {
        if(type != ROW)
        {
            throw new SQLException("cannot call fetch: result of attribute '" + name + "' is not a row");
        }
        return db.prepare(getQuery(source), false).fetch(buildArrayList(source), db.getEntity(resultEntity));
    }

    /**
     * Query the resultset for this multivalued attribute.
     *
     * @param source the source object
     * @exception SQLException when thrown from the database
     * @return the resulting row iterator
     */
    public RowIterator query(SlotMap source) throws SQLException
    {
        return query(source, null, null);
    }

    /**
     * Query the rowset for this attribute.
     *
     * @param source source object
     * @param refineCriteria refine criteria
     * @param order order clause
     * @exception SQLException when thrown by the database
     * @return the resulting row iterator
     */
    public RowIterator query(SlotMap source, List refineCriteria, String order) throws SQLException
    {
        if(type != ROWSET)
        {
            throw new SQLException("cannot call query: result of attribute '" + name + "' is not a rowset");
        }

        String query = getQuery(source);

        if(refineCriteria != null)
        {
            query = SqlUtil.refineQuery(query, refineCriteria);
        }
        if(order != null && order.length() > 0)
        {
            query = SqlUtil.orderQuery(query, order);
        }
        return db.prepare(query, false).query(buildArrayList(source), resultEntity == null ? db.getRootEntity() : db.getEntity(resultEntity));
    }

    // TODO

    /*
     * public long getCount() {
     *   return getCount(null,null);
     * }
     *
     * String countQuery = null;
     * Pattern pattern = Pattern.compile("\\s*select\\s.*\\sfrom\\s",Pattern.CASE_INSENSITIVE);
     *
     * public long getCount(List refineCriteria,String order) {
     *   if(countQuery == null) {
     *       synchronized(this) {
     *           if(countQuery == null) {
     *               String query = getQuery();
     *               Matcher matcher = pattern.matcher(countQuery);
     *               if(matcher.lookingAt()) {
     *                   countQuery = matcher.replaceFirst().... pas bon
     *               }
     *           }
     *       }
     *   }
     *
     *   db.getEntity(resultEntity);
     *   if (refineCriteria!=null) query = SqlUtil.refineQuery(query,refineCriteria);
     *   if (order!=null && order.length()>0) query = SqlUtil.orderQuery(query,order);
     *   return (Long)db.evaluate(query);
     * }
     */

    /**
     * Evaluate this scalar attribute.
     *
     * @param source source object
     * @exception SQLException when thrown from the database
     * @return the resulting scalar
     */
    public Serializable evaluate(SlotMap source) throws SQLException
    {
        if(type != SCALAR)
        {
            throw new SQLException("cannot call evaluate: result of attribute '" + name + "' is not a scalar");
        }
        return db.prepare(getQuery(source), false).evaluate(buildArrayList(source));
    }

    /**
     * Get the type of this attribute.
     *
     * @return this attribute's type
     */
    public int getType()
    {
        return type;
    }

    /**
     * Builds the list of parameter values.
     *
     * @param source source object
     * @exception SQLException thrown by the database engine
     * @return the built list
     */
    private List<Object> buildArrayList(SlotMap source) throws SQLException
    {
        List<Object> result = new ArrayList<Object>();

        if(source != null)
        {
            for(Iterator i = paramNames.iterator(); i.hasNext(); )
            {
                String paramName = (String)i.next();
                Object value = null;
                int dot = paramName.indexOf('.');

                if(dot > 0 && dot < paramName.length() - 1)
                {
                    String parentKey = paramName.substring(0, dot);

                    value = source.get(parentKey);
                    if(value == null)
                    {
                        value = source.get(entity.resolveName(parentKey));
                    }
                    if(value != null && value instanceof Map)
                    {
                        String subKey = paramName.substring(dot + 1);

                        value = ((Map)value).get(subKey);
                    }
                }
                if(value == null)
                {
                    value = source.get(paramName);
                }
                if(entity.isObfuscated(paramName))
                {
                    value = db.deobfuscate(value);
                }
                if(value == null)
                {
                    // TODO - check if "<param/> is [not] null" is present in the query
                    // to choose the apropriate log level
                    Logger.trace("Attribute " + getEntity().getName() + "." + name + ": param " + paramName
                                + " is null!");
                }
                value = db.filterParam(value);
                result.add(value);
            }
        }
        if(Logger.getLogLevel() >= Logger.TRACE_ID)
        {
            Logger.trace("    with parameters " + result);
        }
        return result;
    }

    /**
     * Gets the name of this attribute.
     *
     * @return name of the attribute
     */
    public String getName()
    {
        return name;
    }

    /**
     * Debug method.
     *
     * @return the definition string of this attribute
     */
    public String toString()
    {
        String result = "";

        switch(type)
        {
            case SCALAR :
                result += "!";
                break;
            case ROWSET :
                result += "*";
                break;
        }
        if(resultEntity != null)
        {
            result += resultEntity;
        }
        if(foreignKey != null)
        {
            result += ": foreign key (" + foreignKey + ")";
        }
        else
        {
            if(paramNames.size() > 0)
            {
                result += "(" + StringLists.join(paramNames, ",") + ")";
            }
            result += ": " + query;
        }
        return result;
    }

    protected synchronized String getQuery() throws SQLException
    {
        return db.getEntity(resultEntity).getFetchQuery();
    }
    
    protected synchronized String getQuery(SlotMap source) throws SQLException
    {
        if (query == null) return getQuery();
        if (!dynamicQuery) return query;
        return DynamicQueryBuilder.buildQuery(query, source);
    }

    /**
     * Gets the database connection.
     *
     * @return database connection
     */
    public Database getDB()
    {
        return db;
    }

    /**
     * Gets caching state
     * @return caching state
     */
    public boolean getCaching()
    {
        return caching;
    }

    /**
     * Sets caching on or off
     *  @param c caching state
     */
    public void setCaching(boolean c)
    {
        caching = c;
    }

    /**
     * Database connection.
     */
    protected Database db = null;

    /**
     * Name.
     */
    private String name = null;

    /**
     * Parent entity.
     */
    protected Entity entity;

    /**
     * For row and rowset attributes, the resulting entity (if specified).
     */
    protected String resultEntity;

    /**
     * If used, name of the foreign key.
     * @deprecated
     */
    private String foreignKey = null;

    /**
     * List of the parameter names.
     */
    private List<String> paramNames = new ArrayList<String>();

    /**
     * Attribute query.
     */
    protected String query = null;

    /**
     * whether query is dynamic
     */
    private boolean dynamicQuery = false;

    /**
     * Attribute type.
     */
    private int type = UNDEFINED;

    /**
     * Caching
     */
    private boolean caching = false;
}
