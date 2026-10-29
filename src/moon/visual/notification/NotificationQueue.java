package moon.visual.notification;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** gf: initialize before publishing; expose the original live COW list. */
public final class NotificationQueue {
    private final List<Notification> entries=new CopyOnWriteArrayList<>();
    public void add(Notification notification){notification.initialize();entries.add(notification);}
    public void remove(Notification notification){entries.remove(notification);}
    public List<Notification> entries(){return entries;}
}
