import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EntityLockerTimeoutTest {


    @Test
    public void testProtectedCodeRunsImmediatelyWhenEntityUnlocked() throws InterruptedException {

        final int entityId = 1;
        final long timeout = 200;

        final EntityLocker<Integer> entityLocker = new EntityLocker<>();


        long startTime = System.currentTimeMillis();
        boolean success = entityLocker.tryLockAndExecute(entityId, new ProtectedCodeTaskForTimingOut<>(entityId, timeout), timeout);
        long endTime = System.currentTimeMillis();

        Assertions.assertTrue(endTime - startTime < timeout + 10);
        Assertions.assertTrue(success);

    }

    @Test
    public void testProtectedCodeTimesOutWhenEntityIsLocked() throws InterruptedException {

        final int entityId = 1;
        final long timeout1 = 20000;
        final long timeout2 = 100;

        final EntityLocker<Integer> entityLocker = new EntityLocker<>();


        Thread blockingThread = new Thread(() -> {
            try {
                System.out.println("Executing entityLocker for entity " + entityId);
                entityLocker.lockAndExecute(entityId, new ProtectedCodeTaskForTimingOut<>(entityId, timeout1));
                System.out.println("Finished execution of entityLocker for entity " + entityId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });


        blockingThread.start();

        Thread.sleep(100);

        long startTime = System.currentTimeMillis();
        boolean success = entityLocker.tryLockAndExecute(entityId, new ProtectedCodeTaskForTimingOut<>(entityId, timeout2), timeout2);
        long endTime = System.currentTimeMillis();

        Assertions.assertFalse(success);
        System.out.println("Elapsed time = " + (endTime - startTime));
        Assertions.assertTrue(endTime - startTime < timeout2 + 10);

    }

    private static class ProtectedCodeTaskForTimingOut<T> implements Runnable {
        private final T entityId;
        private final long timeoutInMilliseconds;

        public ProtectedCodeTaskForTimingOut(T entityId, long timeoutInMilliseconds) {
            this.entityId = entityId;
            this.timeoutInMilliseconds = timeoutInMilliseconds;
        }

        @Override
        public void run() {
            System.out.println("Started working on entity " + entityId);

            try {
                Thread.sleep(timeoutInMilliseconds);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
