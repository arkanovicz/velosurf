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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import velosurf.sql.Database;
import velosurf.util.Logger;
import velosurf.util.SlotMap;
import velosurf.util.StringLists;

/**
 * This class corresponds to custom update, delete and insert queries.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 */
public class Action implements Serializable
{
    /**
     * Constructor.
     *
     * @param name name
     * @param entity entity
     */
    public Action(String name, Entity entity)
    {
        this.entity = entity;
        db = this.entity.getDB();
        this.name = name;
    }

    /**
     * Gets the parent entity
     * @return parent entity
     */
    public Entity getEntity()
    {
        return entity;
    }

    /**
     * Add a parameter name.
     * @param paramName
     */
    public void addParamName(String paramName)
    {
        paramNames.add(paramName);
    }

    /**
     * Sets the query.
     * @param query query
     */
    public void setQuery(String query)
    {
        this.query = query;
    }

    /**
     * Executes this action.
     *
     * @param source the object on which apply the action
     * @exception SQLException an SQL problem occurs
     * @return number of impacted rows
     */
    public int perform(SlotMap source) throws SQLException
    {
        List params = buildArrayList(source);

        return db.prepare(query).update(params);
    }

    /**
     * Get the list of values for all parameters.
     *
     * @param source the ReadOnlyMap
     * @exception SQLException thrown by the ReadOnlyMap
     * @return the list of values
     */
    public List<Object> buildArrayList(SlotMap source) throws SQLException
    {
        List<Object> result = new ArrayList<Object>();

        if(source != null)
        {
            for(Iterator i = paramNames.iterator(); i.hasNext(); )
            {
                String paramName = (String)i.next();
                Object value = source.get(paramName);

                if(value == null)
                {
                    /* TODO: same problem than in Entity.extractColumnValues... we need a case-insensitive algorithm */
                    value = source.get(paramName.toUpperCase());
                    if(value == null)
                    {
                        value = source.get(paramName.toLowerCase());
                    }
                }
                if(entity.isObfuscated(paramName))
                {
                    value = db.deobfuscate(value);
                }
                if(value == null)
                {
                    Logger.debug("Action " + getEntity().getName() + "." + name + ": param " + paramName + " is null!");
                }
                result.add(value);
            }
        }
        return result;
    }

    /**
     * Get the name of the action.
     *
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * For debugging purposes.
     *
     * @return definition string
     */
    public String toString()
    {
        String result = "";

        if(paramNames.size() > 0)
        {
            result += "(" + StringLists.join(paramNames, ",") + ")";
        }
        result += ":" + query;
        return result;
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
     * The database connection.
     */
    protected Database db = null;

    /**
     * The entity this action belongs to.
     */
    private Entity entity = null;

    /**
     * The name of this action.
     */
    private String name = null;

    /**
     * Parameter names of this action.
     */
    protected List<String> paramNames = new ArrayList<String>();

    /**
     * Query.
     */
    private String query = null;
}
