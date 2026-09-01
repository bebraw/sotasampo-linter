package fi.ldf.warsampo.linter;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

final class RuntimeMetrics {
    private RuntimeMetrics() {}

    static HeapTracker trackHeap() {
        return new HeapTracker();
    }

    static final class HeapTracker implements AutoCloseable {
        private static final long SAMPLE_INTERVAL_NANOS = 10_000_000L;

        private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicLong peakBytes = new AtomicLong();
        private final Thread sampler;

        private HeapTracker() {
            sample();
            sampler = Thread.ofPlatform().daemon().name("warsampo-linter-heap-sampler").start(() -> {
                while (running.get()) {
                    sample();
                    LockSupport.parkNanos(SAMPLE_INTERVAL_NANOS);
                }
                sample();
            });
        }

        long peakHeapBytes() {
            sample();
            return peakBytes.get();
        }

        private void sample() {
            peakBytes.accumulateAndGet(memory.getHeapMemoryUsage().getUsed(), Math::max);
        }

        @Override
        public void close() {
            if (!running.getAndSet(false)) {
                return;
            }
            sampler.interrupt();
            try {
                sampler.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
