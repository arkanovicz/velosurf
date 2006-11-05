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

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

import velosurf.model.Attribute;
import velosurf.model.Entity;
import velosurf.model.Action;
import velosurf.sql.Database;
import velosurf.sql.ReadOnlyMap;
import velosurf.util.Logger;
import velosurf.util.UserContext;
import velosurf.web.l10n.Localizer;

/** A context wrapper for the main database connection object.<p>
 * The "$db" context variable is assigned a new instance of this class at each velocity parsing.
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 */

 // FIXME : right now, extends HashMap bkoz velocity wants a HashMap for setters

public class DBReference extends HashMap implements ReadOnlyMap
{
    /** Default constructor for use by derived classes
     */
    protected DBReference() {
    }

    /** Constructs a new database reference
     *
     * @param db the wrapped database connection
     */
    public DBReference(Database db) {
        init(db,null);
    }

    /** Constructs a new database reference
     *
     * @param db the wrapped database connection
     */
    public DBReference(Database db,UserContext userContext) {
        init(db,userContext);
    }

    /** protected initialization method
     *
     * @param db database connection
     */
    protected void init(Database db) {
        init(db,null);
    }

    /** protected initialization method
     *
     * @param db database connection
     */
    protected void init(Database db,UserContext userContext) {
       this. db = db;
        cache = new HashMap();
        externalParams = new HashMap();
        if(userContext == null) {
            userContext = new UserContext();
        }
        this.userContext = userContext;
    }

    /** Generic getter, used to access entities or root attributes by their name.<p>
     * For attributes, the return value depends upon the type of the attribute :
     * <ul>
     * <li>if the attribute is multivalued, returns an AttributeReference.
     * <li>if the attribute is singlevalued, returns an Instance.
     * <li>if the attribute is scalar, returns a String.
     * </ul>
     *
     * @param key the name of the desired entity or root attribute.
     * @return an entity, an attribute reference, an instance, a string or null
     *     if not found. See  See above.
     */
    public Object get(Object key) {
        String property = db.adaptCase((String) key);

        try {
            Object result=null;

            // 1) try the cache
            result = cache.get(property);
            if (result!=null) return result;

            // 2) try to get a root attribute
            Attribute attribute = db.getAttribute(property);
            if (attribute!=null) {
//                attribute.setExternalParams(externalParams);
                switch (attribute.getType()) {
                    case Attribute.ROWSET:
                        result = new AttributeReference(this,attribute,userContext);
                        break;
                    case Attribute.SCALAR:
                        result = attribute.evaluate(this);
                        break;
                    case Attribute.ROW:
                        result = attribute.fetch(this);
                        if (result instanceof Instance) {
                            ((Instance)result).setUserContext(userContext);
                        }
                        break;
                    default:
                        Logger.error("Unknown attribute type encountered: db."+property);
                        result=null;
                }
                cache.put(property,result);
                return result;
            }

            // 3) try to get a root action
            Action action = db.getAction(property);
            if (action != null) return Integer.valueOf(action.perform(this));

            // 3) try to get an entity
            Entity entity = db.getEntity(property);
            if (entity!=null) {
                result = new EntityReference(entity,userContext);
                cache.put(property,result);
                return result;
            }

            // 4) try to get an external param
            result = externalParams.get(property);
            if (result != null) return result;

            // 5) try with the user context
            result = userContext.get(property);
            if (result != null) return result;

            // Sincerely, I don't see...
            return null;
        }
        catch (SQLException sqle) {
            userContext.setError(sqle.getMessage());
            Logger.log(sqle);
            return null;
        }
    }

    /** Generic setter used to set external params for children attributes
     *
     * @param key name of the external parameter
     * @param value value given to the external parameter
     * @return the previous value, if any
     */
    public Object put(Object key,Object value) {
        /*
         * Clear actual values in the cache, because the value of attributes may change...
         */
        for(Iterator i = cache.keySet().iterator();i.hasNext();) {
            Object k = i.next();
            Object v = cache.get(k);
            if (! (v instanceof AttributeReference)
                && ! (v instanceof EntityReference)) {
                    i.remove();
            }

        }
        return externalParams.put(db.adaptCase((String) key),value);
    }

    /** get the schema
     * @return the schema
     */
    public String getSchema() {
        return db.getSchema();
    }

    /** obfuscate the given value
     * @param value value to obfuscate
     *
     * @return obfuscated value
     */
    public String obfuscate(Object value)
    {
        return db.obfuscate(value);
    }

    /** de-obfuscate the given value
     * @param value value to de-obfuscate
     *
     * @return obfuscated value
     */
    public String deobfuscate(Object value)
    {
        return db.deobfuscate(value);
    }

    /** the wrapped database connection
     */
    protected Database db = null;

    /** a cache used by the generic getter - it's purpose is to avoid the creation of several attribute references for the same multivalued attribute.
     */
    protected Map cache = null;

     /** The map of external query parameters used by children attributes
      */
    protected Map externalParams = null;

    /** a reference to the user context
     */
    protected UserContext userContext = null;
}
