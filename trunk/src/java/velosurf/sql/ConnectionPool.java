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

import velosurf.util.Strings;
import velosurf.util.Logger;

import java.sql.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

/**
 *  Connection pool
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */

public class ConnectionPool {

    public ConnectionPool(String url,String user,String password,String schema,DriverInfo driver,boolean autocommit,int min,int max) throws SQLException{
        this.user = user;
        this.password = password;
        this.url = url;
        this.schema = schema;
        this.driver = driver;
        this.autocommit= autocommit;
        connections = new ArrayList();
        this.min = min;
        this.max = max;

        for(int i=0;i<this.min;i++) {
            connections.add(createConnection());
        }
    }

    public synchronized ConnectionWrapper getConnection() throws SQLException {
        for (Iterator it = connections.iterator();it.hasNext();) {
            ConnectionWrapper c = (ConnectionWrapper)it.next();
            if (c.isClosed()) {
                it.remove();
            }
            else if (!c.isBusy()) {
                return c;
            }
        }
        if (connections.size() == max) {
            Logger.warn("Connection pool: max number of connections reached! ");
            // return a busy connection...
            return (ConnectionWrapper)connections.get(0);
        }
        ConnectionWrapper newconn = createConnection();
        connections.add(newconn);
        return newconn;
    }

    protected ConnectionWrapper createConnection() throws SQLException {

        Logger.info("Creating a new connection.");
        Connection connection = DriverManager.getConnection(url,user,password);

        // schema
        if (schema != null) {
            String schemaQuery = driver.getSchemaQuery();
            if (schemaQuery != null) {
                schemaQuery = Strings.replace(schemaQuery,"$schema",schema);
                Statement stmt = connection.createStatement();
                stmt.executeUpdate(schemaQuery);
                stmt.close();
            }
        }

        // autocommit
        connection.setAutoCommit(autocommit);

        return new ConnectionWrapper(driver,connection);
    }

/*
protected String getSchema(Connection connection) throws SQLException
{
    Statement stmt = connection.createStatement();
    ResultSet rs = stmt.executeQuery("select sys_context('userenv','current_schema') from dual");
    rs.next();
    return rs.getString(1);
}*/

    public void clear() {
        for (Iterator it = connections.iterator();it.hasNext();) {
            ConnectionWrapper c = (ConnectionWrapper)it.next();
            try { c.close(); } catch(SQLException sqle) {}
        }
    }

    protected String user = null;
    protected String password = null;
    protected String url = null;
    protected String schema = null;
    protected DriverInfo driver = null;
    protected boolean autocommit = true;
    protected List connections = null;

    /* Minimum number of connections */
    protected int min;

    /* Maximum number of connections */
    protected int max;

}
