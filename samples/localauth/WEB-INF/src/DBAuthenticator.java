package auth;

import auth.Authenticator;
import util.Logger;

import java.sql.SQLException;
import java.util.Arrays;

import velosurf.context.DBReference;
import velosurf.context.RowIterator;
import velosurf.sql.PooledPreparedStatement;
import velosurf.sql.Database;
import velosurf.tools.VelosurfTool;
import org.apache.velocity.tools.view.context.ViewContext;

public class DBAuthenticator extends Authenticator {

    private static Database sDb = null;

    public void init(Object initData) {
        super.init(initData);

        // init only if there was no error in super class
        if (initData instanceof ViewContext) {
            if (sDb == null) {
                sDb = VelosurfTool.getDefaultConnection(((ViewContext)initData).getServletContext());
            }
        }
    }

    protected String getPassword(String login) {
        try {
            PooledPreparedStatement stmt = sDb.prepare("select password from user where login = ?");
            RowIterator ri = stmt.query(Arrays.asList(new String[] {login}));
            while (ri.hasNext()) {
                return ""+ri.get("password");
            }
        } catch (SQLException sqle) {
            Logger.log(sqle);
        }
        return null;
    }

    protected Object getUser(String login) {
        try {
            DBReference dbr = new DBReference(sDb);
            dbr.put("login", login);
            return sDb.getAttribute("user_by_login").fetch(dbr);
        } catch (SQLException sqle) {
            Logger.log(sqle);
        }
        return null;
    }

}
