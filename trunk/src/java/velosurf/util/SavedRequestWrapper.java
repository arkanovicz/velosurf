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

import velosurf.util.Enumerator;
import velosurf.util.FastHttpDateFormat;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletRequestWrapper;
import java.text.SimpleDateFormat;
import java.util.*;
import java.security.Principal;

//import com.blandware.atleap.webapp.util.core.FastHttpDateFormat;
//import com.blandware.atleap.webapp.util.core.Enumerator;

/**
 * <p>This class provides request parameters, headers, cookies from original requrest or saved request.</p>
 * <p><a href="CustomContextHolderAwareRequestWrapper.java.html"><i>View Source</i></a></p>
 *
 * @author Andrey Grebnev <a href="mailto:andrey.grebnev@blandware.com">&lt;andrey.grebnev@blandware.com&gt;</a>
 */

public class SavedRequestWrapper extends HttpServletRequestWrapper {

    protected SavedRequest savedRequest = null;

    protected static final TimeZone GMT_ZONE = TimeZone.getTimeZone("GMT");

    /**
     * The default Locale if none are specified.
     */
    protected static final Locale defaultLocale = Locale.getDefault();

    /**
     * The set of SimpleDateFormat formats to use in getDateHeader().
     *
     * Notice that because SimpleDateFormat is not thread-safe, we can't
     * declare formats[] as a static variable.
     */
    protected SimpleDateFormat formats[] = new SimpleDateFormat[3];

    //~ Constructors ===========================================================
    public SavedRequestWrapper(HttpServletRequest request) {
        super(request);

        HttpSession session = request.getSession(false);
        if (session == null)
            return;

        SavedRequest saved = (SavedRequest)session.getAttribute("velosurf.auth.saved-request");
        if (saved == null)
            return;

        String requestURI = saved.getRequestURI();
        if (requestURI == null)
            return;

        if (requestURI.equals(request.getRequestURI())) {
            savedRequest = saved;
            session.removeAttribute("velosurf.auth.saved-request");

            formats[0] = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
            formats[1] = new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US);
            formats[2] = new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US);

