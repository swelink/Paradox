package cc.swelink.events;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class EventBus {
    private static final Comparator<Listener> ORDER = Comparator.comparingInt(Listener::priority)
            .thenComparingLong(Listener::sequence);
    private static final Listener[] NONE = new Listener[0];
    private static final Map<Class<?>, List<Handler>> HANDLERS = new ConcurrentHashMap<>();
    private final Map<Class<?>, Listener[]> listeners = new ConcurrentHashMap<>();
    private final Map<Object, List<Listener>> subscribers = new IdentityHashMap<>();
    private long sequence;

    public synchronized void subscribe(Object subscriber) {
        if (subscribers.containsKey(subscriber)) return;
        var created = new ArrayList<Listener>();
        for (var handler : handlers(subscriber.getClass())) {
            created.add(new Listener(handler.event(), handler.priority(), sequence++, subscriber, handler.invoker()));
        }
        subscribers.put(subscriber, created);
        for (var listener : created) {
            var current = listeners.getOrDefault(listener.event(), NONE);
            var updated = Arrays.copyOf(current, current.length + 1);
            updated[current.length] = listener;
            Arrays.sort(updated, ORDER);
            listeners.put(listener.event(), updated);
        }
    }

    public synchronized void unsubscribe(Object subscriber) {
        var removed = subscribers.remove(subscriber);
        if (removed == null) return;
        for (var listener : removed) {
            listener.removed = true;
            var current = listeners.get(listener.event());
            if (current == null) continue;
            var updated = Arrays.stream(current).filter(existing -> existing != listener).toArray(Listener[]::new);
            if (updated.length == 0) listeners.remove(listener.event());
            else listeners.put(listener.event(), updated);
        }
    }

    public <E> E post(E event) {
        var targets = listeners.get(event.getClass());
        if (targets == null) return event;
        var cancellable = event instanceof CancellableEvent value ? value : null;
        for (var listener : targets) {
            if (listener.removed) continue;
            listener.invoker().accept(listener.subscriber(), event);
            if (cancellable != null && cancellable.cancelled()) break;
        }
        return event;
    }

    private static List<Handler> handlers(Class<?> type) {
        return HANDLERS.computeIfAbsent(type, EventBus::scan);
    }

    private static List<Handler> scan(Class<?> type) {
        var found = new ArrayList<Handler>();
        for (var current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (var method : current.getDeclaredMethods()) {
                var annotation = method.getAnnotation(EventHandler.class);
                if (annotation == null) continue;
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1
                        || method.getReturnType() != void.class) {
                    throw new IllegalStateException("@EventHandler must be a non-static void method with one parameter: " + method);
                }
                found.add(new Handler(method.getParameterTypes()[0], annotation.priority(), invoker(method)));
            }
        }
        return List.copyOf(found);
    }

    @SuppressWarnings("unchecked")
    private static BiConsumer<Object, Object> invoker(Method method) {
        try {
            var owner = method.getDeclaringClass();
            var lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
            MethodHandle target = lookup.unreflect(method);
            try {
                var site = LambdaMetafactory.metafactory(lookup, "accept", MethodType.methodType(BiConsumer.class),
                        MethodType.methodType(void.class, Object.class, Object.class), target,
                        MethodType.methodType(void.class, owner, method.getParameterTypes()[0]));
                return (BiConsumer<Object, Object>) site.getTarget().invoke();
            } catch (Throwable ignored) {
                var generic = target.asType(MethodType.methodType(void.class, Object.class, Object.class));
                return (subscriber, event) -> {
                    try {
                        generic.invokeExact(subscriber, event);
                    } catch (RuntimeException | Error error) {
                        throw error;
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                };
            }
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access @EventHandler " + method, exception);
        }
    }

    private record Handler(Class<?> event, int priority, BiConsumer<Object, Object> invoker) {
    }

    private static final class Listener {
        private final Class<?> event;
        private final int priority;
        private final long sequence;
        private final Object subscriber;
        private final BiConsumer<Object, Object> invoker;
        private volatile boolean removed;

        private Listener(Class<?> event, int priority, long sequence, Object subscriber, BiConsumer<Object, Object> invoker) {
            this.event = event;
            this.priority = priority;
            this.sequence = sequence;
            this.subscriber = subscriber;
            this.invoker = invoker;
        }

        Class<?> event() {
            return event;
        }

        int priority() {
            return priority;
        }

        long sequence() {
            return sequence;
        }

        Object subscriber() {
            return subscriber;
        }

        BiConsumer<Object, Object> invoker() {
            return invoker;
        }
    }
}
