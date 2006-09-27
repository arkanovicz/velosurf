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

package velosurf.util;

/** this class gathers static utility methods for strings
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 */
public class Strings
{
    /** replace a string by another inside a target string
     *
     * @param inTarget target string
     * @param inOldPattern old pattern
     * @param inNewPattern new pattern
     * @return result
     */
    public static String replace(String inTarget,String inOldPattern,String inNewPattern) {
        if (inTarget == null)
            return null;

        if (inOldPattern==null || inOldPattern.length()==0 || inNewPattern==null) return inTarget;

        StringBuffer buff = new StringBuffer();
        int previous=0,offset=0,length=inOldPattern.length();

        while ( (offset=inTarget.indexOf(inOldPattern,previous)) !=-1)
        {
            buff.append(inTarget.substring(previous,offset));
            buff.append(inNewPattern);
            previous=offset+length;
        }
        buff.append(inTarget.substring(previous));

        return buff.toString();

    }

    /** characters to trim
     */
    private static String sTrimmed = " \t\r\n";

    /** trim spaces and EOL characters (TODO : fix the typo - nothing to do with EOF characters)
     *
     * @param inTarget target string
     * @return the trimmed string
     */
    public static String trimSpacesAndEOF(String inTarget) {
        if (inTarget == null || inTarget.length() == 0) return inTarget;

        char c;
        int i=0;
        do {
            c = inTarget.charAt(i++);
        } while (sTrimmed.indexOf(c) != -1 && i<inTarget.length());

        int j=inTarget.length();
        if (j>i) {
            do {
                c = inTarget.charAt(--j);
            } while (sTrimmed.indexOf(c) != -1 && j<inTarget.length());
        }
        else j--;
//Logger.debug("TRIM from : '"+inTarget+"'\nTRIM to : '"+        inTarget.substring(i-1,j+1)+"'");
        return inTarget.substring(i-1,j+1);
    }
}
