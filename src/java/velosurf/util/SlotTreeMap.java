package velosurf.util;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

public class SlotTreeMap extends TreeMap<String,Serializable> implements SlotMap
{
  public SlotTreeMap() {}

  public SlotTreeMap(java.util.Comparator<java.lang.String> comparator) { super(comparator); }

  public SlotTreeMap(Map<? extends String,? extends Serializable> m) { super(m); }

}
