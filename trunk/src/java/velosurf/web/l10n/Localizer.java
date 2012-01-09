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



package velosurf.web.l10n;

import java.util.Locale;

/**
 * To be implemented by localizers used by the engine to get localized strings from their database id.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 */
public interface Localizer
{
    /**
     * Locale setter.
     * @param locale locale
     */
    public void setLocale(Locale locale);

    /**
     * Locale getter.
     * @return locale
     */
    public Locale getLocale();

    /**
     * Message getter.
     * @param id message id
     * @return localized message
     */
    public String get(Object id);

    /**
     * Parameterized message getter.
     * @param id message id
     * @param param message parameters
     * @return localized message
     */
    public String get(Object id, Object... param);
}
