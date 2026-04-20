package alexei.sischin.obfuscatewg.core.concurrent;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Logging utilities for uncaught exceptions.
 */
@Slf4j
@UtilityClass
public class UncaughtErrorLogging {

    /**
     * Executor services often silently swallow errors, and this utility adds the ability to log such errors.
     *
     * @param delegate delegate.
     * @return wrapper.
     */
    public static ExecutorService wrap(ExecutorService delegate) {
        return new ExecutorService() {
            @Override
            public void shutdown() {
                delegate.shutdown();
            }

            @Override
            public List<Runnable> shutdownNow() {
                return delegate.shutdownNow();
            }

            @Override
            public boolean isShutdown() {
                return delegate.isShutdown();
            }

            @Override
            public boolean isTerminated() {
                return delegate.isTerminated();
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                return delegate.awaitTermination(timeout, unit);
            }

            @Override
            public <T> Future<T> submit(Callable<T> task) {
                return delegate.submit(wrap(task));
            }

            @Override
            public <T> Future<T> submit(Runnable task, T result) {
                return delegate.submit(wrap(task), result);
            }

            @Override
            public Future<?> submit(Runnable task) {
                return delegate.submit(wrap(task));
            }

            @Override
            public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
                return delegate.invokeAll(tasks.stream().map(UncaughtErrorLogging::wrap).toList());
            }

            @Override
            public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
                return delegate.invokeAll(tasks.stream().map(UncaughtErrorLogging::wrap).toList(), timeout, unit);
            }

            @Override
            public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
                return delegate.invokeAny(tasks.stream().map(UncaughtErrorLogging::wrap).toList());
            }

            @Override
            public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return delegate.invokeAny(tasks.stream().map(UncaughtErrorLogging::wrap).toList(), timeout, unit);
            }

            @Override
            public void execute(Runnable command) {
                delegate.execute(command);
            }
        };
    }

    public static Runnable wrap(Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                logError(t);
                throw t;
            }
        };
    }

    public static <T> Callable<T> wrap(Callable<T> callable) {
        return () -> {
            try {
                return callable.call();
            } catch (Throwable t) {
                logError(t);
                throw new Exception(t);
            }
        };
    }

    private static void logError(Throwable t) {
        try {
            log.error("Uncaught error occurred", t);
        } catch (Throwable t2) {
            System.err.println("Fatal error occurred");
            t.printStackTrace();
            System.err.println("Logging error");
            t2.printStackTrace();
        }
    }
}
