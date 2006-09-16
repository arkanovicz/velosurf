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
import velosurf.sql.DataAccessor;
import velosurf.util.Logger;
import velosurf.local.Localizer;

/** A context wrapper for the main database connection object.<p>
 * The "$db" context variable is assigned a new instance of this class at each velocity parsing.
 *
 * @author Claude Brisson
 */

 // FIXME : right now, extends HashMap bkoz velocity wants a HashMap for setters

public class DBReference extends HashMap implements DataAccessor
{
	/** Default constructor for use by derived classes
	 */
    protected DBReference() {
    }

	/** Constructs a new reference
	 *
	 * @param inDB the wrapped database connection
	 */
	public DBReference(Database inDB) {
        init(inDB);
	}

	/** protected initialization method
	 *
	 * @param inDB database connection
	 */
    protected void init(Database inDB) {
        mDB = inDB;
        mCache = new HashMap();
        mExternalParams = new HashMap();
    }

	/** Get the last error message, or null if none occured.
	 *
	 * @return The last error message, or null
	 */
	public String getError() {
		return mDB.getError();
	}

	/** Generic getter, used to access entities or root attributes by their name.<p>
	 * For attributes, the return value depends upon the type of the attribute :
	 * <ul>
	 * <li>if the attribute is multivalued, returns an AttributeReference.
	 * <li>if the attribute is singlevalued, returns an Instance.
	 * <li>if the attribute is scalar, returns a String.
	 * </ul>
	 *
	 * @param inKey the name of the desired entity or root attribute.
	 * @return an entity, an attribute reference, an instance, a string or null
	 *     if not found. See  See above.
	 */
	public Object get(Object inKey) {

        String inProperty = mDB.adaptCase((String) inKey);

		try {
			Object result=null;

			// 1) try the cache
			result = mCache.get(inProperty);
			if (result!=null) return result;

			// 2) try to get a root attribute
			Attribute attribute = mDB.getAttribute(inProperty);
			if (attribute!=null) {
//                attribute.setExternalParams(mExternalParams);
				switch (attribute.getType()) {
					case Attribute.ROWSET:
						result = new AttributeReference(this,attribute,mLocalizer);
                        if (mLocalizer != null) ((RowIterator)result).setLocalizer(mLocalizer);
						break;
					case Attribute.SCALAR:
						result = attribute.evaluate(this);
						break;
					case Attribute.ROW:
						result = attribute.fetch(this);
                        if (mLocalizer != null) ((Instance)result).setLocalizer(mLocalizer);
						break;
					default:
						Logger.error("Unknown attribute type encountered: db."+inProperty);
						result=null;
				}
				mCache.put(inProperty,result);
				return result;
			}

            // 3) try to get a root action
            Action action = mDB.getAction(inProperty);
            if (action != null) return Integer.valueOf(action.perform(this));

			// 3) try to get an entity
			Entity entity = mDB.getEntity(inProperty);
			if (entity!=null) {
				result = new EntityReference(entity,mLocalizer);
				mCache.put(inProperty,result);
				return result;
			}

            // 4) try to get an external param
            result = mExternalParams.get(inProperty);
            if (result != null) return result;

			// Sincerely, I don't see...
			return null;
		}
		catch (SQLException sqle) {
			mDB.setError(sqle.getMessage());
			Logger.log(sqle);
			return null;
		}
	}

	/** Generic setter used to set external params for children attributes
	 *
	 * @param inKey name of the external parameter
	 * @param inValue value given to the external parameter
	 * @return the previous value, if any
	 */
	public Object put(Object inKey,Object inValue) {
        /*
         * Clear actual values in the cache, because the value of attributes may change...
         */
        for(Iterator i = mCache.keySet().iterator();i.hasNext();) {
            Object key = i.next();
            Object value = mCache.get(key);
            if (! (value instanceof AttributeReference)
                && ! (value instanceof EntityReference)) {
                    i.remove();
            }

        }
        return mExternalParams.put(mDB.adaptCase((String) inKey),inValue);
	}

    /** get the schema
     * @return the schema
     */
    String getSchema() {
        return mDB.getSchema();
    }

    /** obfuscate the given value
     * @param value value to obfuscate
     *
     * @return obfuscated value
     */
    public String obfuscate(Object value)
    {
        return mDB.obfuscate(value);
    }

    /** de-obfuscate the given value
     * @param value value to de-obfuscate
     *
     * @return obfuscated value
     */
    public String deobfuscate(Object value)
    {
        return mDB.deobfuscate(value);
    }

	/** the wrapped database connection
	 */
	protected Database mDB = null;

	/** a cache used by the generic getter - it's purpose is to avoid the creation of several attribute references for the same multivalued attribute.
	 */
	protected Map mCache = null;

     /** The map of external query parameters used by children attributes
      */
    protected Map mExternalParams = null;

    /** The localizer object
     */
    protected Localizer mLocalizer = null;
}
