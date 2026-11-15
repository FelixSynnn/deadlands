package com.sonicether.soundphysics.profiling;

import com.sonicether.soundphysics.Loggers;
import java.lang.ref.WeakReference;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskProfiler {
    private static final int TASK_RING_BUFFER_SIZE = 100;
    private static final int TASK_RING_TALLY_SIZE = 100;
    private final String identifier;
    private final Deque<Double> durations;
    private final AtomicInteger tally;

    public TaskProfiler(String identifier) {
        this.identifier = identifier;
        this.durations = new ConcurrentLinkedDeque<>();
        this.tally = new AtomicInteger(0);
    }

    public TaskProfiler.TaskProfilerHandle profile() {
        return new TaskProfiler.TaskProfilerHandle();
    }

    public void addDuration(double duration) {
        if (this.durations.size() == 100) {
            this.durations.poll();
        }

        this.durations.offer(duration);
        this.tally.incrementAndGet();
    }

    public double getTotalDuration() {
        return this.durations.stream().mapToDouble(Double::doubleValue).sum();
    }

    public double getAverageDuration() {
        return this.durations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public double getMinDuration() {
        return this.durations.stream().min(Double::compareTo).orElse(Double.MAX_VALUE);
    }

    public double getMaxDuration() {
        return this.durations.stream().max(Double::compareTo).orElse(Double.MIN_VALUE);
    }

    public void logResults() {
        Loggers.logProfiling(
            "Profile for task '{}', total: {} ms, average: {} ms, min: {} ms, max: {} ms",
            this.identifier,
            this.getTotalDuration(),
            this.getAverageDuration(),
            this.getMinDuration(),
            this.getMaxDuration()
        );
    }

    public void onTally(Runnable callback) {
        if (this.tally.get() >= 100) {
            callback.run();
            this.tally.set(0);
        }
    }

    public class TaskProfilerHandle {
        private final long startTime = System.nanoTime();
        private double duration;
        private WeakReference<TaskProfiler> owner = new WeakReference<>(TaskProfiler.this);

        private TaskProfilerHandle() {
        }

        public void finish() {
            TaskProfiler aggregator = this.owner.get();
            if (aggregator != null) {
                long endTime = System.nanoTime();
                this.duration = (endTime - this.startTime) / 1000000.0;
                aggregator.addDuration(this.duration);
            }
        }

        public double getDuration() {
            return this.duration;
        }
    }
}
