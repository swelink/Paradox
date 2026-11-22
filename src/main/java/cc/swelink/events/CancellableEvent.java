package cc.swelink.events;

public abstract class CancellableEvent {
    private boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public boolean cancelled() {
        return cancelled;
    }
}
