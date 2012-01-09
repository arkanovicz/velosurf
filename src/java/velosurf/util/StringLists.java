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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

/**
 * This static class gathers utilities for lists of strings.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 */
public class StringLists
{
    /**
     * builds an array list initially filled with <code>size</code> dummy objects.
     *
     * @param size size of the list
     * @return the new ArrayList
     */
    public static List<String> getPopulatedArrayList(int size)
    {
        List<String> list = new ArrayList<String>(size);
        String s = new String();

        for(int i = 0; i < size; i++)
        {
            list.add(s);
        }
        return list;
    }

    /**
     * joins strings using a separator.
     *
     * @param stringList strings to join
     * @param joinString separator to include between strings
     * @return the result of the join
     */
    public static String join(Collection stringList, String joinString)
    {
        Iterator i = stringList.iterator();
        StringBuffer result = new StringBuffer();

        if(!i.hasNext())
        {
            return "";
        }
        result.append(String.valueOf(i.next()));
        while(i.hasNext())
        {
            result.append(joinString);
            result.append(String.valueOf(i.next()));
        }
        return result.toString();
    }
}
