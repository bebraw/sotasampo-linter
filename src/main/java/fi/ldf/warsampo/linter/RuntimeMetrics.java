package fi.ldf.warsampo.linter;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;

final class RuntimeMetrics {
    private RuntimeMetrics() {}

    static void resetPeakHeap() {
        ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP)
                .forEach(pool -> {
                    try {
                        pool.resetPeakUsage();
                    } catch (UnsupportedOperationException ignored) {
                        // The current JVM may expose a read-only peak counter.
                    }
                });
    }

    static long peakHeapBytes() {
        return ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP)
                .mapToLong(pool -> pool.getPeakUsage().getUsed())
                .sum();
    }
}
