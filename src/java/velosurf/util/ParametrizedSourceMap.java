package velosurf.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import velosurf.util.Logger;

public class ParametrizedSourceMap implements SlotMap
{
    private SlotMap source;
    private SlotMap parameters;
    
    public ParametrizedSourceMap(SlotMap source, SlotMap parameters)
    {
        this.source = source;
        this.parameters = parameters;
    }

    public SlotMap getSource()
    {
        return source;
    }

    public SlotMap getParameters()
    {
        return parameters;
    }
    
    public void clear()
    {
        Logger.error("trying to modify a read-only ParametrizedSourceMap");
    }

    public boolean containsKey(Object key)
    {
        return source.containsKey(key) || parameters.containsKey(key);
    }

    public boolean containsValue(Object value)
    {
        return source.containsValue(value) || parameters.containsValue(value);
    }

    public Set<Map.Entry<String, Serializable>> entrySet()
    {
        Set<Map.Entry<String, Serializable>> ret = source.entrySet();
        ret.addAll(parameters.entrySet());
        return ret;
    }

    public boolean equals(Object o)
    {
        if (o == null || !(o instanceof ParametrizedSourceMap)) return false;
        ParametrizedSourceMap other = (ParametrizedSourceMap)o;
        return source.equals(other.getSource()) && parameters.equals(other.getParameters());
    }

    public Serializable get(Object key)
    {
        Serializable ret = source.get(key);
        return ret == null ? parameters.get(key) : ret;
    }

    public int hashCode()
    {
        return source.hashCode() ^ parameters.hashCode();
    }

    public boolean isEmpty()
    {
        return source.isEmpty() && parameters.isEmpty();
    }

    public Set<String> keySet()
    {
        Set<String> ret = source.keySet();
        ret.addAll(parameters.keySet());
        return ret;
    }

    public Serializable put(String key, Serializable value)
    {
        Logger.error("trying to modify a read-only ParametrizedSourceMap");
        return null;
    }

    public void putAll(Map<? extends String, ? extends Serializable> m)
    {
        Logger.error("trying to modify a read-only ParametrizedSourceMap");
    }

    public Serializable remove(Object key)
    {
        Logger.error("trying to modify a read-only ParametrizedSourceMap");
        return null;
    }

    public int size()
    {
        return source.size() + parameters.size();
    }

    public Collection<Serializable> values()
    {
        ArrayList ret = new ArrayList();
        ret.addAll(source.values());
        ret.addAll(parameters.values());
        return ret;
    }
}
