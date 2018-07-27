package velosurf.context;

import java.util.Set;

public interface EntityListener
{
  public void inserted(Instance instance);
  public void deleted(Instance instance);
  public void updated(Instance instance, Set<String> columns);
}
