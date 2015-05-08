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
        host = "localhost";
    }

    public static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            //we can ignore it
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

    ClientPair initAppAndHardPair() throws Exception {
        return initAppAndHardPair("localhost", appPort, hardPort, "dima@mail.ua 1", null, false);
    }

    ClientPair initMutualAppAndHardPair() throws Exception {
        return initAppAndHardPair("localhost", appPort, hardPort, "andrew@mail.ua 1", null, true);
    }

    public ClientPair initAppAndHardPair(String jsonProfile) throws Exception {
        return initAppAndHardPair("localhost", appPort, hardPort, "dima@mail.ua 1", jsonProfile, false);
    }

    ClientPair initAppAndHardPair(String host, int appPort, int hardPort, String user, String jsonProfile,
                                  boolean enableMutual) throws Exception {
        TestAppClient appClient = null;
        TestMutualAppClient mutualAppClient = null;
        if (!enableMutual) {
            appClient = new TestAppClient(host, appPort);
        } else {
            mutualAppClient = new TestMutualAppClient(host, appPort);
        }
        TestHardClient hardClient = new TestHardClient(host, hardPort);

        if (appClient != null) {
            appClient.start(null);
        } else {
            mutualAppClient.start(null);
        }
        hardClient.start(null);

        String userProfileString = readTestUserProfile(jsonProfile);

        if (appClient != null){
            appClient.send("register " + user)
                    .send("login " + user)
                    .send("saveProfile " + userProfileString)
                    .send("activate 1")
                    .send("getToken 1");
        } else {
            mutualAppClient.send("register " + user)
                    .send("login " + user)
                    .send("saveProfile " + userProfileString)
                    .send("activate 1")
                    .send("getToken 1");
        }

        ArgumentCaptor<Object> objectArgumentCaptor = ArgumentCaptor.forClass(Object.class);
        if (appClient != null){
            verify(appClient.responseMock, timeout(2000).times(5)).channelRead(any(), objectArgumentCaptor.capture());
        } else {
            verify(mutualAppClient.responseMock, timeout(2000).times(5)).channelRead(any(), objectArgumentCaptor.capture());
        }

        List<Object> arguments = objectArgumentCaptor.getAllValues();
        Message getTokenMessage = (Message) arguments.get(4);
        String token = getTokenMessage.body;

        hardClient.send("login " + token);
        verify(hardClient.responseMock, timeout(2000)).channelRead(any(), eq(produce(1, OK)));

        if (appClient != null) {
            appClient.reset();
        } else {
            mutualAppClient.reset();
        }
        hardClient.reset();

        if (appClient != null) {
            return new ClientPair(appClient, hardClient, token);
        } else {
            return new ClientPair(mutualAppClient, hardClient, token);
        }
    }

    public void initServerStructures() {
        fileManager = new FileManager(properties.getProperty("data.folder"));
        sessionsHolder = new SessionsHolder();
        userRegistry = new UserRegistry(fileManager.deserialize());
        stats = new GlobalStats();
        jedisWrapper = new JedisWrapper(properties);
    }
}
