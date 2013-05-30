package org.mpilone.vaadin;

import java.util.ArrayList;
import java.util.List;

/**
 * A helper class for implementing observables in Java. Listeners can be added
 * and removed and events can be fired to all listeners.
 * 
 * @author mpilone
 * 
 * @param <L>
 *          the listener type
 * @param <E>
 *          the event type
 */
abstract class AbstractListenable<L, E> {

  private List<L> listeners = new ArrayList<L>();

  public void addListener(L listener) {
    listeners.add(listener);
  }

  public void removeListener(L listener) {
    listeners.remove(listener);
  }

  /**
   * Dispatches the given event to all listeners.
   * 
   * @param event
   *          the event to dispatch
   */
  public void fireEvent(E event) {
    // Clone the original list to avoid concurrent modification exceptions
    List<L> listenersClone = new ArrayList<L>(listeners);
    for (L listener : listenersClone) {
      fireEvent(listener, event);
    }
  }

  /**
   * Subclasses must implement this method to call the appropriate event
   * handling method on the listener for the given event.
   * 
   * @param listener
   *          the listener to notify
   * @param event
   *          the event to dispatch
   */
  protected abstract void fireEvent(L listener, E event);

}
