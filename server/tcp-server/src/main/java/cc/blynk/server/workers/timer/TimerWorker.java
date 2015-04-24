package cc.blynk.server.workers.timer;

import cc.blynk.common.model.messages.protocol.HardwareMessage;
import cc.blynk.server.dao.SessionsHolder;
import cc.blynk.server.dao.UserRegistry;
import cc.blynk.server.model.auth.Session;
import cc.blynk.server.model.auth.User;
import cc.blynk.server.model.widgets.others.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.jws.soap.SOAPBinding;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.Executors.newFixedThreadPool;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/6/2015.
 *
 * Simplest possible timer implementation.
 *
 * //todo optimize!!! Could handle only ~10k timers per second.
 */
public class TimerWorker implements Runnable {

    private static final Logger log = LogManager.getLogger(TimerWorker.class);
    public static final ExecutorService MESSAGE_EXECUTOR = newFixedThreadPool(getRuntime().availableProcessors());

    private final UserRegistry userRegistry;
    private final SessionsHolder sessionsHolder;
    private final ZoneId UTC = ZoneId.of("UTC");

    private final LongAdder tickedTimers = new LongAdder();
    private final LongAdder onlineTimers = new LongAdder();

    public TimerWorker(UserRegistry userRegistry, SessionsHolder sessionsHolder) {
        this.userRegistry = userRegistry;
        this.sessionsHolder = sessionsHolder;
    }

    @Override
    public void run() {
        log.trace("Starting timer...");
        final LongAdder allTimers = new LongAdder();

        LocalDateTime localDateTime = LocalDateTime.now(UTC);

        long curTime = localDateTime.getSecond() + localDateTime.getMinute() * 60 + localDateTime.getHour() * 3600;

        userRegistry.getUsers().values().parallelStream()
            .filter(user -> user.getProfile().getDashBoards() == null)
            .forEach(user -> user.getProfile().getActiveDashboardTimerWidgets().stream()
                .forEach(timer -> sendMessagesAsync(allTimers, curTime, user, timer))
            );

        //logging only events when timers ticked.
        if (onlineTimers.sumThenReset() > 0) {
            log.info("Timer finished. Processed {}/{}/{} timers.", onlineTimers, tickedTimers.sumThenReset(), allTimers.sum());
        }
    }

    private void sendMessagesAsync(LongAdder allTimers, long curTime, User user, Timer timer) {
        allTimers.increment();
        runAsync(() -> {
            sendMessageIfTicked(user, curTime, timer.startTime, timer.startValue);
            sendMessageIfTicked(user, curTime, timer.stopTime, timer.stopValue);
        }, MESSAGE_EXECUTOR);
    }

    private void sendMessageIfTicked(User user, long curTime, Long time, String value) {
        if (timerTick(curTime, time)) {
            tickedTimers.increment();
            Session session = sessionsHolder.getUserSession().get(user);
            if (session != null) {
                onlineTimers.increment();
                if (session.hardwareChannels.size() > 0) {
                    session.sendMessageToHardware(new HardwareMessage(7777, value));
                }
            }
        }
    }

    protected boolean timerTick(long curTime, Long timerStart) {
        if (timerStart == null) {
            log.error("Timer start field is empty. Shouldn't happen. REPORT!");
            return false;
        }

        return curTime == timerStart;
    }

}
