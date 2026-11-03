package evolution.ui;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationWorkerTest {
    @Test void keepsCalculationOffCallerRejectsBacklogAndDeliversThroughCompletionExecutor() throws Exception {
        var completions=new LinkedBlockingQueue<Runnable>();
        var worker=new SimulationWorker(completions::add);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var cleanup=new CountDownLatch(1);
        Thread caller=Thread.currentThread();var called=new AtomicBoolean();
        try {
            assertTrue(worker.submit(()->{
                assertNotSame(caller,Thread.currentThread());entered.countDown();
                if(!release.await(3,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");return 42;
            },(result,error)->{assertSame(caller,Thread.currentThread());assertNull(error);assertEquals(42,result);called.set(true);}));
            assertTrue(entered.await(3,TimeUnit.SECONDS));assertTrue(worker.busy());
            assertFalse(worker.submit(()->0,(r,e)->fail("Queued an unwanted command")));
            release.countDown();var callback=completions.poll(3,TimeUnit.SECONDS);assertNotNull(callback);
            assertFalse(called.get());callback.run();assertTrue(called.get());assertFalse(worker.busy());
            assertTrue(worker.submit(()->{throw new IllegalArgumentException("bad");},(r,e)->assertEquals("bad",e.getMessage())));
            callback=completions.poll(3,TimeUnit.SECONDS);assertNotNull(callback);callback.run();
        } finally {release.countDown();worker.closeAfter(cleanup::countDown);}
        assertTrue(cleanup.await(3,TimeUnit.SECONDS));assertFalse(worker.submit(()->0,(r,e)->{}));
    }
}
