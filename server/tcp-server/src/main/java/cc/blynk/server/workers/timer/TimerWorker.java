package cc.blynk.server.workers.timer;

import cc.blynk.common.model.messages.protocol.HardwareMessage;
import cc.blynk.server.dao.SessionsHolder;
import cc.blynk.server.dao.UserRegistry;
import cc.blynk.server.model.auth.Session;
import cc.blynk.server.model.auth.User;
import cc.blynk.server.model.widgets.others.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalTime;
import java.time.ZoneId;

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

    private final UserRegistry userRegistry;
    private final SessionsHolder sessionsHolder;
    private final ZoneId UTC = ZoneId.of("UTC");

    private int tickedTimers;
    private int onlineTimers;

    public TimerWorker(UserRegistry userRegistry, SessionsHolder sessionsHolder) {
        this.userRegistry = userRegistry;
        this.sessionsHolder = sessionsHolder;
    }

    @Override
    public void run() {
        log.trace("Starting timer...");
        int allTimers = 0;
        tickedTimers = 0;
        onlineTimers = 0;

        LocalTime localDateTime = LocalTime.now(UTC);

        long curTime = localDateTime.getSecond() + localDateTime.getMinute() * 60 + localDateTime.getHour() * 3600;

        for (User user : userRegistry.getUsers().values()) {
            if (user.getProfile().getDashBoards() != null) {
                for (Timer timer : user.getProfile().getActiveDashboardTimerWidgets()) {
                    allTimers++;
                    sendMessageIfTicked(user, curTime, timer.startTime, timer.startValue);
                    sendMessageIfTicked(user, curTime, timer.stopTime, timer.stopValue);
                }
            }
        }

        sessionsHolder.getUserSession().values().forEach(Session::flushPendingMessagesToHardware);

        //logging only events when timers ticked.
        if (onlineTimers > 0) {
            log.info("Timer finished. Processed {}/{}/{} timers.", onlineTimers, tickedTimers, allTimers);
        }
    }

    private void sendMessageIfTicked(User user, long curTime, Long time, String value) {
        if (timerTick(curTime, time)) {
            tickedTimers++;
            Session session = sessionsHolder.getUserSession().get(user);
            if (session != null) {
                onlineTimers++;
                if (session.hardwareChannels.size() > 0) {
                    session.pendMessageToHardware(new HardwareMessage(7777, value));
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
