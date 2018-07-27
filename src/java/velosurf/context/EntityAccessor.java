package velosurf.context;

// let external packages access proteected 'entity' member

import velosurf.model.Entity;

public class EntityAccessor
{
  public static Entity getInstanceEntity(Instance instance)
  {
    return instance.entity;
  }
}
