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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import velosurf.model.Action;
import velosurf.model.Attribute;
import velosurf.model.Entity;
import velosurf.sql.Database;
import velosurf.util.Logger;
import velosurf.util.SlotHashMap;
import velosurf.util.SlotMap;
import velosurf.util.SlotTreeMap;
import velosurf.util.UserContext;

/**
 * <p>A context wrapper for the main database connection object.</p>
 * <p>The "<code>$db</code>" context variable is assigned a new instance of this class at each velocity parsing.</p>
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 */

public class DBReference extends SlotTreeMap implements HasParametrizedGetter
{
    /**
     * Default constructor for use by derived classes.
     */
    protected DBReference(){}

    /**
     * Constructs a new database reference.
     *
     * @param db the wrapped database connection
     */
    public DBReference(Database db)
    {
        init(db);
    }

    /**
     * Protected initialization method.
     *
     * @param db database connection
     */
    protected void init(Database db)
    {
        this.db = db;
        cache = new SlotTreeMap();
        externalParams = new SlotTreeMap();
    }

    /**
     * <p>Generic getter, used to access entities or root attributes by their name.</p>
     * <p>For attributes, the return value depends upon the type of the attribute :</p>
     * <ul>
     * <li>if the attribute is multivalued, returns an AttributeReference.
     * <li>if the attribute is singlevalued, returns an Instance.
     * <li>if the attribute is scalar, returns a String.
     * </ul>
     * <p>If no attribute is found, entities are searched, then external parameters.</p>
     * @param key the name of the desired entity or root attribute.
     * @return an entity, an attribute reference, an instance, a string or null
     *     if not found. See  See above.
     */
    public Serializable get(Object key)
    {
        String property = db.adaptCase((String)key);

        try
        {
            Serializable result = null;

            /* 1) try the cache */
            result = cache.get(property);
            if(result != null)
            {
                return result;
            }

            /* 2) try to get a root attribute */
            Attribute attribute = db.getAttribute(property);

            if(attribute != null)
            {
                switch(attribute.getType())
                {
                    case Attribute.ROWSET :
                        result = new AttributeReference(this, attribute);
                        break;
                    case Attribute.SCALAR :
                        result = attribute.evaluate(this);
                        break;
                    case Attribute.ROW :
                        result = attribute.fetch(this);
                        break;
                    default :
                        Logger.error("Unknown attribute type encountered: db." + property);
                        result = null;
                }
                cache.put(property, result);
                return result;
            }

            /* 3) try to get a root action */
            Action action = db.getAction(property);

            if(action != null)
            {
                return Integer.valueOf(action.perform(this));
            }

            /* 4) try to get an entity */
            Entity entity = db.getEntity(property);

            if(entity != null)
            {
                result = new EntityReference(entity);
                cache.put(property, result);
                return result;
            }

            /* 5) try to get an external param */
            result = externalParams.get(property);
            if(result != null)
            {
                return result;
            }

            /* 6) try with the user context */
            result = db.getUserContext().get(property);
            if(result != null)
            {
                return result;
            }

            /* Sincerely, I don't see... */
            return null;
        }
        catch(SQLException sqle)
        {
            db.setError(sqle.getMessage());
            Logger.log(sqle);
            return null;
        }
    }

    /**
     * <p>Specific entity getter. </p>
     * @param key the name of the desired entity or root attribute.
     * @return an entity, an attribute reference, an instance, a string or null
     */
    public EntityReference getEntity(String key)
    {
        key = db.adaptCase(key);

        Entity entity = db.getEntity(key);

        if(entity != null)
        {
            EntityReference result = new EntityReference(entity);

            cache.put(key, result);
            return result;
        }
        return null;
    }

    /**
     * <p>Specific getter for last error message in user context </p>
     * @return last threaded error message if any or null
     */
    public String getError()
    {
        return(String)db.getUserContext().get("error");
    }

    /**
     * <p>clear error state in user context</p>
     */
    public void clearError()
    {
        db.getUserContext().setError(null);
    }

