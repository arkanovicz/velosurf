package velosurf.util;

import java.lang.reflect.Method;
import java.util.Map;
import org.apache.velocity.util.introspection.*;
import velosurf.context.HasParametrizedGetter;
import velosurf.util.SlotMap;

/**
 * This uberspector allows getting a property while specifying extra external parameters
 * to the property. It resolves calls that look like a method call, with one Map argument,
 * but where the method name is in fact a property of the underlying object, which must implement
 * the HasParapetrizedGetter interface.
 *
 * @see org.apache.velocity.util.introspection.Uberspect#getMethod(java.lang.Object, java.lang.String,
 *      java.lang.Object[], org.apache.velocity.util.introspection.Info)
 */
public class VelosurfUberspector extends AbstractChainableUberspector
{
    public VelMethod getMethod(Object obj, String methodName, Object[] args, Info i) throws Exception
    {
        VelMethod ret = super.getMethod(obj, methodName, args, i);

        if(ret == null && obj instanceof HasParametrizedGetter && args.length == 1)
        {
            if(args[0] instanceof SlotMap)
            {
                Method method = obj.getClass().getMethod("getWithParams", String.class, SlotMap.class);
                ret = new VelParametrizedGetterMethod(methodName, method);
            }
            else if(args[0] instanceof Map)
            {
                Method method = obj.getClass().getMethod("getWithParams", String.class, Map.class);
                ret = new VelParametrizedGetterMethod(methodName, method);
            }
        }
        return ret;
    }

    public static class VelParametrizedGetterMethod implements VelMethod
    {
        protected String key;
        protected Method method;

        public VelParametrizedGetterMethod(String k, Method m)
        {
            key = k;
            method = m;
        }

        public Object invoke(Object obj, Object[] args) throws Exception
        {
            return method.invoke(obj, key, args[0]);
        }

        public boolean isCacheable()
        {
            return true;
        }

        public String getMethodName()
        {
            return "getWithParams";
        }

        public Class getReturnType()
        {
            return method.getReturnType();
        }
    }
}
