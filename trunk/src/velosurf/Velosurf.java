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

package velosurf;

/** this class is the Velosurf main entry class if you do not use the toolbox.xml mechanism.
 *  Unless you specify the config file to use, it will successively search for a configuration file :
 *  <ul>
 *  <li><p>from the java property "config.velosurf" (like in 'java -Dconfig.velosurf=/.../velosurf.xml ...' )</p>
 *  <li><p>from the Velocity property "config.velosurf" (entry of the velocity.properties file)</p>
 *  <li><p>as './velosurf.xml', './conf/velosurf.xml', './WEB-INF/velosurf.xml', './cfg/velosurf.xml'
 *
 * @author Claude Brisson
 *
 */

import velosurf.context.DBReference;
import velosurf.util.Logger;
import velosurf.sql.Database;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import org.apache.velocity.app.Velocity;

public class Velosurf extends DBReference
{
    public Velosurf() throws IOException,SQLException
    {
        this(findConfig());
    }

    public Velosurf(File file) throws IOException,SQLException
    {
        this(new FileInputStream(file));
    }

    public Velosurf(InputStream config)  throws IOException,SQLException
    {
        initLogging();
        init(config);
    }

    protected void initLogging()
    {
        Logger.log2Stderr();
    }

    protected static InputStream findConfig() throws IOException {
        String pathname = null;
        File file = null;

        // first, ask Java
        pathname = System.getProperty("velosurf.config");
        if (pathname != null) {
            file = new File(pathname);
            if (file.exists()) return new FileInputStream(file);
        }

        // then Velocity
        pathname = (String)Velocity.getProperty("velosurf.config");
        if (pathname != null) {
            file = new File(pathname);
            if (file.exists()) return new FileInputStream(file);
        }

        // then try some standard pathes
        String [] guesses = {
            "./velosurf.xml","./conf/velosurf.xml","./WEB-INF/velosurf.xml","./cfg/velosurf.xml"
        };
        for (int i=0;i<guesses.length;i++) {
            file = new File(guesses[i]);
            if (file.exists()) return new FileInputStream(file);
        }

        return null;
    }

    protected void init(InputStream configStream) throws SQLException,IOException {
        if (configStream == null) throw new IOException("Configuration InputStream is null!");
        Database db = Database.getInstance(configStream);
        super.init(db);
    }
}
