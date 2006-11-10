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
    /** replace a string by another inside a target string.
     *
     * @param target target string
     * @param oldPattern old pattern
     * @param newPattern new pattern
     * @return result
     */
    public static String replace(String target,String oldPattern,String newPattern) {
        if (target == null)
            return null;

        if (oldPattern==null || oldPattern.length()==0 || newPattern==null) return target;

        StringBuffer buff = new StringBuffer();
        int previous=0,offset=0,length=oldPattern.length();

        while ( (offset=target.indexOf(oldPattern,previous)) !=-1)
        {
            buff.append(target.substring(previous,offset));
            buff.append(newPattern);
            previous=offset+length;
        }
        buff.append(target.substring(previous));

        return buff.toString();

    }

    /** characters to trim.
     */
    private static String trimmed = " \t\r\n";

    /** trim spaces and EOL characters.
     *
     * @param target target string
     * @return the trimmed string
     */
    public static String trimSpacesAndEOL(String target) {
        if (target == null || target.length() == 0) return target;

        char c;
        int i=0;
        do {
            c = target.charAt(i++);
        } while (trimmed.indexOf(c) != -1 && i<target.length());

        int j=target.length();
        if (j>i) {
            do {
                c = target.charAt(--j);
            } while (trimmed.indexOf(c) != -1 && j<target.length());
        }
        else j--;
        return target.substring(i-1,j+1);
    }
}
