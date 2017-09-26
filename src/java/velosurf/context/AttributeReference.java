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
import java.util.*;
import velosurf.model.Attribute;
import velosurf.util.Logger;
import velosurf.util.SlotMap;
import velosurf.util.StringLists;

/**
 * Context wrapper for attributes.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 */
public class AttributeReference implements Iterable, Serializable
{
    /**
     * Constructor.
     *
     * @param params the parameters map this attribute reference applies to
     * @param attribute the wrapped attribute
     */
    public AttributeReference(SlotMap params, Attribute attribute)
    {
        this.params = params;
        this.attribute = attribute;
    }

    /**
     * <p>Refines this attribute's reference result. The provided criterium will be added to the 'where' clause (or a 'where' clause will be added).</p>
     * <p>This method can be called several times, thus allowing a field-by-field handling of an html search form.</p>
     * <p>All criteria will be merged with the sql 'and' operator (if there is an initial where clause, it is wrapped into parenthesis).</p>
     * <p>Example : suppose we have defined the attribute 'person.children' as " *person(person_id):select * from person where parent_id=?". Then, if we issue the following calls from inside the template :</p>
     * <blockquote>
     * $bob.children.refine("age>18")
     * <br>
     * $bob.children.refine("gender='F'")
     * </blockquote>
     * <p>the resulting query that will be issed is :</p>
     * <p><code>select * from person where (parent_id=?) and (age>18) and (gender='F')</code></p>
     *
     * @param criterium a valid sql condition
     */
    public void refine(String criterium)
    {
        if(refineCriteria == null)
        {
            refineCriteria = new ArrayList();
        }
        refineCriteria.add(criterium);
    }

    /**
     * Clears any refinement made on this attribute.
     * <p>
     */
    public void clearRefinement()
    {
        refineCriteria = null;
    }

    /**
     * Called by the #foreach directive.
     * <p>
     * Returns a RowIterator on all possible instances of this entity, possibly previously refined and ordered.</p>
     *
     * @return a RowIterator on instances of this entity, or null if an error
     *     occured.
     */
    public Iterator iterator()
    {
        try
        {
            RowIterator iterator = attribute.query(params, refineCriteria, order);

            return iterator;
        }
        catch(SQLException sqle)
        {
            Logger.log("attribute '" + attribute.getName() + "': ", sqle);
            attribute.getDB().setError(sqle.getMessage());
            return null;
        }
    }

    /**
     * Gets all the rows in a list of maps.
     *
     * @return a list of all the rows
     */
    public List getRows()
    {
        try
        {
            RowIterator iterator = attribute.query(params, refineCriteria, order);

            return iterator.getRows();
        }
        catch(SQLException sqle)
        {
            Logger.log("attribute '" + attribute.getName() + "': ", sqle);
            attribute.getDB().setError(sqle.getMessage());
            return null;
        }
    }

    /**
     * Gets a list of scalars
     *  @return a list of scalars
     */
    public List getScalars()
    {
        try
        {
            RowIterator iterator = attribute.query(params, refineCriteria, order);

            return iterator.getScalars();
        }
        catch(SQLException sqle)
        {
            Logger.log("attribute '" + attribute.getName() + "': ", sqle);
            attribute.getDB().setError(sqle.getMessage());
            return null;
        }
    }

    /**
     * Get all the rows in a map firstcol-&gt;secondcol.
     * FIXME: better in Attribute than in AttributeReference
     * @return a list of all the rows
     */
    public Map getMap()
    {
        Map result = null;

        try
        {
            RowIterator iterator = attribute.query(params, refineCriteria, order);
            List<String> keys = iterator.keyList();

            if(keys.size() < 2)
            {
                Logger.error(".map needs at least two result columns");
                return null;
            }
            if(keys.size() > 2)
            {
                Logger.warn(
                    "attribute.map needs only two result columns, only the first two will be taken into account");
            }
            result = new TreeMap();
            while(iterator.hasNext())
            {
                Instance i = iterator.next();

                result.put(i.get(keys.get(0)), i.get(keys.get(1)));
            }
        }
        catch(SQLException sqle)
        {
            Logger.log("attribute '" + attribute.getName() + "': ", sqle);
            attribute.getDB().setError(sqle.getMessage());
            return null;
        }
        return result;
    }

    /**
     * Get all the rows in a { singlecol } set
     * @return a set of all the fist column values
     */
    public Set getSet()
    {
        Set result = null;

        try
        {
            RowIterator iterator = attribute.query(params, refineCriteria, order);
            List<String> keys = iterator.keyList();

            if(keys.size() > 1)
            {
                Logger.warn(
                    "attribute.map needs only two result columns, only the first two will be taken into account");
            }
            result = new TreeSet();
            while(iterator.hasNext())
            {
                Instance i = iterator.next();

                result.add(i.get(keys.get(0)));
            }
        }
        catch(SQLException sqle)
        {
            Logger.log("attribute '" + attribute.getName() + "': ", sqle);
            attribute.getDB().setError(sqle.getMessage());
            return null;
        }
        return result;
    }
    
    /**
     * Get all the rows in a map firstcol->instance
     * FIXME: better in Attribute than in AttributeReference
     * @return a list of all the rows
     */
    public Map getInstanceMap()
    {
        Map result = null;

        try
        {
            RowIterator iterator = attribute.query(params, refineCriteria, order);
            List<String> keys = iterator.keyList();

            result = new TreeMap();
            while(iterator.hasNext())
            {
                Instance i = iterator.next();

                result.put(i.get(keys.get(0)), i);
            }
        }
        catch(SQLException sqle)
        {
            Logger.log("attribute '" + attribute.getName() + "': ", sqle);
            attribute.getDB().setError(sqle.getMessage());
            return null;
        }
        return result;
    }

    /**
     * <p>Specify an 'order by' clause for this attribute reference result.</p>
     * <p>If an 'order by' clause is already present in the original query, the new one is appended (but successive calls to this method overwrite previous ones)</p>
     * <p> postfix " DESC " to a column for descending order.</p>
     * <p>Pass it null or an empty string to clear any ordering.</p>
     *
     * @param order valid sql column names (separated by commas) indicating the
     *      desired order
     */
    public void setOrder(String order)
    {
        this.order = order;
    }

    /**
     * Specified refining criteria defined on this attribute reference.
     */
    private List<String> refineCriteria = null;

    /**
     * Specified 'order by' clause specified for this attribute reference.
     */
    private String order = null;

    /**
     * The map this attribute reference applies to.
     */
    private SlotMap params = null;

    /**
     * The wrapped attribute.
     */
    private Attribute attribute = null;
}
