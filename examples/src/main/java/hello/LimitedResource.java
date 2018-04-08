package hello;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class LimitedResource {

    private static final ConcurrentHashMap<Integer, LimitedResource> CACHED_INSTANCES = new ConcurrentHashMap<>();

    private final Semaphore semaphore;

    public LimitedResource(int maxPermits) {
        this.semaphore = new Semaphore(maxPermits);
    }

    public String get(Long maxWaitMillis, long lattency, TimeUnit timeUnit) {
        Objects.requireNonNull(timeUnit);

        try {
            boolean success = semaphore.tryAcquire(maxWaitMillis, TimeUnit.MILLISECONDS);
            if (!success) {
                throw new RuntimeException("resource wait failed");
            }
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        try {
            timeUnit.sleep(lattency);

            return "pong";
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        } finally {
            semaphore.release();
        }
    }

    public static LimitedResource instance(int maxPermits) {
        if (!CACHED_INSTANCES.containsKey(maxPermits)) {
            CACHED_INSTANCES.putIfAbsent(maxPermits, new LimitedResource(maxPermits));
        }

        return CACHED_INSTANCES.get(maxPermits);
    }
}
