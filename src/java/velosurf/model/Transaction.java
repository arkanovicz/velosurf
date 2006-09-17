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

/** This class is an action that gather several consecutive queries
 *
 *  <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 */
public class Transaction extends Action
{
	/** Builds a new transaction
	 *
	 * @param inEntity entity
	 * @param inJDOMAction XML tree
	 */
    public Transaction(Entity inEntity,Element inJDOMAction) {
        super(inEntity,inJDOMAction);
    }

	/** builds the queries
	 *
	 * @param inJDOMAction XML tree
	 */
    protected void defineQuery(Element inJDOMAction) {
        mQueries = new ArrayList();
        mParamNamesList = new ArrayList();
        StringBuilder query = new StringBuilder();
        List paramNames = new ArrayList();
        Iterator queryElements = inJDOMAction.getContent().iterator();
        while (queryElements.hasNext()) {
            Object content = queryElements.next();
            if (content instanceof Text) {
                String text = Strings.trimSpacesAndEOF(((Text)content).getText());
                int i = text.indexOf(';');
                if (i!=-1) {
                    query.append(text.substring(0,i));
                    mQueries.add(query);
                    mParamNamesList.add(paramNames);
                    query = new StringBuilder();
                    paramNames = new ArrayList();
                }
                query.append(text.substring(i+1));
            }
            else {
                query.append(" ? ");
                Element elem = (Element)content;
                paramNames.add(elem.getName());
            }
        }
        if (query.length()>0) {
            mQueries.add(query);
            mParamNamesList.add(paramNames);
        }
    }

	/** performs this action
	 *
	 * @param inSource DataAccessor containing parameter values
	 * @exception SQLException thrown from the database
	 * @return number of affected rows (addition of all the partial counts)
	 */
    public int perform(DataAccessor inSource) throws SQLException {
        // Since for now we only have one connection for transactions,
        // we have to synchronize on it ! Otherwise commits and rollbacks
        // could create a holy mess...
        // Since transactionnal updates occur much less often than selects,
        // it will be enough for this first version of transactions.
        // FIXME : use a connection pool for transactions to avoid
        // this synchronization.
        synchronized(mDB.getTransactionConnection()) {
            try {
                int nb = mQueries.size();
                int ret = 0;
                for (int i=0; i<nb; i++) {
                    // fool the buildArrayList method by using
                    //  the super member mParamNames
                    mParamNames = (List)mParamNamesList.get(i);
                    List params = buildArrayList(inSource);
                    ret += mDB.transactionPrepare((String)mQueries.get(i)).update(params);
                }
                mDB.getTransactionConnection().commit();
                return ret;
            }
            catch (SQLException sqle) {
                mDB.getTransactionConnection().rollback();
                throw sqle;
            }
        } // synchronized
    }

	/** debug method
	 *
	 * @return the definition string of the transaction
	 */
    public String toString() {
        StringBuffer result = new StringBuffer();
        int nb = mQueries.size();
        int ret = 0;
        for (int i=0; i<nb; i++) {
            List paramNames = (List)mParamNamesList.get(i);
            if (paramNames.size()>0) {
                result.append("(");
                result.append(StringLists.join(paramNames,",")+")");
            }
            result.append(":"+mQueries.get(i));
            if (i<nb-1) result.append('\n');
        }
        return result.toString();

    }

	/** all the queries
	 */
    protected List mQueries; //  = null; WARNING : this init code is executed AFER Action constructor
	/** list of lists of parameter names
	 */
    protected List mParamNamesList; // = null;

}
