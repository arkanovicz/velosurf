package velosurf.util;

import java.io.Serializable;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.Map;

public class ConcurrentSlotTreeMap extends ConcurrentSkipListMap<String,Serializable> implements SlotMap
{
  public ConcurrentSlotTreeMap() {}

  public ConcurrentSlotTreeMap(java.util.Comparator<java.lang.String> comparator) { super(comparator); }

  public ConcurrentSlotTreeMap(Map<? extends String,? extends Serializable> m) { super(m); }
}
