import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class EntityLockerReentranceTest {

    @Test
    public void testReentrancy() throws InterruptedException {

        final int entityId = 1;
        final EntityLocker<Integer> entityLocker = new EntityLocker<>();
        final AtomicInteger successCounter = new AtomicInteger();

        Thread t = new Thread(() -> {
            try {
                System.out.println("Executing entityLocker for entity " + entityId);
                entityLocker.lockAndExecute(entityId, new ReentrantTask<>(entityId, entityLocker, successCounter));
                System.out.println("Finished execution of entityLocker for entity " + entityId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t.start();
        t.join();

        Assertions.assertEquals(2, successCounter.get());

    }


    private static class ReentrantTask<T> implements Runnable {

        private final  T entityId;
        private final  AtomicInteger successCounter;
        private final  EntityLocker<T> entityLocker;

        public ReentrantTask(T entityId, EntityLocker<T> entityLocker, AtomicInteger successCounter) {
            this.entityId = entityId;
            this.successCounter = successCounter;
            this.entityLocker = entityLocker;
        }

        @Override
        public void run() {
            System.out.println("Started working on entity " + entityId);


            try {
                entityLocker.tryLockAndExecute(entityId, successCounter::incrementAndGet, 200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            successCounter.incrementAndGet();
            System.out.println("Finished working on entity " + entityId);


        }
    }


}
