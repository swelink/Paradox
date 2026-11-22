package cc.swelink.utils.network;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class TimedPacketQueue<T> {
    private final ArrayDeque<Entry<T>> entries = new ArrayDeque<>();
    private final int capacity;

    public TimedPacketQueue(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
    }

    public boolean offer(T value, long now) {
        Objects.requireNonNull(value);
        if (entries.size() >= capacity) return false;
        entries.addLast(new Entry<>(value, now));
        return true;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean any(Predicate<T> predicate) {
        for (var entry : entries) {
            if (predicate.test(entry.value)) return true;
        }
        return false;
    }

    public long age(long now) {
        return entries.isEmpty() ? 0 : now - entries.getFirst().timestamp;
    }

    public void release(long now, long delay, Consumer<T> consumer) {
        while (!entries.isEmpty() && now - entries.getFirst().timestamp >= delay) {
            consumer.accept(entries.removeFirst().value);
        }
    }

    public void release(long now, long delay, Predicate<T> early, Consumer<T> consumer) {
        while (!entries.isEmpty() && (now - entries.getFirst().timestamp >= delay || early.test(entries.getFirst().value))) {
            consumer.accept(entries.removeFirst().value);
        }
    }

    public void drain(Consumer<T> consumer) {
        while (!entries.isEmpty()) consumer.accept(entries.removeFirst().value);
    }

    public void clear() {
        entries.clear();
    }

    private record Entry<T>(T value, long timestamp) {
    }
}
