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

import org.jdom.Element;
import org.jdom.Text;

import velosurf.sql.Database;
import velosurf.sql.DataAccessor;
import velosurf.util.Logger;
import velosurf.util.StringLists;
import velosurf.util.Strings;

/** This class correspond to custom update and delete queries
 *
 * @author Claude Brisson
 *
 */
public class Action
{
	/** Constructor
	 *
	 * @param inDB database connection
	 * @param inJDOMAction the XML tree for this action
	 */
	public Action(Entity inEntity,Element inJDOMAction) {
		mEntity = inEntity;
		mDB = mEntity.mDB;
		mName = inJDOMAction.getAttributeValue("name");
        defineQuery(inJDOMAction);
    }

	/** define the query from the XML tree
	 *
	 * @param inJDOMAction the XML tree
	 */
    protected void defineQuery(Element inJDOMAction) {
		mQuery="";
		mParamNames = new ArrayList();
		Iterator queryElements = inJDOMAction.getContent().iterator();
		while (queryElements.hasNext()) {
			Object content = queryElements.next();
			if (content instanceof Text) mQuery += Strings.trimSpacesAndEOF(((Text)content).getText());
			else {
				mQuery+=" ? ";
				Element elem = (Element)content;
				mParamNames.add(mDB.adaptCase(elem.getName()));
			}
		}
	}

	/** executes this action
	 *
	 * @param inSource the object on which apply the action
	 * @exception SQLException an SQL problem occurs
	 * @return number of impacted rows
	 */
	public int perform(DataAccessor inSource) throws SQLException {
		// TODO: check type
		List params = buildArrayList(inSource);
		return mDB.prepare(mQuery).update(params);
	}


	/** get the list of values for all parameters
	 *
	 * @param inSource the DataAccessor
	 * @exception SQLException thrown by the DataAccessor
	 * @return the list of values
	 */
	public List buildArrayList(DataAccessor inSource) throws SQLException {
		ArrayList result = new ArrayList();
		if (inSource!=null)
			for (Iterator i = mParamNames.iterator();i.hasNext();) {
				String paramName = (String)i.next();
				Object value = inSource.get(paramName);
				if (mEntity.isObfuscated(paramName)) value = mDB.deobfuscate(value);
				if (value == null) Logger.warn("Query "+mQuery+": param "+paramName+" is null!");
				result.add(value);
			}
		return result;
	}

	/** get the name of the action
	 *
	 * @return the name
	 */
	public String getName() {
		return mName;
	}

	/** for debugging purpose
	 *
	 * @return definition string
	 */
	public String toString() {
		String result = "";
		if (mParamNames.size()>0) result += "("+StringLists.join(mParamNames,",")+")";
		result+=":"+mQuery;
		return result;
	}

	/** get the database connection
	 *
	 * @return the database connection
	 */
	public Database getDB() {
		return mDB;
	}

	/** checks whether the action defined by this XML tree is a simple action or a transaction
	 *
	 * @param inElement XML tree defining an action
	 * @return true if the action is a transaction
	 */
    public static boolean isTransaction(Element inElement) {
        Iterator queryElements = inElement.getContent().iterator();
        while (queryElements.hasNext()) {
            Object content = queryElements.next();
            if (content instanceof Text) {
                String text = Strings.trimSpacesAndEOF(((Text)content).getText());
                char[] chars = text.toCharArray();
                boolean insideLitteral = false;
                for (int i=0;i<chars.length;i++) {
                    if(chars[i] == '\'') insideLitteral = !insideLitteral;
                    else if (!insideLitteral && chars[i] == ';' && i<chars.length-1)
                        return true;
                }
            }
        }
        return false;
    }

	/** the satabase connection
	 */
	protected Database mDB = null;
	/** the entity this action belongs to
	 */
	protected Entity mEntity = null;
	/** the name of this action
	 */
	protected String mName = null;

    // for simple actions
	/** parameter names of this action
	 */
	protected List mParamNames = null;
	/** query of this action
	 */
	protected String mQuery = null;

}
