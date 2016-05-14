package cc.blynk.server.core.batch;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link EventLoop} scoped holder for {@link FlushWritesTask}.
 * <br/>
 * This is thread-local facility to keep track writes produced by reads. Logic behind this class is bound to a fact
 * that after every {@code epollEventLoop.processReady()} (or {@code nioEventLoop.processSelectedKeys()}) always goes a call
 * to {@link SingleThreadEventExecutor#runAllTasks()} (or {@link SingleThreadEventExecutor#runAllTasks(long)}), that's why
 * writes caught between first channel read and subsequent call to {@code runAllTasks}
 * can be batched (<b>iff</b> writes occur within <b>same</b> EventLoop).
 * <p>
 * The Blynk Project.
 * Created by Artem Vysochyn.
 * Created on 20.04.16.
 */
public final class FlushWritesTaskHolder extends FastThreadLocal<FlushWritesTaskHolder.FlushWritesTask> {
    private static final Logger log = LogManager.getLogger(FlushWritesTaskHolder.class);

    static final Object FLUSH_EVENT = new Object();

    @Override
    protected FlushWritesTask initialValue() {
        return FlushWritesTask.INSTANCE;
    }

    void setOnChannelRead(EventLoop eventLoop) {
        SingleThreadEventExecutor eventLoop1 = (SingleThreadEventExecutor) eventLoop;
        if (get() == FlushWritesTask.INSTANCE) {
            FlushWritesTask task = new FlushWritesTask();
            set(task);
            //noinspection unchecked
            eventLoop1.submit(task).addListener((GenericFutureListener) future -> remove());
        }
    }

    boolean addDirtyChannel(Channel channel) {
        boolean taskWasSet = get() != FlushWritesTask.INSTANCE;
        if (taskWasSet) {
            AtomicInteger ai = get().dirtyChannels.get(channel);
            if (ai == null) {
                get().dirtyChannels.put(channel, new AtomicInteger(1));
            } else {
                ai.incrementAndGet();
            }
        }
        return taskWasSet;
    }

    public static class FlushWritesTask implements Runnable {
        private static final FlushWritesTask INSTANCE = new FlushWritesTask();

        private final Map<Channel, AtomicInteger> dirtyChannels = new HashMap<>();

        @Override
        public void run() {
            for (Map.Entry<Channel, AtomicInteger> entry : dirtyChannels.entrySet()) {
                log.debug("FlushWritesTask on channel: {}, writes: {}", entry.getKey(), entry.getValue());
                try {
                    entry.getKey().pipeline().fireUserEventTriggered(FLUSH_EVENT);
                } catch (Throwable e) {
                    log.warn("FlushWritesTask caught exception: {} on channel: ", e.getMessage(), entry.getKey());
                }
            }
        }
    }
}
