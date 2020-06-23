import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


public class EntityLocker<T> {

    final Map<T, ReentrantLock> locks = new ConcurrentHashMap<>();
    DeadlockDetector<T> deadlockDetector = new DeadlockDetector<>();


    public boolean tryLockAndExecute(T entityId, Runnable protectedCode, long timeoutInMilliseconds) throws InterruptedException, DeadlockPreventedException {

        locks.putIfAbsent(entityId, new ReentrantLock(true));
        ReentrantLock lock = locks.get(entityId);

        if (deadlockDetector.isDeadlockSafe(entityId)) {
            if (timeoutInMilliseconds != 0) {
                if (!lock.tryLock(timeoutInMilliseconds, TimeUnit.MILLISECONDS)) {
                    deadlockDetector.afterUnsuccessfulAcquire(entityId);
                    return false;
                }
            } else {
                lock.lock();
            }
        } else {
            throw new DeadlockPreventedException();
        }

        deadlockDetector.afterAcquiringLock(entityId);

        try {
            protectedCode.run();
        } finally {
            lock.unlock();
            deadlockDetector.afterReleasingLock(entityId);
        }

        return true;
    }

    public void lockAndExecute(T entityId, Runnable protectedCode) throws InterruptedException, DeadlockPreventedException {
        tryLockAndExecute(entityId, protectedCode, 0);
    }

    public static class DeadlockPreventedException extends RuntimeException {
    }

}
