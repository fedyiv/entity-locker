import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class EntityLockerConcurrentTest {

    @Test
    public void testExecutingProtectedCodeForDifferentIdsConcurrently() throws InterruptedException {


        final int NUMBER_OF_TASKS = 100;
        final CountDownLatch latch1 = new CountDownLatch(NUMBER_OF_TASKS);
        final CountDownLatch latch2 = new CountDownLatch(NUMBER_OF_TASKS);
        final EntityLocker<Integer> entityLocker = new EntityLocker<>();
        final AtomicInteger successCounter = new AtomicInteger();

        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < NUMBER_OF_TASKS; i++) {
            threads.add(new Thread(new TestTask<>(i, latch1, latch2, entityLocker, successCounter)));
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
        final CountDownLatch externalLatch;
        final EntityLocker<T> entityLocker;
        final AtomicInteger successCounter;

        public TestTask(T entityId, CountDownLatch testSynchronizationLatch, CountDownLatch externalLatch, EntityLocker<T> entityLocker, AtomicInteger successCounter) {
            this.entityId = entityId;
            this.entityLocker = entityLocker;
            this.testSynchronizationLatch = testSynchronizationLatch;
            this.successCounter = successCounter;
            this.externalLatch = externalLatch;

        }

        @Override
        public void run() {

            try {
                testSynchronizationLatch.countDown();
                System.out.println("Executing entityLocker for entity " + entityId);
                entityLocker.lockAndExecute(entityId, new ProtectedCodeTaskForConcurrentExecution<>(entityId, externalLatch, successCounter));
                System.out.println("Finished execution of entityLocker for entity " + entityId);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    public class ProtectedCodeTaskForConcurrentExecution<T> implements Runnable {

        private T entityId;
        private CountDownLatch latch;
        private AtomicInteger successCounter;

        public ProtectedCodeTaskForConcurrentExecution(T entityId, CountDownLatch latch, AtomicInteger successCounter) {
            this.entityId = entityId;
            this.latch = latch;
            this.successCounter = successCounter;
        }

        @Override
        public void run() {

            latch.countDown();
            try {
                if (latch.await(10, TimeUnit.SECONDS)) {

                    System.out.println("Started working on entity " + entityId);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    successCounter.incrementAndGet();
                    System.out.println("Finished working on entity " + entityId);
                } else {
                    System.out.println("Protected code was not run in parallel");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


        }
    }


}
