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
        _user = user;
        _password = password;
        _url = url;
        _schema = schema;
        _driver = driver;
        _autocommit= autocommit;
        _connections = new ArrayList();
        _min = min;
        _max = max;

        for(int i=0;i<_min;i++) {
            _connections.add(createConnection());
        }
    }

    public synchronized ConnectionWrapper getConnection() throws SQLException {
        for (Iterator it = _connections.iterator();it.hasNext();) {
            ConnectionWrapper c = (ConnectionWrapper)it.next();
            if (c.isClosed()) {
                it.remove();
            }
            else if (!c.isBusy()) {
                return c;
            }
        }
        if (_connections.size() == _max) {
            Logger.warn("Connection pool: max number of connections reached! ");
            // return a busy connection...
            return (ConnectionWrapper)_connections.get(0);
        }
        ConnectionWrapper newconn = createConnection();
        _connections.add(newconn);
        return newconn;
    }

    protected ConnectionWrapper createConnection() throws SQLException {

        Logger.info("Creating a new connection.");
        Connection connection = DriverManager.getConnection(_url,_user,_password);

//Logger.debug("#### before setting schema : "+getSchema(connection)+" ; schema to be set : "+_schema);

        // schema
        if (_schema != null) {
            String schemaQuery = _driver.getSchemaQuery();
            if (schemaQuery != null) {
                schemaQuery = Strings.replace(schemaQuery,"$schema",_schema);
                Statement stmt = connection.createStatement();
                stmt.executeUpdate(schemaQuery);
                stmt.close();
            }
        }

//Logger.debug("#### after setting schema : "+getSchema(connection));

        // autocommit
        connection.setAutoCommit(_autocommit);

        return new ConnectionWrapper(_driver,connection);
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
        for (Iterator it = _connections.iterator();it.hasNext();) {
            ConnectionWrapper c = (ConnectionWrapper)it.next();
            try { c.close(); } catch(SQLException sqle) {}
        }
    }

    protected String _user = null;
    protected String _password = null;
    protected String _url = null;
    protected String _schema = null;
    protected DriverInfo _driver = null;
    protected boolean _autocommit = true;
    protected List _connections = null;

    /* Minimum number of connections */
    protected int _min;

    /* Maximum number of connections */
    protected int _max;

}
