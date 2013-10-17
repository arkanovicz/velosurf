package velosurf.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class SlotHashMap extends HashMap<String,Serializable> implements SlotMap
{
  public SlotHashMap() {}

  public SlotHashMap(Map<? extends String,? extends Serializable> m) { super(m); }
}
