package velosurf.util;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * A conversion handler adds admissible conversions between Java types whenever Velocity introspection has to map
 * VTL methods and property accessors to Java methods.
 * Both methods must be consistent: <code>getNeededConverter</code> must not return <code>null</code> whenever
 * <code>isExplicitelyConvertible</code> returned true with the same arguments.
 *
 * @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 * @version $Id: ConversionHandler.java $
 * @since 2.0
 */

import java.io.Serializable;

public interface ConversionHandler extends Serializable
{
    /**
     * Determines whether a type represented by a class object is
     * convertible to another type represented by a class object using a
     * method invocation conversion, treating object types of primitive
     * types as if they were primitive types (that is, a Boolean actual
     * parameter type matches boolean primitive formal type). This behavior
     * is because this method is used to determine applicable methods for
     * an actual parameter list, and primitive types are represented by
     * their object duals in reflective method calls.
     *
     * @param formal the formal parameter type to which the actual
     * parameter type should be convertible
     * @param actual the actual parameter type.
     * @return true if either formal type is assignable from actual type,
     * or formal is a primitive type and actual is its corresponding object
     * type or an object type of a primitive type that can be converted to
     * the formal type.
     */
    public boolean isMethodInvocationConvertible(Class formal, Class actual);

    /**
     * Check to see if the conversion can be done using an explicit conversion
     * @param formal expected formal type
     * @param actual provided argument type
     * @return null if no conversion is needed, or the appropriate Converter object
     * @since 2.0
     */
    public boolean isExplicitlyConvertible(Class formal, Class actual, boolean possibleVarArg);

    /**
     * Returns the appropriate Converter object needed for an explicit conversion
     * Returns null if no appropriate conversion conversion is found.
     *
     * @param formal expected formal type
     * @param actual provided argument type
     * @return null if no conversion is needed, or the appropriate Converter object
     * @since 2.0
     */
    public Converter getNeededConverter(final Class formal, final Class actual);

    /**
     * Add the given converter to the handler. Implementation should be thread-safe.
     *
     * @param formal expected formal type
     * @param actual provided argument type
     * @param converter converter
     * @since 2.0
     */
    public void addConverter(Class formal, Class actual, Converter converter);

}
