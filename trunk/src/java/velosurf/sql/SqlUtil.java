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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import velosurf.util.Logger;
import velosurf.util.StringLists;

/**
 * various SQL-related helpers.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 */
public class SqlUtil
{
    // that's crazy to have to code such a method...
    // in Ruby for instance, it's :
    // '*' * n
    private static String stars(int length)
    {
        StringBuffer ret = new StringBuffer(length);

        for(int i = 0; i < length; i++)
        {
            ret.append('*');
        }
        return ret.toString();
    }

    /**
     *  add seach criteria to a query
     * @param query query
     * @param criteriaList list of criteria
     * @return new query
     */
    public static String refineQuery(String query, List criteriaList)
    {
        if(criteriaList == null || criteriaList.size() == 0)
        {
            return query;
        }
        try
        {
            Logger.trace("refining query: " + query);

            /*
             *  issue all searches on a string where all constant strings
             * (inside quotes) and subqueries (inside parenthesis) have been filtered
             */

            StringBuffer buffer = new StringBuffer(query.toLowerCase());
            Matcher matcher = Pattern.compile("'[^']+'").matcher(buffer);

            while(matcher.find())
            {
                int start = matcher.start();
                int end = matcher.end();

                buffer.replace(start, end, stars(end - start));
            }

            Pattern pattern = Pattern.compile("\\([^()]+\\)");

            while(true)
            {
                matcher = pattern.matcher(buffer);
                if(matcher.find())
                {
                    int start = matcher.start();
                    int end = matcher.end();

                    buffer.replace(start, end, stars(end - start));
                }
                else
                {
                    break;
                }
            }

            Matcher where = Pattern.compile("\\Wwhere\\W").matcher(buffer);
            Matcher groupby = Pattern.compile("\\Wgroup\\W+by\\W").matcher(buffer);
            Matcher orderby = Pattern.compile("\\Worder\\W+by\\W").matcher(buffer);
            int after = query.length();

            if(groupby.find())
            {
                after = groupby.start();
            }
            if(orderby.find())
            {
                after = Math.min(after, orderby.start());
            }

            String criteria = " (" + StringLists.join(criteriaList, " ) and ( ") + ") ";

            if(where.find())
            {
                // a little check
                if(where.start() > after)
                {
                    throw new Exception("Error: 'where' clause found after 'order by' or 'group by' clause");
                }
                query = query.substring(0, where.end()) + " ( " + query.substring(where.end(), after) + ") and "
                        + criteria + query.substring(after);
            }
            else
            {
                query = query.substring(0, after) + " where " + criteria + query.substring(after);
            }
            Logger.trace("refined query: " + query);
            return query;
        }
        catch(Exception ree)
        {
            Logger.warn("Could not refine query: " + query);
            Logger.log(ree);
            return query;
        }
    }

    /**
     * add an ordering clause to a query
     *
     * @param query initial query
     * @param order order clause
     * @return ordered query
     */
    public static String orderQuery(String query, String order)
    {
        if(order == null || order.length() == 0)
        {
            return query;
        }
        try
        {
            /*
             *  issue all searches on a string where all constant strings
             * (inside quotes) and subqueries (inside parenthesis) have been filtered
             */
            StringBuffer buffer = new StringBuffer(query.toLowerCase());
            Matcher matcher = Pattern.compile("'[^']+'").matcher(buffer);

            while(matcher.find())
            {
                int start = matcher.start();
                int end = matcher.end();

                buffer.replace(start, end, stars(end - start));
            }
            matcher = Pattern.compile("\\([^()]+\\)").matcher(buffer);
            while(matcher.find())
            {
                int start = matcher.start();
                int end = matcher.end();

                buffer.replace(start, end, stars(end - start));
            }

            Matcher orderby = Pattern.compile("\\Worder\\W+by\\W").matcher(buffer);

            if(orderby.find())
            {
                Logger.warn("Query has already an 'order by' clause: " + query);
            }
            else
            {
                query = query + " order by " + order;
            }
            return query;
        }
        catch(Exception e)
        {
            Logger.log(e);
            return null;    // or query ?
        }
    }

    /**
     * get the column nams of a result set
     * @param resultSet result set
     * @return list of columns
     * @throws SQLException
     */
    public static List<String> getColumnNames(ResultSet resultSet) throws SQLException
    {
        List<String> columnNames = new ArrayList<String>();
        ResultSetMetaData meta = resultSet.getMetaData();
        int count = meta.getColumnCount();

        for(int c = 1; c <= count; c++)
        {
            // see http://jira.springframework.org/browse/SPR-3541
            // columnNames.add(meta.getColumnName(c));
            columnNames.add(meta.getColumnLabel(c));
        }
        return columnNames;
    }
}
