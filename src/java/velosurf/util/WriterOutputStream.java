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

/** an output stream wrapping a writer.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 */

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

public class WriterOutputStream extends OutputStream {
    /** wrapped writer. */
    private Writer writer = null;

    /**
     * Construct a new WriterOutputStream, bound to the specified writer.
     *
     * @param w the writer
     */
    public WriterOutputStream(Writer w) {
        writer = w;
    }

    /**
     * Write a byte to this output stream.
     * @param c byte
     * @exception IOException may be thrown
     */
    public void write(int c) throws IOException {
        writer.write(c);
    }
}
