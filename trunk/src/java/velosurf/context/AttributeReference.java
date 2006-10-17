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
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import velosurf.model.Attribute;
import velosurf.sql.ReadOnlyMap;
import velosurf.util.Logger;
import velosurf.util.UserContext;

/** Context wrapper for attributes
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 */
public class AttributeReference extends AbstractList
{

    /** Constructor for attributes
     *
     * @param inReadOnlyMap the data accessor this attribute reference applies to
     * @param inAttribute the wrapped attribute
     */
    public AttributeReference(ReadOnlyMap inReadOnlyMap,Attribute inAttribute) {
        this(inReadOnlyMap,inAttribute,null);
    }

    /** Constructor for attributes
     *
     * @param inReadOnlyMap the data accessor this attribute reference applies to
     * @param inAttribute the wrapped attribute
     */
    public AttributeReference(ReadOnlyMap inReadOnlyMap,Attribute inAttribute,UserContext usrCtx) {
        mReadOnlyMap = inReadOnlyMap;
        mAttribute = inAttribute;
        userContext = usrCtx;
    }

    /** Refines this attribute's reference result : the provided criterium will be added to the 'where' clause (or a 'where' clause will be added).
     * <p>
     * This method can be called several times, thus allowing a field-by-field handling of an html search form.
     * <p>
     * All criteria will be merged with the sql 'and' operator (if there is an initial where clause, it is wrapped into parenthesis).
     * <p>
     * Example : suppose we have defined the attribute 'person.children' as " *person(person_id):select * from person where parent_id=?". Then, if we issue the following calls from inside the template :
     * <blockquote>
     * $bob.children.refine("age>18")
     * <p>
     * $bob.children.refine("gender='F'")
     * </blockquote>
     * the resulting query that will be issed is :
     * <p>
     * select * from person where (parent_id=?) and (age>18) and (gender='F')
     *
     * @param inCriterium a valid sql condition
     */
    public void refine(String inCriterium) {
        if (mRefineCriteria == null) mRefineCriteria = new ArrayList();
        mRefineCriteria.add(inCriterium);
    }

    /** Clears any refinement made on this attribute
     * <p>
     */
    public void clearRefinement() {
        mRefineCriteria = null;
    }

    /** Called by the #foreach directive.
     * <p>
     * Returns a RowIterator on all possible instances of this entity, possibly previously refined and ordered.
     *
     * @return a RowIterator on instances of this entity, or null if an error
     *     occured.
     */
    public Iterator iterator() {
        try {
            RowIterator iterator = mAttribute.query(mReadOnlyMap,mRefineCriteria,mOrder);
            if (userContext != null) iterator.setUserContext(userContext);
            return iterator;
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            if (userContext != null) {
                userContext.setError(sqle.getMessage());
            }
            return null;
        }
    }

    /** gets all the rows in a list of maps
     *
     * @return a list of all the rows
     */
    public List getRows() throws SQLException {
        RowIterator iterator = mAttribute.query(mReadOnlyMap,mRefineCriteria,mOrder);
        if (userContext != null) iterator.setUserContext(userContext);
        return iterator.getRows();
    }

    /** Specify an 'order by' clause for this attribute reference result.<p>
     * If an 'order by' clause is already present in the original query, the new one is appended (but successive calls to this method overwrite previous ones)<p>
     * Pass it null or an empty string to clear any ordering.
     *
     * @param inOrder valid sql column names (separated by commas) indicating the
     *      desired order
     */
    public void setOrder(String inOrder) {
        mOrder = inOrder;
    }

    /** Dummy method. Since this class has to appear as a Collection for Velocity, it extends the AbstractList class but only the iterator() method has a real meaning.
     *
     * @param i ignored
     * @return null
     */
    public Object get(int i) { return null; }
    /** Not yet implemented. Will return the number of rows.
     *
     * @return 0
     */
    public int size() { return 0; }

    /** Specified refining criteria defined on this attribute reference.
     */
    protected List mRefineCriteria = null;
    /** Specified 'order by' clause specified for this attribute reference.
     */
    protected String mOrder = null;
    /** The data accessor this attribute reference applies to.
     */
    protected ReadOnlyMap mReadOnlyMap = null;
    /** The wrapped attribute.
     */
    protected Attribute mAttribute = null;

    /** user context
     */
    protected UserContext userContext = null;
}
