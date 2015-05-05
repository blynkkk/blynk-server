package cc.blynk.integration;

import cc.blynk.common.model.messages.Message;
import cc.blynk.common.stats.GlobalStats;
import cc.blynk.common.utils.ServerProperties;
import cc.blynk.integration.model.ClientPair;
import cc.blynk.integration.model.TestAppClient;
import cc.blynk.integration.model.TestHardClient;
import cc.blynk.integration.model.TestMutualAppClient;
import cc.blynk.server.dao.FileManager;
import cc.blynk.server.dao.JedisWrapper;
import cc.blynk.server.dao.SessionsHolder;
import cc.blynk.server.dao.UserRegistry;
import cc.blynk.server.model.Profile;
import cc.blynk.server.utils.JsonParser;
import cc.blynk.server.workers.notifications.NotificationsProcessor;
import org.junit.BeforeClass;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.io.BufferedReader;
import java.io.InputStream;
import java.util.List;

import static cc.blynk.common.enums.Response.OK;
import static cc.blynk.common.model.messages.MessageFactory.produce;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/4/2015.
 */
public abstract class IntegrationBase {

    public static ServerProperties properties;
    public static int appPort;
    public static int hardPort;
    public static String host;
    private static int hardSslPort;


    @Mock
    public BufferedReader bufferedReader;

    @Mock
    public BufferedReader bufferedReader2;

    @Mock
    public NotificationsProcessor notificationsProcessor;

    public FileManager fileManager;

    public SessionsHolder sessionsHolder;

    public UserRegistry userRegistry;

    public GlobalStats stats;

    public JedisWrapper jedisWrapper;

    @BeforeClass
    public static void initBase() {
        properties = new ServerProperties();
        appPort = properties.getIntProperty("app.ssl.port");
        hardPort = properties.getIntProperty("hardware.default.port");
        hardSslPort = properties.getIntProperty("hardware.ssl.port");
        host = "localhost";
    }

    public static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {

        }

    }

    public static String readTestUserProfile(String fileName) {
        if (fileName == null) {
            fileName = "user_profile_json.txt";
        }
        InputStream is = IntegrationBase.class.getResourceAsStream("/json_test/" + fileName);
        Profile profile = JsonParser.parseProfile(is);
        return profile.toString();
    }

    public static String readTestUserProfile() {
        return readTestUserProfile(null);
    }

    public ClientPair initAppAndHardPair() throws Exception {
        return initAppAndHardPair("localhost", appPort, hardPort, "dima@mail.ua 1", null);
    }

    public ClientPair initMutualAppAndHardPair() throws Exception {
        return initMutualAppAndHardPair("localhost", appPort, hardSslPort, "andrew@mail.ua 1", null);
    }

    public ClientPair initAppAndHardPair(String jsonProfile) throws Exception {
        return initAppAndHardPair("localhost", appPort, hardPort, "dima@mail.ua 1", jsonProfile);
    }

    public ClientPair initAppAndHardPair(String host, int appPort, int hardPort, String user, String jsonProfile) throws Exception {
        TestAppClient appClient = new TestAppClient(host, appPort);
        TestHardClient hardClient = new TestHardClient(host, hardPort);

        appClient.start(null);
        hardClient.start(null);

        String userProfileString = readTestUserProfile(jsonProfile);

        appClient.send("register " + user)
                .send("login " + user)
                .send("saveProfile " + userProfileString)
                .send("activate 1")
                .send("getToken 1");

        ArgumentCaptor<Object> objectArgumentCaptor = ArgumentCaptor.forClass(Object.class);
        verify(appClient.responseMock, timeout(2000).times(5)).channelRead(any(), objectArgumentCaptor.capture());

        List<Object> arguments = objectArgumentCaptor.getAllValues();
        Message getTokenMessage = (Message) arguments.get(4);
        String token = getTokenMessage.body;

        hardClient.send("login " + token);
        verify(hardClient.responseMock, timeout(2000)).channelRead(any(), eq(produce(1, OK)));

        appClient.reset();
        hardClient.reset();

        return new ClientPair(appClient, hardClient, token);
    }

    public ClientPair initMutualAppAndHardPair(String host, int appPort, int hardPort, String user, String jsonProfile) throws Exception {
        TestMutualAppClient appClient = new TestMutualAppClient(host, appPort, false);
        TestHardClient hardClient = new TestHardClient(host, hardPort);

        appClient.start(null);
        hardClient.start(null);

        String userProfileString = readTestUserProfile(jsonProfile);

        appClient.send("register " + user)
                .send("login " + user)
                .send("saveProfile " + userProfileString)
                .send("activate 1")
                .send("getToken 1");

        ArgumentCaptor<Object> objectArgumentCaptor = ArgumentCaptor.forClass(Object.class);
        verify(appClient.responseMock, timeout(2000).times(5)).channelRead(any(), objectArgumentCaptor.capture());

        List<Object> arguments = objectArgumentCaptor.getAllValues();
        Message getTokenMessage = (Message) arguments.get(4);
        String token = getTokenMessage.body;

        hardClient.send("login " + token);
        verify(hardClient.responseMock, timeout(2000)).channelRead(any(), eq(produce(1, OK)));

        appClient.reset();
        hardClient.reset();

        return new ClientPair(appClient, hardClient, token);
    }

    public void initServerStructures() {
        fileManager = new FileManager(properties.getProperty("data.folder"));
        sessionsHolder = new SessionsHolder();
        userRegistry = new UserRegistry(fileManager.deserialize());
        stats = new GlobalStats();
        jedisWrapper = new JedisWrapper(properties);
    }
}
