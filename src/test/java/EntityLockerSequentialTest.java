import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class EntityLockerSequentialTest {

    @Test
    public void testExecuteProtectedCodeSequentially() throws InterruptedException {


        final int ID = 1;
        final int NUMBER_OF_TASKS = 100;
        final CountDownLatch latch = new CountDownLatch(NUMBER_OF_TASKS);
        final EntityLocker<Integer> entityLocker = new EntityLocker<>();
        final Semaphore semaphore = new Semaphore(1);
        final AtomicInteger successCounter = new AtomicInteger();

        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < NUMBER_OF_TASKS; i++) {
            threads.add(new Thread(new TestTask<>(ID, latch, entityLocker, successCounter, semaphore)));
        }

        for (Thread t : threads)
            t.start();

        for (Thread t : threads)
            t.join();

        Assertions.assertEquals(NUMBER_OF_TASKS, successCounter.get());

    }

    private class TestTask<T> implements Runnable {

        final T entityId;
        final CountDownLatch testSynchronizationLatch;
        final Semaphore semaphore;
        final EntityLocker<T> entityLocker;
        final AtomicInteger successCounter;

        public TestTask(T entityId, CountDownLatch testSynchronizationLatch, EntityLocker<T> entityLocker, AtomicInteger successCounter, Semaphore semaphore) {
            this.entityId = entityId;
            this.entityLocker = entityLocker;
            this.testSynchronizationLatch = testSynchronizationLatch;
            this.successCounter = successCounter;
            this.semaphore = semaphore;

        }

        @Override
        public void run() {

            try {
                testSynchronizationLatch.countDown();
                System.out.println("Executing entityLocker for entity " + entityId);
                entityLocker.lockAndExecute(entityId, new ProtectedCodeTaskForSequentialExecution<>(entityId, semaphore, successCounter));
                System.out.println("Finished execution of entityLocker for entity " + entityId);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    public class ProtectedCodeTaskForSequentialExecution<T> implements Runnable {

        private T entityId;
        private Semaphore semaphore;
        private AtomicInteger successCounter;

        public ProtectedCodeTaskForSequentialExecution(T entityId, Semaphore semaphore, AtomicInteger successCounter) {
            this.entityId = entityId;
            this.semaphore = semaphore;
            this.successCounter = successCounter;
        }

        @Override
        public void run() {
            System.out.println("Started working on entity " + entityId);

            if (semaphore.tryAcquire()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    semaphore.release();
                }

                semaphore.release();
                successCounter.incrementAndGet();
                System.out.println("Finished working on entity " + entityId);
            }

            System.out.println("Could not get access to entity");


        }
    }


}
