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
import java.io.OutputStream;
import java.io.Writer;

public class LineWriterOutputStream extends OutputStream {

    protected Writer writer = null;
    protected StringBuffer buffer = new StringBuffer(200);

    /**
     * Construct a new WriterOutputStream, bound to the specified writer.
     * 
     * @param w the writer
     */
    public LineWriterOutputStream(Writer w) {
        writer = w;
    }

    /** 
     * Write a byte to this output stream.
     * 
     * @exception IOException may be thrown
     */
    public void write(int c) throws IOException {
        if (c=='\n') {
            writer.write(buffer.toString());
            buffer.delete(0,buffer.length());
        }
        else buffer.append((char)c);
    }
}
