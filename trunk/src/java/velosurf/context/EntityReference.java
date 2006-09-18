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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.sql.SQLException;

import velosurf.model.Entity;
import velosurf.util.Logger;
import velosurf.i18n.Localizer;

// inherits AbstractList so that Velocity will call iterator() from within a #foreach directive
/** Context wrapper for an entity.
 *
 *  <a href=mailto:claude.brisson.com>Claude Brisson</a>
 */
public class EntityReference extends AbstractList
{
    /** Builds a new EntityReference.
     *
     * @param inEntity the wrapped entity
     */
    public EntityReference(Entity inEntity) {
        this(inEntity,null);
        mEntity = inEntity;
    }

    /** Builds a new EntityReference.
     *
     * @param inEntity the wrapped entity
     */
    public EntityReference(Entity inEntity,Localizer inLocalizer) {
        mEntity = inEntity;
        mLocalizer = inLocalizer;
    }

    /** Insert a new row in this entity's table.
     *
     * @param inValues col -> value map
     * @return <code>true</code> if successfull, <code>false</code> if an error occurs (in which case $db.lastError can be checked).
     */
    public boolean insert(Map inValues) {
        return mEntity.insert(inValues);
    }

    /** Returns the ID of the last inserted row (obfuscated if needed)
     *
     * @return last insert ID
     */
    public Object getLastInsertID() {
        return mEntity.getLastInsertID();
    }

    /** Update a row in this entity's table.
     * <p>
     * Velosurf will ensure all key columns are specified, to avoid an accidental massive update.
     *
     * @param inValues col -> value map
     * @return <code>true</code> if successfull, <code>false</code> if an error occurs (in which case $db.lastError can be checked).
     */
    public boolean update(Map inValues) {
        return mEntity.update(inValues);
    }

    /** Detele a row from this entity's table.
     * <p>
     * Velosurf will ensure all key columns are specified, to avoid an accidental massive update.
     * <p>
     *
     * @param inValues col -> value map
     * @return <code>true</code> if successfull, <code>false</code> if an error occurs (in which case $db.lastError can be checked).
     */
    public boolean delete(Map inValues) {
        return mEntity.delete(inValues);
    }

    /** Fetch an Instance of this entity, specifying the values of its key columns in their natural order.
     *
     * @param inValues values of the key columns
     * @return an Instance, or null if an error occured (in which case
     *     $db.lastError can be checked)
     */
    public Instance fetch(List inValues) {
        Instance instance = mEntity.fetch(inValues);
        if (mLocalizer != null) instance.setLocalizer(mLocalizer);
        return instance;
    }

    /** Fetch an Instance of this entity, specifying the values of its key columns in the map.
     *
     * @param inValues key=>value map
     * @return an Instance, or null if an error occured (in which case
     *     $db.lastError can be checked)
     */
    public Instance fetch(Map inValues) {
        Instance instance = mEntity.fetch(inValues);
        if (mLocalizer != null) instance.setLocalizer(mLocalizer);
        return instance;
    }

    /** Fetch an Instance of this entity, specifying the value of its unique key column as a string
     *
     * @param inKeyValue value of the key column
     * @return an Instance, or null if an error occured (in which case
     *     $db.lastError can be checked)
     */
    public Instance fetch(String inKeyValue) {
        Instance instance = mEntity.fetch(inKeyValue);
        if (mLocalizer != null) instance.setLocalizer(mLocalizer);
        return instance;
    }

    /** Fetch an Instance of this entity, specifying the value of its unique key column as an integer
     *
     * @param inKeyValue value of the key column
b     * @return an Instance, or null if an error occured (in which case
     *     $db.lastError can be checked)
     */
    public Instance fetch(Number inKeyValue) {
        Instance instance = mEntity.fetch(inKeyValue);
        if (mLocalizer != null) instance.setLocalizer(mLocalizer);
        return instance;
    }

    // called by the #foreach directive
    /** Called by the #foreach directive.
     *
     * @return a RowIterator on all instances of this entity, possibly previously
     *      refined or ordered.
     */
    public Iterator iterator() {
        RowIterator iterator =  mEntity.query(mRefineCriteria,mOrder);
        if (mLocalizer != null) iterator.setLocalizer(mLocalizer);
        return iterator;
    }

    /** gets all the rows in a list of maps
     *
     * @return a list of all the rows
     */
    public List getRows() throws SQLException {
        RowIterator iterator = mEntity.query(mRefineCriteria,mOrder);
        if (mLocalizer != null) iterator.setLocalizer(mLocalizer);
        return iterator.getRows();
    }

    /** Refines this entity reference querying result: the provided criterium will be added to the 'where' clause (or a 'where' clause will be added).
     * <p>
     * This method can be called several times, thus allowing a field-by-field handling of an html search form.
     * <p>
     * All criteria will be merged with the sql 'and' operator (if there is an initial where clause, it is wrapped into parenthesis).
     * <p>
     * Example: if we issue the following calls from inside the template:
     * <blockquote>
     * $person.refine("age>30")
     * <p>
     * $person.refine("salary>3000")
     * </blockquote>
     * the resulting query that will be issed is:
     * <p>
     * select * from person where (age>30) and (salary>3000)
     *
     * @param inCriterium a valid sql condition
     */
    public void refine(String inCriterium) {
        Logger.debug("refineS: "+inCriterium);
        if (mRefineCriteria == null) mRefineCriteria = new ArrayList();
        mRefineCriteria.add(inCriterium);
    }

    /** Clears any refinement made on this entity
     * <p>
     */
    public void clearRefinement() {
        mRefineCriteria = null;
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

    /** Create a new instance for this entity
     *
     * @return null
     */
    public Instance newInstance() {
        Instance instance = mEntity.newInstance();
        if (mLocalizer != null) instance.setLocalizer(mLocalizer);
        return instance;
    }

    /** getter for the list of column names
     *
     * @return the list of column names
     */
    public List getColumns() {
        return mEntity.getColumns();
    }


    /** Dummy method. Since this class has to appear as a Collection for Velocity, it extends the AbstractList class but only the iterator() method has a real meaning.
     *
     * @param i ignored
     * @return null
     */
    public Object get(int i) { return null; }

    /** Dummy method. Since this class has to appear as a Collection for Velocity, it extends the AbstractList class but only the iterator() method has a real meaning.
     *
     * @return 0
     */
    public int size() { return 0; }

    /** the wrapped entity
     */
    protected Entity mEntity = null;

    /** specified order
     */
    protected String mOrder = null;

    /** specified refining criteria
     */
    protected List mRefineCriteria = null;

    /** localizer to give to created instances
     *
     */
    protected Localizer mLocalizer = null;
}
