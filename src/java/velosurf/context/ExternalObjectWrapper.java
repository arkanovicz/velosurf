package velosurf.context;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import velosurf.model.Entity;
import velosurf.util.Logger;

/**
 * <p>This wrapper allows one to specify custom mapping objects that don't inherit from Instance.</p>
 * <p>For now, the introspection is rather basic but may work for standard getters without ambiguity.</p>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class ExternalObjectWrapper extends Instance
{
    /**
     * Builds a new PlaiObjectWrapper.
     *
     * @param entity the related entity
     * @param object target object
     */
    public ExternalObjectWrapper(Entity entity, Object object)
    {
        super(entity);
        wrapped = object;

        Class clazz = wrapped.getClass();

        classInfo = classInfoMap.get(clazz.getName());
        if(classInfo == null)
        {
            classInfo = new ClassInfo(clazz);
            classInfoMap.put(clazz.getName(), classInfo);
        }
    }

    /**
     * Wrapper generic getter. Tries first to get the property from the wrapped object, and falls back to the superclass
     * if not found.
     *
     * @param key key of the property to be returned
     * @return a String, an Instance, an AttributeReference or null if not found or if an error
     *      occurs
     */
    public Object get(Object key)
    {
        Object ret = getExternal(key);

        if(ret == null)
        {
            ret = super.get(key);
        }
        return ret;
    }

    /**
     * External getter. Get a value on the external object
     *
     * @param key key of the property to be returned
     * @return a String, an Instance, an AttributeReference or null if not found or if an error
     *      occurs
     */
    public Object getExternal(Object key)
    {
        Method m = classInfo.getGetter((String)key);

        if(m != null)
        {
            try
            {
                return m.invoke(wrapped, m.getParameterTypes().length > 0 ? new Object[] { key } : new Object[] {});    // return even if result is null
            }
            catch(Exception e)
            {
                Logger.warn("could not get value of field " + getEntity().getName() + "." + key
                            + "... falling back to the Instance getter");
                Logger.log(e);
            }
        }
        return null;
    }

    /**
     * Wrapper generic setter. Tries first to set the property into the wrapped object, and falls back to the superclass
     * if not found.
     * @param key key of the property to be set
     * @param value corresponding value
     * @return previous value, or null
     */
    public Object put(String key, Object value)
    {
        Method m = classInfo.getSetter((String)key);

        if(m != null)
        {
            try
            {
                return m.invoke(wrapped,
                                m.getParameterTypes().length == 2
                                ? new Object[] { key, value } : new Object[] { value });
            }
            catch(Exception e)
            {
                Logger.warn("could not set value of field " + getEntity().getName() + "." + key + " to '" + value
                            + "'... falling back to the Instance setter");
                Logger.log(e);
            }
        }
        return super.put(key, value);
    }

    /**
     * <p>Try to update the row associated with this Instance using an update() method
     * in the external object.</p>
     *
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *     occurs (in which case $db.error can be checked).
     */
    public boolean update()
    {
        Method m = classInfo.getUpdate1();

        if(m != null)
        {
            Class cls = m.getReturnType();
            Object args[] = {};

            try
            {
                if(cls == boolean.class || cls == Boolean.class)
                {
                    return((Boolean)m.invoke(wrapped, args)).booleanValue();
                }
                else
                {
                    Logger.warn(
                        "external object wrapper: update method should return boolean or Boolean. Actual result will be ignored.");
                    m.invoke(wrapped, args);
                    return true;
                }
            }
            catch(Exception e)
            {
                Logger.warn("method" + cls.getName() + ".update() throw exception: " + e);
                return false;
            }
        }
        else
        {
            return super.update();
        }
    }

    /**
     * <p>Try to update the row associated with this Instance using an update(map) method
     * in the external object.</p>
     *
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *     occurs (in which case $db.error can be checked).
     */
    public boolean update(Map values)
    {
        Method m = classInfo.getUpdate2();

        if(m != null)
        {
            Class cls = m.getReturnType();
            Object args[] = { values };

            try
            {
                if(cls == boolean.class || cls == Boolean.class)
                {
                    return((Boolean)m.invoke(wrapped, args)).booleanValue();
                }
                else
                {
                    Logger.warn(
                        "external object wrapper: update method should return boolean or Boolean. Actual result will be ignored.");
                    m.invoke(wrapped, args);
                    return true;
                }
            }
            catch(Exception e)
            {
                Logger.warn("method" + cls.getName() + ".update() throw exception: " + e);
                return false;
            }
        }
        else
        {
            return super.update();
        }
    }

    /**
     * <p>Tries to delete the row associated with this Instance using a delete() method in the external object.
     * Velosurf will ensure all key columns are specified, to avoid an accidental massive update.</p>
     *
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *     occurs (in which case $db.error can be checked).
     */
    public boolean delete()
    {
        Method m = classInfo.getDelete();

        if(m != null)
        {
            Class cls = m.getReturnType();
            Object args[] = {};

            try
            {
                if(cls == boolean.class || cls == Boolean.class)
                {
                    return((Boolean)m.invoke(wrapped, args)).booleanValue();
                }
                else
                {
                    Logger.warn(
                        "external object wrapper: delete method should return boolean or Boolean. Actual result will be ignored.");
                    m.invoke(wrapped, args);
                    return true;
                }
            }
            catch(Exception e)
            {
                Logger.warn("method" + cls.getName() + ".delete() throw exception: " + e);
                return false;
            }
        }
        else
        {
            return super.delete();
        }
    }

    /**
     * Tries to insert a new row corresponding to this Instance using an insert() method in the external object.
     *
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *     occurs (in which case $db.error can be checked).
     */
    public boolean insert()
    {
        Method m = classInfo.getInsert();

        if(m != null)
        {
            Class cls = m.getReturnType();
            Object args[] = {};

            try
            {
                if(cls == boolean.class || cls == Boolean.class)
                {
                    return((Boolean)m.invoke(wrapped, args)).booleanValue();
                }
                else
                {
                    Logger.warn(
                        "external object wrapper: insert method should return boolean or Boolean. Actual result will be ignored.");
                    m.invoke(wrapped, args);
                    return true;
                }
            }
            catch(Exception e)
            {
                Logger.warn("method" + cls.getName() + ".delete() throw exception: " + e);
                return false;
            }
        }
        else
        {
            return super.insert();
        }
    }

    /**
     * Returns the underlying external object.
     *
     * @return the external object
     */
    public Object unwrap()
    {
        return wrapped;
    }

    /** The wrapped object. */
    Object wrapped = null;

    /** Info on the wrapped object class. */
    ClassInfo classInfo = null;

    /** A map of class infos. */
    static Map<String, ClassInfo> classInfoMap = new HashMap<String, ClassInfo>();

    /** A cache for the wrapped object getter methods. */
    Map getterCache = null;

    /** A cache for the wrapped object setter methods. */
    Map setterCache = null;

    /** This private class gathers informations on the class of wrapped objects. */
    static private class ClassInfo
    {
        ClassInfo(Class clazz)
        {
            this.clazz = clazz;
        }

        /**
         * Getter getter :-) .
         * @param key property name
         * @return property getter, if found
         */
        Method getGetter(String key)
        {
            Method result = (Method)getterMap.get(key);

            if(result == noSuchMethod)
            {
                return null;
            }
            if(result != null)
            {
                return result;
            }

            Class[] types = {};

            // getFoo
            StringBuffer sb = new StringBuffer("get");

            sb.append(key);
            try
            {
                result = clazz.getMethod(sb.toString(), types);
                getterMap.put(key, result);
                return result;
            }
            catch(NoSuchMethodException nsme) {}

            // getfoo
            char c = sb.charAt(3);

            sb.setCharAt(3, Character.isLowerCase(c) ? Character.toUpperCase(c) : Character.toLowerCase(c));
            try
            {
                result = clazz.getMethod(sb.toString(), types);
                getterMap.put(key, result);
                return result;
            }
            catch(NoSuchMethodException nsme) {}

            // get(foo)
            result = getGenericGetter();
            if(result == null)
            {
                getterMap.put(key, noSuchMethod);
            }
            else
            {
                getterMap.put(key, result);
            }
            return result;
        }

        /**
         * Setter getter
         * @param key property name
         * @return property setter, if found
         */
        Method getSetter(String key)
        {
            Method result = setterMap.get(key);

            if(result == noSuchMethod)
            {
                return null;
            }
            if(result != null)
            {
                return result;
            }

            Class[] types = {};

            /* setFoo */
            StringBuffer sb = new StringBuffer("set");

            sb.append(key);
            try
            {
                result = clazz.getMethod(sb.toString(), types);
                setterMap.put(key, result);
                return result;
            }
            catch(NoSuchMethodException nsme) {}

            /* setfoo */
            char c = sb.charAt(3);

            sb.setCharAt(3, Character.isLowerCase(c) ? Character.toUpperCase(c) : Character.toLowerCase(c));
            try
            {
                result = clazz.getMethod(sb.toString(), types);
                setterMap.put(key, result);
                return result;
            }
            catch(NoSuchMethodException nsme) {}

            /* put(foo,bar) */
            result = getGenericSetter();
            if(result == null)
            {
                setterMap.put(key, noSuchMethod);
            }
            else
            {
                setterMap.put(key, result);
            }
            return result;
        }

        /**
         * Tries to get an update() method in the wrapped object.
         * @return found update method, if any
         */
        Method getUpdate1()
        {
            if(update1 == noSuchMethod)
            {
                return null;
            }
            if(update1 != null)
            {
                return update1;
            }
            try
            {
                return update1 = clazz.getMethod("update", new Class[] {});
            }
            catch(NoSuchMethodException nsme)
            {
                update1 = noSuchMethod;
                return null;
            }
        }

        /**
         * Tries to get an update(Map) method in the wrapped object.
         * @return found update method, if any
         */
        Method getUpdate2()
        {
            if(update2 == noSuchMethod)
            {
                return null;
            }
            if(update2 != null)
            {
                return update2;
            }
            try
            {
                return update2 = clazz.getMethod("update", new Class[] { Map.class });
            }
            catch(NoSuchMethodException nsme)
            {
                update2 = noSuchMethod;
                return null;
            }
        }

        /**
         * Tries to get an insert() method in the wrapped object.
         * @return found method, if any
         */
        Method getInsert()
        {
            if(insert == noSuchMethod)
            {
                return null;
            }
            if(insert != null)
            {
                return insert;
            }
            try
            {
                return insert = clazz.getMethod("update", new Class[] { Map.class });
            }
            catch(NoSuchMethodException nsme)
            {
                insert = noSuchMethod;
                return null;
            }
        }

        /**
         * Tries to get a delete() method in the wrapped object.
         * @return found method, if any
         */
        Method getDelete()
        {
            if(delete == noSuchMethod)
            {
                return null;
            }
            if(delete != null)
            {
                return delete;
            }
            try
            {
                return delete = clazz.getMethod("update", new Class[] { Map.class });
            }
            catch(NoSuchMethodException nsme)
            {
                delete = noSuchMethod;
                return null;
            }
        }

        /**
         * Tries to get a generic getter in the wrapped object.
         * @return found method, if any
         */
        Method getGenericGetter()
        {
            if(genericGetter == noSuchMethod)
            {
                return null;
            }
            if(genericGetter != null)
            {
                return genericGetter;
            }

            Class[] types = new Class[] { Object.class };

            try
            {
                return genericGetter = clazz.getMethod("get", types);
            }
            catch(NoSuchMethodException nsme)
            {
                genericGetter = noSuchMethod;
                return null;
            }
        }

        /**
         * Tries to get a generic setter in the wrapped object.
         * @return found method, if any
         */
        Method getGenericSetter()
        {
            if(genericSetter == noSuchMethod)
            {
                return null;
            }
            if(genericSetter != null)
            {
                return genericSetter;
            }

            Class[] types = new Class[] { Object.class, Object.class };

            try
            {
                return genericSetter = clazz.getMethod("put", types);
            }
            catch(NoSuchMethodException nsme)
            {
                genericSetter = noSuchMethod;
                return null;
            }
        }

        /**
         * Wrapped class.
         */
        Class clazz;

        /**
         * Getter map.
         */
        Map<String, Method> getterMap = new HashMap<String, Method>();

        /**
         * Setter map.
         */
        Map<String, Method> setterMap = new HashMap<String, Method>();

        /**
         * Generic getter.
         */
        Method genericGetter = null;

        /**
         * Generic setter.
         */
        Method genericSetter = null;

        /**
         * Update method, first form.
         */
        Method update1 = null;

        /**
         * Update method, second form.
         */
        Method update2 = null;

        /**
         * Insert method.
         */
        Method insert = null;

        /**
         * Delete method.
         */
        Method delete = null;

        /* dummy method object used to remember we already tried to find an unexistant method. */
        static Method noSuchMethod;

        static
        {
            try
            {
                noSuchMethod = Object.class.getMethod("toString", new Class[] {});
            }
            catch(NoSuchMethodException nsme) {}
        }
    }
}
