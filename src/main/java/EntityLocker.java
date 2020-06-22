import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class EntityLocker<T> {

    final Map<T, ReentrantLock> locks = new ConcurrentHashMap<>();
    //TODO: move deadlock related functionality to separate class
    final Map<Long, T> entitiesWaitedByThreads = new ConcurrentHashMap<>();
    final Map<T, Long> lockOwnersMap = new ConcurrentHashMap<>();

    public boolean tryLockAndExecute(T entityId, Runnable protectedCode, long timeoutInMilliseconds) throws InterruptedException, DeadlockPreventedException {

        long currentThreadId = Thread.currentThread().getId();

        locks.putIfAbsent(entityId, new ReentrantLock(true));
        ReentrantLock lock = locks.get(entityId);



        if (acquireInternalLockIfDeadlockSafe(entityId, currentThreadId)) {
            if (timeoutInMilliseconds != 0) {
                if (!lock.tryLock(timeoutInMilliseconds, TimeUnit.MILLISECONDS)) {
                    entitiesWaitedByThreads.remove(currentThreadId);
                    return false;
                }
            } else {
                lock.lock();
            }
        } else {
            entitiesWaitedByThreads.remove(currentThreadId);
            throw new DeadlockPreventedException();
        }

        entitiesWaitedByThreads.remove(currentThreadId);
        lockOwnersMap.put(entityId, currentThreadId);

        try {
            protectedCode.run();
        } finally {
            lock.unlock();
            lockOwnersMap.remove(entityId);
        }

        return true;
    }

    private synchronized boolean acquireInternalLockIfDeadlockSafe(T currentEntityId, Long currentThreadId) {

        ReentrantLock lock = locks.get(currentEntityId);

        if (lock.isHeldByCurrentThread() || !lock.isLocked()) {
            entitiesWaitedByThreads.put(currentThreadId, currentEntityId);
            return true;
        }

        T entityId = currentEntityId;

        while (entityId != null) {

            Long holderThreadId = lockOwnersMap.get(entityId);

            if(holderThreadId == null) // lock was already released
                return true;

            if (currentThreadId.equals(holderThreadId)) {
                return false; //Loop detected
            }

            entityId = entitiesWaitedByThreads.get(holderThreadId);

        }

        entitiesWaitedByThreads.put(currentThreadId, currentEntityId);
        return true;

    }

    public void lockAndExecute(T entityId, Runnable protectedCode) throws InterruptedException, DeadlockPreventedException {
        tryLockAndExecute(entityId, protectedCode, 0);
    }


    public static class DeadlockPreventedException extends RuntimeException {

    }

}
