import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * This class manages states of entities and threads and detecting deadlocks before they occured.
 * It works properly only if caller executes corresponding methods before/after acquiring/releasing locks.
 * */
public class DeadlockDetector<T> {
    private final ConcurrentHashMap<T, Long> lockOwningThreadsMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, T> lockWaitingThreadsMap = new ConcurrentHashMap<>();


    @SuppressWarnings("unused") // I am planning to use this method in future
    public synchronized void beforeTryingToAcquireLock(T entityId) {
        long threadId = Thread.currentThread().getId();
        lockWaitingThreadsMap.put(threadId, entityId);
    }

    public synchronized void afterUnsuccessfulAcquire(T entityId) {
        long threadId = Thread.currentThread().getId();
        lockWaitingThreadsMap.remove(threadId);
        System.out.println("afterUnsuccessfulAcquire: threadId=" + threadId + ", entityId=" + entityId);
    }

    public synchronized void afterAcquiringLock(T entityId) {
        long threadId = Thread.currentThread().getId();
        lockOwningThreadsMap.put(entityId, threadId);
        lockWaitingThreadsMap.remove(threadId);
        System.out.println("afterAcquiringLock: threadId=" + threadId + ", entityId=" + entityId);
    }

    public synchronized void afterReleasingLock(T entityId) {
        long threadId = Thread.currentThread().getId();
        lockOwningThreadsMap.remove(entityId, threadId);
        System.out.println("afterReleasingLock: threadId=" + threadId + ", entityId=" + entityId);
    }

    public synchronized boolean isDeadlockSafe(T currentEntityId) {

        long currentThreadId = Thread.currentThread().getId();

        System.out.println("isDeadlockSafe:begin: threadId=" + currentThreadId + ", entityId=" + currentEntityId);

        printLockOwnersMap();
        printEntitiesWaitedByThreads();

        if (lockOwningThreadsMap.get(currentEntityId) == null || lockOwningThreadsMap.get(currentEntityId) == currentThreadId ) {
            lockWaitingThreadsMap.put(currentThreadId,currentEntityId);
            System.out.println("isDeadlockSafe:Safe: threadId=" + currentThreadId + ", entityId=" + currentEntityId);
            return true;
        }

        T entityId = currentEntityId;

        while (entityId != null) {
            System.out.println("isDeadlockSafe: Begin Loop:entityId=" + entityId + ", currentThreadId=" + currentThreadId);

            if(lockOwningThreadsMap.get(entityId) == null) {
                /*Thread already present in lockWaitingThreadsMap but not in lockOwningThreadsMap.
                * It can happen only if the waiting thread wil become locker thread, i.e. that thread is "waiting for itself"
                */
                lockWaitingThreadsMap.put(currentThreadId,currentEntityId);
                System.out.println("isDeadlockSafe:Safe: threadId=" + currentThreadId + ", entityId=" + currentEntityId);
                return true;
            }

            long holderThreadId = lockOwningThreadsMap.get(entityId);

            if (currentThreadId==holderThreadId) {
                return false; //Loop detected
            }

            entityId = lockWaitingThreadsMap.get(holderThreadId);

            System.out.println("isDeadlockSafe: End Loop: holderThreadId=" + holderThreadId + ", entityId=" + entityId);

        }
        lockWaitingThreadsMap.put(currentThreadId,currentEntityId);
        System.out.println("isDeadlockSafe: After acquiring lock : currentThreadId=" +  currentThreadId + ", entityId=" + currentEntityId);
        return true;
    }

    private void printEntitiesWaitedByThreads() {

        System.out.println("-----------entitiesWaitedByThreadContent start----------------:");

        for(Map.Entry<Long, T> entry:lockWaitingThreadsMap.entrySet()) {
            System.out.println("Thread = " + entry.getKey() + ", waiting for entity = " + entry.getValue());
        }

        System.out.println("-----------entitiesWaitedByThreadContent end----------------:");

    }

    private void printLockOwnersMap() {

        System.out.println("-----------lockOwnersMapContent start----------------:");

        for(Map.Entry<T, Long> entry:lockOwningThreadsMap.entrySet()) {
            System.out.println("Entity = " + entry.getKey() + ", is locked by Thread = " + entry.getValue());
        }

        System.out.println("-----------lockOwnersMapContent end----------------:");

    }

}
