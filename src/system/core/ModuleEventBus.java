package system.core;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** In-process event bus. A failing listener does not prevent other listeners running. */
public final class ModuleEventBus {
    private static final Logger LOGGER = Logger.getLogger(ModuleEventBus.class.getName());
    private final CopyOnWriteArrayList<Consumer<ModuleEvent>> listeners =
            new CopyOnWriteArrayList<Consumer<ModuleEvent>>();

    public AutoCloseable subscribe(Consumer<ModuleEvent> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.add(listener);
        return new AutoCloseable() {
            @Override
            public void close() {
                listeners.remove(listener);
            }
        };
    }

    void publish(ModuleEvent event) {
        for (Consumer<ModuleEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Module event listener failed", exception);
            }
        }
    }
}
