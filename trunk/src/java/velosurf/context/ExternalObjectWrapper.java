package velosurf.context;

import velosurf.model.Entity;
import velosurf.util.Logger;

import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import java.lang.reflect.Method;

/**
 * <p>This wrapper allows one to specify custom mapping objects that don't inherit from Instance.</p>
 * <p>For now, the introspection is rather basic but may work for standard getters without ambiguity.</p>
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
        Class clazz = mWrapped.getClass();
        mClassInfo = sClassInfoMap.get(clazz.getName());
        if (mClassInfo == null) {
            mClassInfo = new ClassInfo(clazz);
            sClassInfoMap.put(clazz.getName(),mClassInfo);
        }
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
        Method m = mClassInfo.getGetter((String)inKey);
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
        Method m = mClassInfo.getSetter((String)inKey);
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
        Method m = mClassInfo.getUpdate1();
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
        Method m = mClassInfo.getUpdate2();
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
        Method m = mClassInfo.getDelete();
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
        Method m = mClassInfo.getInsert();
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

    /* info on the wrapped object class */
    ClassInfo mClassInfo = null;

    /* a map of class infos */
    static Map<String,ClassInfo> sClassInfoMap = new HashMap<String,ClassInfo>();

    /* a cache for the wrapped object getter methods */
    Map mGetterCache = null;

    /* a cache for the wrapped object setter methods */
    Map mSetterCache = null;

    static private class ClassInfo {
        ClassInfo(Class clazz) {
            this.clazz = clazz;
        }

        Method getGetter(String key) {
            Method result = (Method)getterMap.get(key);
            if(result == noSuchMethod) {
                return null;
            }
            if (result != null) {
                return result;
            }

            Class [] types = {};

            // getFoo
            StringBuffer sb = new StringBuffer("get");
            sb.append(key);
            try {
                result = clazz.getMethod(sb.toString(), types);
                getterMap.put(key,result);
                return result;
            }
            catch (NoSuchMethodException nsme) {}

            // getfoo
            char c = sb.charAt(3);
            sb.setCharAt(3,Character.isLowerCase(c)?Character.toUpperCase(c):Character.toLowerCase(c));
            try {
                result = clazz.getMethod(sb.toString(),types);
                getterMap.put(key,result);
                return result;
            }
            catch (NoSuchMethodException nsme) {}

            // get(foo)
            result = getGenericGetter();
            if (result == null) {
                getterMap.put(key,noSuchMethod);
            } else {
                getterMap.put(key,result);
            }
            return result;

        }

        Method getSetter(String key) {
            Method result = setterMap.get(key);
            if(result == noSuchMethod) {
                return null;
            }
            if (result != null) {
                return result;
            }

            Class [] types = {};

            // setFoo
            StringBuffer sb = new StringBuffer("set");
            sb.append(key);
            try {
                result = clazz.getMethod(sb.toString(), types);
                setterMap.put(key,result);
                return result;
            }
            catch (NoSuchMethodException nsme) {}

            // setfoo
            char c = sb.charAt(3);
            sb.setCharAt(3,Character.isLowerCase(c)?Character.toUpperCase(c):Character.toLowerCase(c));
            try {
                result = clazz.getMethod(sb.toString(),types);
                setterMap.put(key,result);
                return result;
            }
            catch (NoSuchMethodException nsme) {}

            // put(foo,bar)
            result = getGenericSetter();
            if (result == null) {
                setterMap.put(key,noSuchMethod);
            } else {
                setterMap.put(key,result);
            }
            return result;
        }

        Method getUpdate1() {
            if(update1 == noSuchMethod) {
                return null;
            }
            if (update1 != null) {
                return update1;
            }
            try {
                return update1 = clazz.getMethod("update",new Class[] {});
            } catch(NoSuchMethodException nsme) {
                update1 = noSuchMethod;
                return null;
            }
        }

        Method getUpdate2() {
            if(update2 == noSuchMethod) {
                return null;
            }
            if (update2 != null) {
                return update2;
            }
            try {
                return update2 = clazz.getMethod("update",new Class[] {Map.class});
            } catch(NoSuchMethodException nsme) {
                update2 = noSuchMethod;
                return null;
            }
        }

        Method getInsert() {
            if(insert == noSuchMethod) {
                return null;
            }
            if (insert != null) {
                return insert;
            }
            try {
                return insert = clazz.getMethod("update",new Class[] {Map.class});
            } catch(NoSuchMethodException nsme) {
                insert = noSuchMethod;
                return null;
            }
        }

        Method getDelete() {
            if(delete == noSuchMethod) {
                return null;
            }
            if (delete != null) {
                return delete;
            }
            try {
                return delete = clazz.getMethod("update",new Class[] {Map.class});
            } catch(NoSuchMethodException nsme) {
                delete = noSuchMethod;
                return null;
            }
        }

        Method getGenericGetter() {
            if(genericGetter == noSuchMethod) {
                return null;
            }
            if (genericGetter != null) {
                return genericGetter;
            }
            Class[] types = new Class[] { Object.class };
            try {
                return genericGetter = clazz.getMethod("get",types);
            }
            catch (NoSuchMethodException nsme) {
                genericGetter = noSuchMethod;
                return null;
            }
        }

        Method getGenericSetter() {
            if (genericSetter == noSuchMethod) {
                return null;
            }
            if(genericSetter != null) {
                return genericSetter;
            }
            Class[] types = new Class[] { Object.class,Object.class };
            try {
                return genericSetter = clazz.getMethod("put",types);
            }
            catch (NoSuchMethodException nsme) {
                genericSetter = noSuchMethod;
                return null;
            }
        }

        Class clazz;
        Map<String,Method> getterMap = new HashMap<String,Method>();
        Map<String,Method> setterMap = new HashMap<String,Method>();
        Method genericGetter = null;
        Method genericSetter = null;
        Method update1 = null;
        Method update2 = null;
        Method insert = null;
        Method delete = null;
        /* dummy method object used to remember we already tried to find an unexistant method */
        static Method noSuchMethod;
        static {
            try {
                noSuchMethod = Object.class.getMethod("toString",new Class[]{});
            } catch(NoSuchMethodException nsme) {}
        }
    }
}
