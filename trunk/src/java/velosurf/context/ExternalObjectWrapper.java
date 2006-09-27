package velosurf.context;

import velosurf.model.Entity;
import velosurf.util.Logger;

import java.util.Map;
import java.util.TreeMap;
import java.lang.reflect.Method;

/**
 * <p>This wrapper allows one to specify custom mapping objects that don't inherit from Instance.</p>
 * <p>For now, the introspection is rather basic but may work for standard getters without ambiguïty.</p>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class ExternalObjectWrapper extends Instance {

    /* Builds a new PlaiObjectWrapper
     *
     * @param inEntity the related entity
     * @param inObject target object
     */
    public ExternalObjectWrapper(Entity inEntity,Object inObject) {
        super(inEntity);
        mWrapped = inObject;
        mGetterCache = new TreeMap();
    }

    /*
     * Wrapper generic getter: tries first to get the property from the wrapped object, and falls back to the superclass
     * if not found.
     *
     * @param inKey key of the property to be returned
     * @return a String, an Instance, an AttributeReference or null if not found or if an error
     *      occurs
     */
    public Object get(Object inKey) {
        Object ret = getExternal(inKey);
        if (ret == null) ret = super.get(inKey);
        return ret;
    }

    /*
     * External getter: get a value on the external object
     *
     * @param inKey key of the property to be returned
     * @return a String, an Instance, an AttributeReference or null if not found or if an error
     *      occurs
     */
    public Object getExternal(Object inKey) {
        Method m = findGetter((String)inKey);
        if (m != null) {
            try {
                return m.invoke(mWrapped,m.getParameterTypes().length>0?new Object[] {inKey}:new Object[]{}); // return even if result is null
            }
            catch(Exception e) {
                Logger.warn("could not get value of field "+mEntity.getName()+"."+inKey+"... falling back to the Instance getter");
                Logger.log(e);
            }
        }
        return null;
    }


    /*
    * Wrapper generic setter: tries first to set the property into the wrapped object, and falls back to the superclass
    * if not found.
    * @param key key of the property to be set
    * @param value corresponding value
    * @return previous value, or null
     */
    public Object put(Object inKey,Object inValue) {
        Method m = findSetter((String)inKey);
        if (m != null) {
            try {
                return m.invoke(mWrapped,m.getParameterTypes().length==2?new Object[] {inKey,inValue}:new Object[]{inValue});
            }
            catch(Exception e) {
                Logger.warn("could not set value of field "+mEntity.getName()+"."+inKey+" to '"+inValue+"'... falling back to the Instance setter");
                Logger.log(e);
            }
        }
        return super.put(inKey,inValue);
    }

    /** <p>Try to update the row associated with this Instance using an update() method
     * in the external object.</p>
     *
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *     occurs (in which case $db.lastError can be checked).
     */
    public boolean update() {
        Method m = findMethod("update",new Class[] {});
        if (m != null) {
            Class cls = m.getReturnType();
            Object args[] = {};
            try {
                if (cls == boolean.class || cls == Boolean.class) {
                    return ((Boolean)m.invoke(mWrapped,args)).booleanValue();
                } else {
                    Logger.warn("external object wrapper: update method should return boolean or Boolean. Actual result will be ignored.");
                    m.invoke(mWrapped,args);
                    return true;
                }
            } catch(Exception e) {
                Logger.warn("method"+cls.getName()+".update() throw exception: "+e);
                return false;
            }
        }
        else return super.update();
    }

    /** <p>Try to update the row associated with this Instance using an update(map) method
     * in the external object.</p>
     *
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *     occurs (in which case $db.lastError can be checked).
     */
    public boolean update(Map inValues) {
        Method m = findMethod("update",new Class[] {Map.class});
        if (m != null) {
            Class cls = m.getReturnType();
            Object args[] = { inValues };
            try {
                if (cls == boolean.class || cls == Boolean.class) {
                    return ((Boolean)m.invoke(mWrapped,args)).booleanValue();
                } else {
                    Logger.warn("external object wrapper: update method should return boolean or Boolean. Actual result will be ignored.");
                    m.invoke(mWrapped,args);
                    return true;
                }
            } catch(Exception e) {
                Logger.warn("method"+cls.getName()+".update() throw exception: "+e);
                return false;
            }
        }
        else return super.update();
    }

    /** Tries to delete the row associated with this Instance using a delete() method in the external object.<p>
     * Velosurf will ensure all key columns are specified, to avoid an accidental massive update.
     *
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *     occurs (in which case $db.lastError can be checked).
     */
    public boolean delete() {
        Method m = findMethod("delete",new Class[] {});
        if (m != null) {
            Class cls = m.getReturnType();
            Object args[] = {};
            try {
                if (cls == boolean.class || cls == Boolean.class) {
                    return ((Boolean)m.invoke(mWrapped,args)).booleanValue();
                } else {
                    Logger.warn("external object wrapper: delete method should return boolean or Boolean. Actual result will be ignored.");
                    m.invoke(mWrapped,args);
                    return true;
                }
            } catch(Exception e) {
                Logger.warn("method"+cls.getName()+".delete() throw exception: "+e);
                return false;
            }
        }
        else return super.delete();
    }

    /** Tries to insert a new row corresponding to this Instance using an insert() method in the external object.
     *
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *     occurs (in which case $db.lastError can be checked).
     */
    public boolean insert() {
        Method m = findMethod("insert",new Class[] {});
        if (m != null) {
            Class cls = m.getReturnType();
            Object args[] = {};
            try {
                if (cls == boolean.class || cls == Boolean.class) {
                    return ((Boolean)m.invoke(mWrapped,args)).booleanValue();
                } else {
                    Logger.warn("external object wrapper: insert method should return boolean or Boolean. Actual result will be ignored.");
                    m.invoke(mWrapped,args);
                    return true;
                }
            } catch(Exception e) {
                Logger.warn("method"+cls.getName()+".delete() throw exception: "+e);
                return false;
            }
        }
        else return super.insert();
    }


    /*
     * Returns the underlying external object
     *
     * @return the external object
     */
    public Object unwrap() {
        return mWrapped;
    }

    /*
     * Tries to find an appropriate getter in the wrapped object for the given key
     *
     * @param inKey key of the searched property
     * @return the getter method that corresponds to this property, or null
     */
    protected Method findGetter(String inKey) {

        Method result = (Method)mGetterCache.get(inKey);
        if (result != null) return result;

        Class cls = mWrapped.getClass();
        Class [] types = {};

        // getFoo
        StringBuffer sb = new StringBuffer("get");
        sb.append(inKey);
        try {
            result = cls.getMethod(sb.toString(), types);
            mGetterCache.put(inKey,result);
            return result;
        }
        catch (NoSuchMethodException nsme) {}

        // getfoo
        char c = sb.charAt(3);
        sb.setCharAt(3,Character.isLowerCase(c)?Character.toUpperCase(c):Character.toLowerCase(c));
        try {
            result = cls.getMethod(sb.toString(),types);
            mGetterCache.put(inKey,result);
            return result;
        }
        catch (NoSuchMethodException nsme) {}

        // get(foo)
        types = new Class[] { Object.class };
        try {
            result = cls.getMethod("get",types);
            mGetterCache.put(inKey,result);
            return result;
        }
        catch (NoSuchMethodException nsme) {}

        return null;
    }

    /*
     * Tries to find an appropriate setter in the wrapped object for the given key
     *
     * @param inKey key of the searched property
     * @return the setter method that corresponds to this property, or null
     */
    protected Method findSetter(String inKey) {
        Method result = (Method)mSetterCache.get(inKey);
        if (result != null) return result;

        Class cls = mWrapped.getClass();
        Class [] types = {};

        // setFoo
        StringBuffer sb = new StringBuffer("set");
        sb.append(inKey);
        try {
            result = cls.getMethod(sb.toString(), types);
            mSetterCache.put(inKey,result);
            return result;
        }
        catch (NoSuchMethodException nsme) {}

        // setfoo
        char c = sb.charAt(3);
        sb.setCharAt(3,Character.isLowerCase(c)?Character.toUpperCase(c):Character.toLowerCase(c));
        try {
            result = cls.getMethod(sb.toString(),types);
            mSetterCache.put(inKey,result);
            return result;
        }
        catch (NoSuchMethodException nsme) {}

        // put(foo,bar)
        types = new Class[] { Object.class,Object.class };
        try {
            result = cls.getMethod("put",types);
            mSetterCache.put(inKey,result);
            return result;
        }
        catch (NoSuchMethodException nsme) {}

        return null;
    }

    /* Tries to find a named method in the external object
     *
     *  @param inName the name of the method
     *  @param inArgs the types of the arguments
     */
    protected Method findMethod(String inName,Class[] inArgs) {
        try {
            return mWrapped.getClass().getMethod(inName,inArgs);
        }
        catch(NoSuchMethodException nsme) {}
        return null;
    }

    /* the wrapped object */
    Object mWrapped = null;

    /* a cache for the wrapped object getter methods */
    Map mGetterCache = null;

    /* a cache for the wrapped object setter methods */
    Map mSetterCache = null;
}
