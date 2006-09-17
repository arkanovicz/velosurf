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

import java.io.IOException;
import java.io.Writer;

import javax.servlet.ServletContext;

/** This class implements a writer towards the servlet log
 */
public class ServletLogWriter extends Writer
{
    /** builds a new ServletLogWriter
     * 
     * @param log ServletContext
     */
    public ServletLogWriter(ServletContext log) {
        mLog = log;
    }
    

    /** writes an array of chars to the servlet log
     * 
     * @param cbuf characters to write
     * @param off offset in the array
     * @param len number of characters to write
     * @exception IOException thrown by underlying servlet logger
     */
    public void write(char[] cbuf,int off,int len) throws IOException {
        // ignore \r\n & \n
        if ( (len==2 && cbuf[off]==13 && cbuf[off+1]==10) || (len==1 && cbuf[off]==10)) return;
        String s=new String(cbuf,off,len);        
        mLog.log(s);
    }

    /** flush any pending output
     * 
     * @exception IOException thrown by underlying servlet logger
     */
    public void flush() throws IOException    {
    }
    
    /** close the writer
     * 
     * @exception IOException thrown by underlying servlet logger
     */
    public void close() throws IOException {
    }
    
    /** the ServletContext object used to log
     */
    protected ServletContext mLog = null;
}
