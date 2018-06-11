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

import java.io.InputStream;
import java.io.StringReader;
import java.util.Date;
import org.apache.velocity.util.ExtProperties;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.Resource;
import org.apache.velocity.runtime.resource.loader.ResourceLoader;
import velosurf.context.DBReference;
import velosurf.context.EntityReference;
import velosurf.web.VelosurfTool;
import java.io.Reader;
/**
 * A database resource loader for use with Velosurf. Experimental.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 */
public class DBResourceLoader extends ResourceLoader
{
    protected DBReference db = null;
    protected EntityReference table = null;
    protected String entity = null;
    protected String dataField = null;
    protected String timestampField = null;

    public void init(ExtProperties configuration)
    {
        entity = configuration.getString("entity", "template");
        dataField = configuration.getString("data", "data");
        timestampField = configuration.getString("lastmodified", "lastmodified");
        initdb();
    }

    protected synchronized void initdb()
    {
        if(db == null)
        {
            db = VelosurfTool.getDefaultInstance();
            if(db != null)
            {
                table = (EntityReference)db.get(entity);
            }
        }
    }

    public InputStream getResourceStream(String id) throws ResourceNotFoundException
    {
        if(db == null)
        {
            initdb();
        }

        String template = (String)table.fetch(id).get(dataField);

        return new ReaderInputStream(new StringReader(template));
    }

    public boolean isSourceModified(Resource resource)
    {
        return((Date)table.fetch(resource.getName()).get(timestampField)).getTime() > resource.getLastModified();
    }

    public long getLastModified(Resource resource)
    {
        return((Date)table.fetch(resource.getName()).get(timestampField)).getTime();
    }

    public Reader getResourceReader(String source,String encoding)
    {
        //TODO AA return a Reader for real ...
        return null;
    }
}
