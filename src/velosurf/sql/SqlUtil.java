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

import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import velosurf.util.StringLists;
import velosurf.util.Logger;

/** various SQL-related helpers
 *
 * @author Claude Brisson
 *
 */

public class SqlUtil
{
    // that's crazy to have to code such a method...
    // in Ruby for instance, it's :
    //  '*' * n
    private static String stars(int length)
    {
        StringBuffer ret = new StringBuffer(length);
        for(int i=0;i<length;i++)ret.append('*');
        return ret.toString();
    }

	// add seach criteria to a query
	public static String refineQuery(String inQuery,List inCriteria) {

        if (inCriteria == null || inCriteria.size()==0) return inQuery;

        try {
            /* issue all searches on a string where all constant strings
             * (inside quotes) and subqueries (inside parenthesis) have been filtered
             */

            StringBuffer buffer = new StringBuffer(inQuery.toLowerCase());

            Matcher matcher = Pattern.compile("'[^']+'").matcher(buffer);
            while(matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                buffer.replace(start,end,stars(end-start));
            }

            matcher = Pattern.compile("\\([^()]+\\)").matcher(buffer);
            while(matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                buffer.replace(start,end,stars(end-start));
            }

            Matcher where = Pattern.compile("\\Wwhere\\W").matcher(buffer);
            Matcher groupby = Pattern.compile("\\Wgroup\\W+by\\W").matcher(buffer);
            Matcher orderby = Pattern.compile("\\Worder\\W+by\\W").matcher(buffer);

            int after = inQuery.length();
            if (groupby.find()) after = groupby.start();
            if (orderby.find()) after = Math.min(after,orderby.start());

            String query;
            String criteria = " (" + StringLists.join(inCriteria," ) and ( ") + ") ";

            if (where.find()) {
                // a little check
                if (where.start()>after) throw new Exception("Error: 'where' clause found after 'order by' or 'group by' clause");
                query = inQuery.substring(0,where.end()) + " ( " + inQuery.substring(where.end(),after) + ") and " + criteria + inQuery.substring(after);
            } else {
                query = inQuery.substring(0,after) + " where " + criteria + inQuery.substring(after);
            }
            return query;
        }
        catch(Exception ree) {
            Logger.warn("Could not refine query: " + inQuery);
            Logger.log(ree);
            return inQuery;
        }
	}

	public static String orderQuery(String inQuery,String inOrder) {

		if (inOrder == null || inOrder.length()==0) return inQuery;

        try {
            /* issue all searches on a string where all constant strings
             * (inside quotes) and subqueries (inside parenthesis) have been filtered
             */

            StringBuffer buffer = new StringBuffer(inQuery.toLowerCase());


            Matcher matcher = Pattern.compile("'[^']+'").matcher(buffer);
            while(matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                buffer.replace(start,end,stars(end-start));
            }

            matcher = Pattern.compile("\\([^()]+\\)").matcher(buffer);
            while(matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                buffer.replace(start,end,stars(end-start));
            }

            Matcher orderby = Pattern.compile("\\Worder\\W+by\\W").matcher(buffer);

            String query;

            if (orderby.find()) {
                Logger.warn("Query has already an 'order by' clause: "+inQuery);
                query = inQuery;
            } else {
                query = inQuery + " order by " + inOrder;
            }
            return query;
        }
        catch (Exception e) {
            Logger.log(e);
            return null; // or inQuery ?
        }
	}

    public static List getColumnNames(ResultSet inResultSet) throws SQLException {
        List columnNames = new ArrayList();
        ResultSetMetaData meta = inResultSet.getMetaData();
        int count = meta.getColumnCount();
        int type;
        for (int c=1;c<=count;c++)
            columnNames.add(meta.getColumnName(c));
        return columnNames;
    }
}