            formats[0].setTimeZone(GMT_ZONE);
            formats[1].setTimeZone(GMT_ZONE);
            formats[2].setTimeZone(GMT_ZONE);
        }
    }

    /**
     * The default behavior of this method is to return getMethod()
     * on the wrapped request object.
     */
    public String getMethod() {
        if (savedRequest == null) {
            return super.getMethod();
        } else {
            return savedRequest.getMethod();
        }
    }

    /**
     * The default behavior of this method is to return getHeader(String name)
     * on the wrapped request object.
     */
    public String getHeader(String name) {
        if (savedRequest == null) {
            return super.getHeader(name);
        } else {
            String header = null;
            Iterator iterator = savedRequest.getHeaderValues(name);
            while (iterator.hasNext()) {
                header = (String) iterator.next();
                break;
            }
            return header;
        }
    }

    /**
     * The default behavior of this method is to return getIntHeader(String name)
     * on the wrapped request object.
     */

    public int getIntHeader(String name) {
        if (savedRequest == null) {
            return super.getIntHeader(name);
        } else {
            String value = getHeader(name);
            if (value == null) {
                return (-1);
            } else {
                return (Integer.parseInt(value));
            }
        }
    }

    /**
     * The default behavior of this method is to return getDateHeader(String name)
     * on the wrapped request object.
     */
    public long getDateHeader(String name) {
        if (savedRequest == null) {
            return super.getDateHeader(name);
        } else {
            String value = getHeader(name);
            if (value == null)
                return (-1L);

            // Attempt to convert the date header in a variety of formats
            long result = FastHttpDateFormat.parseDate(value, formats);
            if (result != (-1L)) {
                return result;
            }
            throw new IllegalArgumentException(value);
        }
    }

    /**
     * The default behavior of this method is to return getHeaderNames()
     * on the wrapped request object.
     */

    public Enumeration getHeaderNames() {
        if (savedRequest == null) {
            return super.getHeaderNames();
        } else {
            return new Enumerator(savedRequest.getHeaderNames());
        }
    }


    /**
     * The default behavior of this method is to return getHeaders(String name)
     * on the wrapped request object.
     */
    public Enumeration getHeaders(String name) {
        if (savedRequest == null) {
            return super.getHeaders(name);
        } else {
            return new Enumerator(savedRequest.getHeaderValues(name));
        }
    }

    /**
     * The default behavior of this method is to return getCookies()
     * on the wrapped request object.
     */
    public Cookie[] getCookies() {
        if (savedRequest == null) {
            return super.getCookies();
        } else {
            List cookies = savedRequest.getCookies();
            return (Cookie[])cookies.toArray(new Cookie[cookies.size()]);
        }
    }

    /**
     * The default behavior of this method is to return getParameterValues(String name)
     * on the wrapped request object.
     */
    public String[] getParameterValues(String name) {
        if (savedRequest == null) {
            return super.getParameterValues(name);
        } else {
            return savedRequest.getParameterValues(name);
        }
    }

    /**
     * The default behavior of this method is to return getParameterNames()
     * on the wrapped request object.
     */

    public Enumeration getParameterNames() {
        if (savedRequest == null) {
            return super.getParameterNames();
        } else {
            return new Enumerator(savedRequest.getParameterNames());
        }
    }

    /**
     * The default behavior of this method is to return getParameterMap()
     * on the wrapped request object.
     */
    public Map getParameterMap() {
        if (savedRequest == null) {
            return super.getParameterMap();
        } else {
            return savedRequest.getParameterMap();
        }
    }

    /**
     * The default behavior of this method is to return getParameter(String name)
     * on the wrapped request object.
     */

    public String getParameter(String name) {
/*
        if (savedRequest == null) {
            return super.getParameter(name);
        } else {
            String value = null;
            String[] values = savedRequest.getParameterValues(name);
            if (values == null)
                return null;
            for (int i = 0; i < values.length; i++) {
                value = values[i];
                break;
            }
            return value;
        }
*/
        //we do not get value from super.getParameter because there is a bug in Jetty servlet-container
        String value = null;
        String[] values = null;
        if (savedRequest == null) {
            values = super.getParameterValues(name);
        } else {
            values = savedRequest.getParameterValues(name);
        }

        if (values == null)
            return null;
        for (int i = 0; i < values.length; i++) {
            value = values[i];
            break;
        }
        return value;

    }


    /**
     * The default behavior of this method is to return getLocales()
     * on the wrapped request object.
     */

    public Enumeration getLocales() {
        if (savedRequest == null) {
            return super.getLocales();
        } else {
            Iterator iterator = savedRequest.getLocales();
            if (iterator.hasNext()) {
                return new Enumerator(iterator);
            } else {
                ArrayList results = new ArrayList();
                results.add(defaultLocale);
                return new Enumerator(results.iterator());
            }
        }
    }

    /**
     * The default behavior of this method is to return getLocale()
     * on the wrapped request object.
     */

    public Locale getLocale() {
        if (savedRequest == null) {
            return super.getLocale();
        } else {
            Locale locale = null;
            Iterator iterator = savedRequest.getLocales();
            while (iterator.hasNext()) {
                locale = (Locale) iterator.next();
                break;
            }
            if (locale == null) {
                return defaultLocale;
            } else {
                return locale;
            }
        }
    }

    /**
     * Returns the <code>Authentication</code> (which is a subclass of
     * <code>Principal</code>), or <code>null</code> if unavailable.
     *
     * <p>We override this method in order to workaround the problem in Sun Java System Application Server 8.1 PE</p>
     * <p>This approach is tottaly incorrect, but as noone use this method it is safe</p>
     *
     * @return the <code>Authentication</code>, or <code>null</code>
     */
    public Principal getUserPrincipal() {
        //TODO remove this method at all when SJSAS bug will be fixed
        return null;
    }

}
