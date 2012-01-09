package velosurf.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * Simple class to allow casting a reader in an input stream.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 */
public class ReaderInputStream extends InputStream
{
    private Reader reader = null;

    public ReaderInputStream(Reader reader)
    {
        this.reader = reader;
    }

    public int read() throws IOException
    {
        return reader.read();
    }
}
