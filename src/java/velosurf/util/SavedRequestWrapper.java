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

import java.text.SimpleDateFormat;
import java.util.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;

/**
 * <p>This class provides request parameters, headers, cookies from original requrest or saved request.</p>
 *
 * @author Andrey Grebnev <a href="mailto:andrey.grebnev@blandware.com">&lt;andrey.grebnev@blandware.com&gt;</a>
 * @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public @SuppressWarnings("deprecation")
class SavedRequestWrapper extends HttpServletRequestWrapper
{
    /** the saved request. */
    private SavedRequest savedRequest = null;

    /** timezone. */
    private static final TimeZone GMT_ZONE = TimeZone.getTimeZone("GMT");

    /**
     * The default Locale if none are specified.
     */
    private static final Locale defaultLocale = Locale.getDefault();

    /**
     * The set of SimpleDateFormat formats to use in getDateHeader().
     *
     * Notice that because SimpleDateFormat is not thread-safe, we can't
     * declare formats[] as a static variable.
     */
    private SimpleDateFormat formats[] = new SimpleDateFormat[3];

    /**
     * Constructor
     * @param request to save
     */
    public SavedRequestWrapper(HttpServletRequest request, SavedRequest saved)
    {
        super(request);

        HttpSession session = request.getSession(false);

        if(session == null)
        {
            return;
        }

        String requestURI = saved.getRequestURI();

        if(requestURI == null)
        {
            return;
        }
        savedRequest = saved;
        formats[0] = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        formats[1] = new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US);
        formats[2] = new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US);
        formats[0].setTimeZone(GMT_ZONE);
        formats[1].setTimeZone(GMT_ZONE);
        formats[2].setTimeZone(GMT_ZONE);
    }

    /**
     * The default behavior of this method is to return getMethod()
     * on the wrapped request object.
     * @return the HTTP method
     */
    public String getMethod()
    {
        return savedRequest.getMethod();
    }

    /**
     * The default behavior of this method is to return getHeader(String name)
     * on the wrapped request object.
     * @param name header name
     * @return header value or null
     */
    public String getHeader(String name)
    {
        String header = null;
        Iterator iterator = savedRequest.getHeaderValues(name);

        while(iterator.hasNext())
        {
            header = (String)iterator.next();
            break;
        }
        return header;
    }

    /**
     * The default behavior of this method is to return getIntHeader(String name)
     * on the wrapped request object.
     * @param name integer header name
     * @return integer header value or -1
     */
    public int getIntHeader(String name)
    {
        String value = getHeader(name);

        if(value == null)
        {
            return(-1);
        }
        else
        {
            return(Integer.parseInt(value));
        }
    }

    /**
     * The default behavior of this method is to return getDateHeader(String name)
     * on the wrapped request object.
     * @param name date header name
     * @return date header value or null
     */
    public long getDateHeader(String name)
    {
        String value = getHeader(name);

        if(value == null)
        {
            return(-1L);
        }

        // Attempt to convert the date header in a variety of formats
        long result = FastHttpDateFormat.parseDate(value, formats);

        if(result != (-1L))
        {
            return result;
        }
        throw new IllegalArgumentException(value);
    }

    /**
     * The default behavior of this method is to return getHeaderNames()
     * on the wrapped request object.
     * @return an enumeration of header names
     */
    public Enumeration getHeaderNames()
    {
        return new Enumerator(savedRequest.getHeaderNames());
    }

    /**
     * The default behavior of this method is to return getHeaders(String name)
     * on the wrapped request object.
     * @param name multivalued header
     * @return enumeration of values for this header
     */
    public Enumeration getHeaders(String name)
    {
        return new Enumerator(savedRequest.getHeaderValues(name));
    }

    /**
     * The default behavior of this method is to return getCookies()
     * on the wrapped request object.
     * @return cookies array
     */
    public Cookie[] getCookies()
    {
        List cookies = savedRequest.getCookies();

        return(Cookie[])cookies.toArray(new Cookie[cookies.size()]);
    }

    /**
     * The default behavior of this method is to return getParameterValues(String name)
     * on the wrapped request object.
     * @param name parameter name
     * @value array of values
     */
    public String[] getParameterValues(String name)
    {
        return savedRequest.getParameterValues(name);
    }

    /**
     * The default behavior of this method is to return getParameterNames()
     * on the wrapped request object.
     * @return enumeration of parameter names
     */
    public Enumeration getParameterNames()
    {
        return new Enumerator(savedRequest.getParameterNames());
    }

    /**
     * The default behavior of this method is to return getParameterMap()
     * on the wrapped request object.
     * @return parameter map
     */
    public Map getParameterMap()
    {
        return savedRequest.getParameterMap();
    }

    /**
     * The default behavior of this method is to return getParameter(String name)
     * on the wrapped request object.
     * @param name parameter name
     * @return  parameter value
     */
    public String getParameter(String name)
    {
        String[] values = savedRequest.getParameterValues(name);

        if(values == null)
        {
            return null;
        }
        else
        {
            return values[0];
        }
    }

    /**
     * The default behavior of this method is to return getLocales()
     * on the wrapped request object.
     * @return enumeration of locales
     */
    public Enumeration getLocales()
    {
        Iterator iterator = savedRequest.getLocales();

        if(iterator.hasNext())
        {
            return new Enumerator(iterator);
        }
        else
        {
            ArrayList results = new ArrayList();

            results.add(defaultLocale);
            return new Enumerator(results.iterator());
        }
    }

    /**
     * The default behavior of this method is to return getLocale()
     * on the wrapped request object.
     * @return locale
     */
    public Locale getLocale()
    {
        Locale locale = null;
        Iterator iterator = savedRequest.getLocales();

        while(iterator.hasNext())
        {
            locale = (Locale)iterator.next();
            break;
        }
        if(locale == null)
        {
            return defaultLocale;
        }
        else
        {
            return locale;
        }
    }
}
