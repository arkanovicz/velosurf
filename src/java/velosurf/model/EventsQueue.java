package velosurf.model;

import velosurf.context.EntityAccessor;
import velosurf.context.Instance;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EventsQueue implements Runnable
{

  private Queue<Event> queue = null;

  public EventsQueue()
  {
    queue = new ConcurrentLinkedQueue<Event>();
  }

  @Override
  public void run()
  {
    while (true)
    {
      Event event;
      while ((event = queue.poll()) != null)
      {
        Entity entity = EntityAccessor.getInstanceEntity(event.instance);
        entity.dispatchEvent(event);
      }
    }
  }

  public void post(Event event) { queue.add(event); }

  public static enum EventType { INSERT, UPDATE, DELETE }

  public static class Event
  {
      EventType type;
      Instance instance;
      Set<String> fields;
      public Event(EventType type, Instance instance) { this(type, instance, null); }
      public Event(EventType type, Instance instance, Set<String> fields) { this.type = type; this.instance = instance; this.fields = fields; }
  }
}
