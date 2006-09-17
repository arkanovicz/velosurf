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

package velosurf.test;

import java.util.Arrays;
import java.util.Hashtable;

import velosurf.context.RowIterator;
import velosurf.sql.Database;
import velosurf.sql.PooledPreparedStatement;
import velosurf.util.Logger;

public class Test {
    
    public static void main(String args[]) {
        
        try {

            // test normal select
            Database db = Database.getInstance("user","pass","jdbc:mysql://127.0.0.1/db","");
            RowIterator row = db.query("select pr_name from project");
            while (row.next()!=null) {
                System.out.println(row.get("pr_name"));
            }

            row = db.query("select pr_name from project");
            while (row.next()!=null) {
                System.out.println(row.get("pr_name"));
            }
            
            // test prepared statement
            Hashtable rowt = null;
            PooledPreparedStatement st = db.prepare("select pr_name from project where pr_id=?");
            for (int i=1;i<=10;i++) {
                rowt = (Hashtable)st.fetch(Arrays.asList(new String[] {""+i}));
                System.out.println(rowt.get("pr_name"));
            }
            
            db.displayStats();

            db.close();
        
        } catch (Exception e) {
            Logger.log(e);
        }
    }
    
}
