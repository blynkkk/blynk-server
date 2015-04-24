package cc.blynk.server.workers.timer;

import cc.blynk.server.dao.SessionsHolder;
import cc.blynk.server.dao.UserRegistry;
import cc.blynk.server.model.DashBoard;
import cc.blynk.server.model.Profile;
import cc.blynk.server.model.auth.Session;
import cc.blynk.server.model.auth.User;
import cc.blynk.server.model.widgets.others.Timer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.*;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/6/2015.
 */
@RunWith(MockitoJUnitRunner.class)
public class TimerWorkerTest {

    @Mock
    private UserRegistry userRegistry;

    @Mock
    private SessionsHolder sessionsHolder;

    @Spy
    @InjectMocks
    private TimerWorker timerWorker;

    @Mock
    private User user;

    @Mock
    private Profile profile;

    private ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();

    private ConcurrentHashMap<User, Session> userSession = new ConcurrentHashMap<>();

    private Set<Timer> timers = new HashSet<>();
    private Timer w = new Timer();

    @Before
    public void init() {
        timers.add(w);
    }

    @Test
    public void testTimer() throws InterruptedException {
        //wait for start of a second
        long startDelay = 1001 - (System.currentTimeMillis() % 1000);
        try {
            Thread.sleep(startDelay);
        } catch (InterruptedException e) {
        }

        LocalDateTime localDateTime = LocalDateTime.now(ZoneId.of("UTC"));
        long curTime = localDateTime.getSecond() + localDateTime.getMinute() * 60 + localDateTime.getHour() * 3600;
        w.startTime = curTime;

        int userCount = 1000;
        for (int i = 0; i < userCount; i++) {
            users.put(String.valueOf(i), user);
        }

        when(userRegistry.getUsers()).thenReturn(users);
        when(sessionsHolder.getUserSession()).thenReturn(userSession);
        when(user.getProfile()).thenReturn(profile);
        when(profile.getDashBoards()).thenReturn(new DashBoard[] {});
        when(profile.getActiveDashboardTimerWidgets()).thenReturn(timers);

        timerWorker.run();

        Thread.sleep(200);
        verify(timerWorker, times(1000)).timerTick(eq(curTime), eq(curTime));
    }

}
