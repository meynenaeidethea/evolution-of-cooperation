package evolution.ui;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/** One owner thread, at most one in-flight command. Completion always uses the supplied UI executor. */
final class SimulationWorker implements AutoCloseable {
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{
        var t=new Thread(r,"simulation-worker");t.setDaemon(false);return t;
    });
    private final Executor completion;
    private final AtomicBoolean busy=new AtomicBoolean();
    private volatile boolean closed;
    SimulationWorker(Executor completion) {this.completion=completion;}
    boolean busy() {return busy.get();}
    <T> boolean submit(Callable<T> work,BiConsumer<T,Throwable> done) {
        if(closed || !busy.compareAndSet(false,true)) return false;
        executor.execute(()->{
            T value=null;Throwable error=null;
            try {value=work.call();} catch(Exception e) {error=e;}
            T result=value;Throwable failure=error;
            completion.execute(()->{busy.set(false);if(!closed) done.accept(result,failure);});
        });return true;
    }
    /** Cleanup is queued after the current command; closing never waits on the UI thread. */
    void closeAfter(Runnable cleanup) {
        closed=true;executor.execute(cleanup);executor.shutdown();
    }
    @Override public void close() {closeAfter(()->{});}
}
