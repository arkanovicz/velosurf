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

package velosurf.sql;

import java.sql.SQLException;

/** this abstract class represents a pooled object.<p>
 * It has two booleans : inUse and useOver (understand : usageOver).<p>
 * The cycle of those two booleans is the following :<p>
 * states (inUse - useOver) : (false-false) -> (true-false) -> (true-true) -> [delay] (false-false)
 *
 * @author Claude Brisson
 *
 */
public abstract class Pooled {

	/** builds a new pooled object
	 */
	public Pooled() {
		mTagTime = System.currentTimeMillis();
	}

	/** get the time tag of this pooled object
	 *
	 * @return the time tag
	 */
	public long getTagTime() {
		return mTagTime;
	}

	/** reset the time tag
	 */
	public void resetTagTime() {
		mTagTime = System.currentTimeMillis();
	}

	/** notify this object that it is in use
	 */
	public void notifyInUse() {
		mInUse = true;
		resetTagTime();
	}

	/** notify this object that it is no more in use
	 */
	public void notifyOver() {
		mInUse = false;
	}

	/** checks whether this pooled object is in use
	 *
	 * @return whether this object is in use
	 */
	public boolean isInUse() {
		return mInUse;
	}

    /** checks whether this pooled object is marked as valid or invalid
     * (used in the recovery process)
     *
     * @return whether this object is in use
     */
    public boolean isValid() {
        return mValid;
    }

    /** definitely mark this statement as meant to be deleted
     */
    public void setInvalid() {
        mValid = false;
    }

    /** get the connection used by this statement
     *
     * @return the connection used by this statement
     */
    public abstract ConnectionWrapper getConnection();

	/** close this pooled object
	 *
	 * @exception SQLException when thrown by the database engine
	 */
    public abstract void close() throws SQLException;

	/** time tag
	 */
	protected long mTagTime = 0;
	// states (inUse - useOver) : (false-false) -> (true-false) -> (true-true) -> [delay] (false-false)

    /** valid statement ?
     */
    protected boolean mValid = true;

	/** is this object in use ?
	 */
	protected boolean mInUse = false;
	/** is the usage of this object over ?
	 */
	protected boolean mUseOver = false;
}