    /**
     * Default method handler, called by Velocity when it did not find the specified method.
     *
     * @param key asked key
     * @param params passed parameters
     * @see HasParametrizedGetter
     */
    public Serializable getWithParams(String key, SlotMap params)
    {
        Serializable result = null;

        try
        {
            String property = db.adaptCase(key);

            SlotMap fixedParams = new SlotTreeMap();
            for(Map.Entry<String,Serializable> entry : (Set<Map.Entry<String,Serializable>>)params.entrySet())
            {
                fixedParams.put(db.adaptCase(entry.getKey()), entry.getValue());
            }

            /* 1) try to get a root attribute */
            Attribute attribute = db.getAttribute(property);

            if(attribute != null)
            {
                switch(attribute.getType())
                {
                    case Attribute.ROWSET :
                        result = new AttributeReference(fixedParams, attribute);
                        break;
                    case Attribute.SCALAR :
                        result = attribute.evaluate(fixedParams);
                        break;
                    case Attribute.ROW :
                        result = attribute.fetch(fixedParams);
                        break;
                    default :
                        Logger.error("Unknown attribute type encountered: db." + property);
                }
            }

            /* 2) try to get a root action */
            Action action = db.getAction(property);

            if(action != null)
            {
                result = Integer.valueOf(action.perform(fixedParams));    // TODO actions should return a Long !!!!
            }
        }
        catch(SQLException sqle)
        {
            db.setError(sqle.getMessage());
            Logger.log(sqle);
            result = null;
        }
        return result;
    }

    /**
     * Default method handler, called by Velocity when it did not find the specified method.
     *
     * @param key asked key
     * @param params passed parameters
     * @see HasParametrizedGetter
     * @deprecated
     */
    @Deprecated
    public Serializable getWithParams(String key, Map params)
    {
        SlotMap p = new SlotHashMap();
        for(Map.Entry entry: (Set<Map.Entry>)params.entrySet())
        {
            p.put((String)entry.getKey(), (Serializable)entry.getValue());
        }
        return getWithParams(key, p);
    }

    /**
     * Generic setter used to set external params for children attributes.
     *
     * @param key name of the external parameter
     * @param value value given to the external parameter
     * @return the previous value, if any
     */
    public Serializable put(String key, Serializable value)
    {
        /*
         * Clear actual values in the cache, because the value of attributes may change...
         */
        for(Iterator i = cache.keySet().iterator(); i.hasNext(); )
        {
            Object k = i.next();
            Serializable v = cache.get(k);

            if(!(v instanceof AttributeReference) &&!(v instanceof EntityReference))
            {
                i.remove();
            }
        }
        return externalParams.put(db.adaptCase((String)key), value);
    }

    /**
     * Get the schema name.
     * @return the schema
     */
    public String getSchema()
    {
        return db.getSchema();
    }

    /**
     * Obfuscate the given value.
     * @param value value to obfuscate
     *
     * @return obfuscated value
     */
    public String obfuscate(Serializable value)
    {
        return db.obfuscate(value);
    }

    /**
     * De-obfuscate the given value.
     * @param value value to de-obfuscate
     *
     * @return obfuscated value
     */
    public String deobfuscate(Serializable value)
    {
        return db.deobfuscate(value);
    }

    /**
     * User context getter
     * @return current user context
     */
    public UserContext getUserContext()
    {
        return db.getUserContext();
    }

    /**
     * User context setter
     * @param userContext user context
     */
    public void setUserContext(UserContext userContext)
    {
        db.setUserContext(userContext);
    }

    /**
     * String representation of this db reference.
     * For now, returns a list of defined external parameters.
     */
    public String toString()
    {
        Entity root = db == null ? null : db.getRootEntity();
        return "[DBRef with root attributes " + (root == null ? "<uninitialized>" : root.getAttributes().toString()) + " and external params"
               + externalParams + "]";
    }

    /**
     * The wrapped database connection.
     */
    protected Database db = null;

    /**
     * A cache used by the generic getter. Its purpose is to avoid the creation of several
     * attribute references for the same multivalued attribute.
     */
    private SlotMap cache = null;

    /**
     * The map of external query parameters used by children attributes.
     */
    private SlotMap externalParams = null;

//  public void displayStats() { db.displayStats(); }
}
