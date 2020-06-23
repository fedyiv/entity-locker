import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class EntityLockerDeadlockPreventionTest {

    @Test
    public void testDeadlockPreventionWithTwoThreadsAndTwoEntities() {


        Integer entityId1 = 1;
        Integer entityId2 = 2;
        long singleThreadWorkTimeMs = 1000;

        final EntityLocker<Integer> entityLocker = new EntityLocker<>();
        final AtomicBoolean deadlockPrevented = new AtomicBoolean();

        for (int i = 0; i < 10; i++) {

            final CountDownLatch latch = new CountDownLatch(2);


            Thread t1 = new Thread(new TestTask<>(entityId1, entityId2, latch, entityLocker, deadlockPrevented, singleThreadWorkTimeMs));
            Thread t2 = new Thread(new TestTask<>(entityId2, entityId1, latch, entityLocker, deadlockPrevented, singleThreadWorkTimeMs));

            t1.setName("1");
            t2.setName("2");

            t1.start();
            t2.start();


            Assertions.assertTimeoutPreemptively(Duration.ofSeconds((singleThreadWorkTimeMs / 1000 * 2) + 1), () -> {
                t1.join();
                t2.join();
            });
        }

        //If this condition fails it might mean that deadlock condition simply did not occur
        Assertions.assertTrue(deadlockPrevented.get());

    }

    @Test
    public void testDeadlockPreventionWithThreeThreadsAndThreeEntities() {


        Integer entityId1 = 1;
        Integer entityId2 = 2;
        Integer entityId3 = 3;
        long singleThreadWorkTimeMs = 1000;

        final EntityLocker<Integer> entityLocker = new EntityLocker<>();
        final AtomicBoolean deadlockPrevented = new AtomicBoolean();

        for (int i = 0; i < 10; i++) {

            final CountDownLatch latch = new CountDownLatch(2);


            Thread t1 = new Thread(new TestTask<>(entityId1, entityId2, latch, entityLocker, deadlockPrevented, singleThreadWorkTimeMs));
            Thread t2 = new Thread(new TestTask<>(entityId2, entityId3, latch, entityLocker, deadlockPrevented, singleThreadWorkTimeMs));
            Thread t3 = new Thread(new TestTask<>(entityId3, entityId1, latch, entityLocker, deadlockPrevented, singleThreadWorkTimeMs));

            t1.setName("1");
            t2.setName("2");
            t3.setName("4");

            t1.start();
            t2.start();
            t3.start();


            Assertions.assertTimeoutPreemptively(Duration.ofSeconds((singleThreadWorkTimeMs / 1000 * 3) + 1), () -> {
                t1.join();
                t2.join();
                t3.join();
            });
        }

        //If this condition fails it might mean that deadlock condition simply did not occur
        Assertions.assertTrue(deadlockPrevented.get());

    }

    @Test
    public void testDeadlockPreventionWithMultipleThreadsAndTwoEntities() {


        Integer entityId1 = 1;
        Integer entityId2 = 2;
        final long singleThreadWorkTimeMs = 1000;
        final int numberOfThreads = 20;

        final EntityLocker<Integer> entityLocker = new EntityLocker<>();
        final AtomicBoolean deadlockPrevented = new AtomicBoolean();


        final CountDownLatch latch = new CountDownLatch(numberOfThreads);

        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < numberOfThreads / 2; i++) {

            Thread t1 = new Thread(new TestTask<>(entityId1, entityId2, latch, entityLocker, deadlockPrevented, singleThreadWorkTimeMs));
            Thread t2 = new Thread(new TestTask<>(entityId2, entityId1, latch, entityLocker, deadlockPrevented, singleThreadWorkTimeMs));

            t1.setName("1");
            t2.setName("2");

            threads.add(t1);
            threads.add(t2);

            t1.start();
            t2.start();

        }

        Assertions.assertTimeoutPreemptively(Duration.ofSeconds(((long) 2 * numberOfThreads) + 1), () -> {
            for (Thread t : threads)
                t.join();
        });

        Assertions.assertTrue(deadlockPrevented.get());

    }

    private static class TestTask<T> implements Runnable {

        private final T entityId1;
        private final T entityId2;
        private final long singleThreadWorkTimeMs;
        private final CountDownLatch testSynchronizationLatch;
        private final EntityLocker<T> entityLocker;
        private final AtomicBoolean deadlockPrevented;

        public TestTask(T entityId1, T entityId2, CountDownLatch testSynchronizationLatch, EntityLocker<T> entityLocker, AtomicBoolean deadlockPrevented, long singleThreadWorkTimeMs) {
            this.entityId1 = entityId1;
            this.entityId2 = entityId2;
            this.entityLocker = entityLocker;
            this.testSynchronizationLatch = testSynchronizationLatch;
            this.deadlockPrevented = deadlockPrevented;
            this.singleThreadWorkTimeMs = singleThreadWorkTimeMs;

        }

        @Override
        public void run() {

            try {
                testSynchronizationLatch.countDown();
                System.out.println("Executing entityLocker for entity " + entityId1);
                entityLocker.lockAndExecute(entityId1, new DeadlockProneTask<>(entityId2, entityLocker, deadlockPrevented, singleThreadWorkTimeMs));
                System.out.println("Finished execution of entityLocker for entity " + entityId1);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    private static class DeadlockProneTask<T> implements Runnable {

        private final T entityId;
        private final EntityLocker<T> entityLocker;
        private final AtomicBoolean deadlockPrevented;
        private final long singleThreadWorkTimeMs;

        public DeadlockProneTask(T entityId, EntityLocker<T> entityLocker, AtomicBoolean deadlockPrevented, long singleThreadWorkTimeMs) {
            this.entityId = entityId;
            this.deadlockPrevented = deadlockPrevented;
            this.entityLocker = entityLocker;
            this.singleThreadWorkTimeMs = singleThreadWorkTimeMs;
        }

        @Override
        public void run() {

            try {

                entityLocker.lockAndExecute(entityId, () -> {
                    try {
                        System.out.println("Starting inner task for entityId = " + entityId + "and threadId = " + Thread.currentThread().getId());
                        Thread.sleep(singleThreadWorkTimeMs);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            } catch (EntityLocker.DeadlockPreventedException e) {
                System.out.println("Cancelled execution due to detected deadlock");
                deadlockPrevented.set(true);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }


        }
    }

}
