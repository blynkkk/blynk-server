package cc.blynk.integration;

import cc.blynk.common.model.messages.Message;
import cc.blynk.common.utils.ServerProperties;
import cc.blynk.integration.model.ClientPair;
import cc.blynk.integration.model.TestHardClient;
import cc.blynk.server.TransportTypeHolder;
import cc.blynk.server.core.application.AppServer;
import cc.blynk.server.core.hardware.HardwareServer;
import io.netty.channel.ChannelFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static cc.blynk.common.enums.Command.HARDWARE_COMMAND;
import static cc.blynk.common.enums.Response.*;
import static cc.blynk.common.model.messages.MessageFactory.produce;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * The Blynk Project.
 * Created by Andrew Zakordonets.
 * Created on 03/5/2015.
 */
public class MainWorkflowWithMutualSslTest extends IntegrationBase {

    private AppServer appServer;
    private ClientPair clientPair;
    private HardwareServer hardwareServer;

    @Before
    public void init() throws Exception {
        initServerStructures();
        properties = new ServerProperties("mutual.server.properties");

        hardwareServer = new HardwareServer(properties, userRegistry, sessionsHolder, stats, notificationsProcessor, new TransportTypeHolder(properties));
        appServer = new AppServer(properties, userRegistry, sessionsHolder, stats, new TransportTypeHolder(properties));

        new Thread(hardwareServer).start();
        new Thread(appServer).start();

        //todo improve this
        //wait util server starts.
        sleep(500);

        clientPair = initMutualAppAndHardPair();
    }

    @After
    public void shutdown() {
        appServer.stop();
        hardwareServer.stop();
        clientPair.stopMutual();
    }

    @Test
    public void testConnectAppAndHardware() throws Exception {
        // we just test that app and hardware can actually connect
    }

    @Test
    public void testHardwareDeviceWentOffline() throws Exception {
        //waiting for channel to become inactive
        sleep(2000);
        verify(clientPair.appMutualClient.responseMock, timeout(500)).channelRead(any(), eq(produce(0, DEVICE_WENT_OFFLINE)));
    }

    @Test
    public void testPingCommandWorks() throws Exception {
        clientPair.appMutualClient.send("ping");
        verify(clientPair.appMutualClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, OK)));
    }

    @Test
    public void testPingCommandOk() throws Exception {
        clientPair.appMutualClient.send("ping");
        verify(clientPair.appMutualClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, OK)));

        clientPair.appMutualClient.reset();

        clientPair.appMutualClient.send("ping");
        verify(clientPair.appMutualClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, OK)));
    }

    @Test
    public void testAppSendAnyHardCommandAndBack() throws Exception {
        clientPair.appMutualClient.send("hardware 1 1");
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, HARDWARE_COMMAND, "1 1".replaceAll(" ", "\0"))));

        clientPair.hardwareClient.send("hardware ar 1");

        ArgumentCaptor<Message> objectArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(clientPair.appMutualClient.responseMock, timeout(500).times(1)).channelRead(any(), objectArgumentCaptor.capture());

        List<Message> arguments = objectArgumentCaptor.getAllValues();
        Message hardMessage = arguments.get(0);
        assertEquals(1, hardMessage.id);
        assertEquals(HARDWARE_COMMAND, hardMessage.command);
        assertEquals(4, hardMessage.length);
        assertEquals("ar 1".replaceAll(" ", "\0"), hardMessage.body);
    }

    @Test
    public void testAppSendWriteHardCommandNotGraphAndBack() throws Exception {
        clientPair.appMutualClient.send("hardware ar 11");
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, HARDWARE_COMMAND, "ar 11".replaceAll(" ", "\0"))));

        String body = "aw 11 333";
        clientPair.hardwareClient.send("hardware " + body);

        ArgumentCaptor<Message> objectArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(clientPair.appMutualClient.responseMock, timeout(500).times(1)).channelRead(any(), objectArgumentCaptor.capture());

        List<Message> arguments = objectArgumentCaptor.getAllValues();
        Message hardMessage = arguments.get(0);
        assertEquals(1, hardMessage.id);
        assertEquals(HARDWARE_COMMAND, hardMessage.command);
        assertEquals(body.length(), hardMessage.length);
        assertTrue(hardMessage.body.startsWith(body.replaceAll(" ", "\0")));
    }


    @Test
    public void testActivateWorkflow() throws Exception {
        clientPair.appMutualClient.send("activate 2");
        verify(clientPair.appMutualClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, ILLEGAL_COMMAND)));

        clientPair.appMutualClient.send("deactivate");
        verify(clientPair.appMutualClient.responseMock, timeout(500)).channelRead(any(), eq(produce(2, OK)));

        clientPair.appMutualClient.send("hardware ar 1 1");
        verify(clientPair.appMutualClient.responseMock, timeout(500)).channelRead(any(), eq(produce(3, NO_ACTIVE_DASHBOARD)));

        clientPair.appMutualClient.send("activate 1");
        verify(clientPair.appMutualClient.responseMock, timeout(500)).channelRead(any(), eq(produce(4, OK)));

        clientPair.appMutualClient.send("hardware ar 1 1");
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(produce(5, HARDWARE_COMMAND, "ar 1 1".replaceAll(" ", "\0"))));

        String userProfileWithGraph = readTestUserProfile();

        clientPair.appMutualClient.send("saveProfile " + userProfileWithGraph);
        verify(clientPair.appMutualClient.responseMock, timeout(500)).channelRead(any(), eq(produce(6, OK)));


        clientPair.appMutualClient.send("hardware ar 1 1");
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(produce(7, HARDWARE_COMMAND, "ar 1 1".replaceAll(" ", "\0"))));
    }

    @Test
    public void testAppSendWriteHardCommandForGraphAndBack() throws Exception {
        String userProfileWithGraph = readTestUserProfile();
        clientPair.appMutualClient.send("saveProfile " + userProfileWithGraph);
        verify(clientPair.appMutualClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, OK)));

        reset(clientPair.appMutualClient.responseMock);
        clientPair.appMutualClient.reset();

        clientPair.appMutualClient.send("hardware ar 8");
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, HARDWARE_COMMAND, "ar 8".replaceAll(" ", "\0"))));

        String body = "aw 8 333";
        clientPair.hardwareClient.send("hardware " + body);

        ArgumentCaptor<Message> objectArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(clientPair.appMutualClient.responseMock, timeout(500).times(1)).channelRead(any(), objectArgumentCaptor.capture());

        List<Message> arguments = objectArgumentCaptor.getAllValues();
        Message hardMessage = arguments.get(0);
        assertEquals(1, hardMessage.id);
        assertEquals(HARDWARE_COMMAND, hardMessage.command);
        //"aw 11 333".length + ts.length + separator
        assertEquals(body.length() + 14, hardMessage.length);
        assertTrue(hardMessage.body.startsWith(body.replaceAll(" ", "\0")));
    }

    @Test
    //todo more tests for that
    public void testSendPinModeCommandWhenHardwareGoesOnline() throws Exception {
        ChannelFuture channelFuture = clientPair.hardwareClient.stop();
        channelFuture.await();

        if (!channelFuture.isDone()) {
            throw new RuntimeException("Error closing hard cahnnel.");
        }

        String body = "pm 13 in";
        clientPair.appMutualClient.send("hardware " + body);
        verify(clientPair.appMutualClient.responseMock, timeout(1000)).channelRead(any(), eq(produce(1, DEVICE_NOT_IN_NETWORK)));

        TestHardClient hardClient = new TestHardClient(host, hardPort);
        hardClient.start(null);
        hardClient.send("login " + clientPair.token);
        verify(hardClient.responseMock, timeout(2000)).channelRead(any(), eq(produce(1, OK)));
        verify(hardClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, HARDWARE_COMMAND, body.replaceAll(" ", "\0"))));
    }

    @Test
    public void testConnectAppAndHardwareAndSendCommands() throws Exception {
        for (int i = 0; i < 100; i++) {
            clientPair.appMutualClient.send("hardware 1 1");
        }

        verify(clientPair.hardwareClient.responseMock, timeout(500).times(100)).channelRead(any(), any());
    }

    @Test
    @Ignore
    public void testTryReachQuotaLimit() throws Exception {
        String body = "ar 100 100";

        //within 1 second sending more messages than default limit 100.
        for (int i = 0; i < 1000 / 9; i++) {
            clientPair.appMutualClient.send("hardware " + body, 1);
            sleep(9);
        }

        verify(clientPair.appMutualClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, QUOTA_LIMIT_EXCEPTION)));
        verify(clientPair.hardwareClient.responseMock, atLeast(100)).channelRead(any(), eq(produce(1, HARDWARE_COMMAND, body.replaceAll(" ", "\0"))));

        clientPair.appMutualClient.reset();
        clientPair.hardwareClient.reset();

        //check no more accepted
        for (int i = 0; i < 10; i++) {
            clientPair.appMutualClient.send("hardware " + body, 1);
            sleep(9);
        }

        verify(clientPair.appMutualClient.responseMock, times(0)).channelRead(any(), eq(produce(1, QUOTA_LIMIT_EXCEPTION)));
        verify(clientPair.hardwareClient.responseMock, times(0)).channelRead(any(), eq(produce(1, HARDWARE_COMMAND, body.replaceAll(" ", "\0"))));
    }


    @Test
    public void test2ClientPairsWorkCorrectly() throws Exception {
        final int ITERATIONS = 100;
        ClientPair clientPair2 = initAppAndHardPair("localhost", appPort, hardPort, "dima2@mail.ua 1", null, true);

        String body = "ar 1";
        for (int i = 1; i <= ITERATIONS; i++) {
            clientPair.appMutualClient.send("hardware " + body);
            clientPair2.appMutualClient.send("hardware " + body);
        }

        verify(clientPair.hardwareClient.responseMock, timeout(500).times(ITERATIONS)).channelRead(any(), any());
        verify(clientPair2.hardwareClient.responseMock, timeout(500).times(ITERATIONS)).channelRead(any(), any());


        for (int i = 1; i <= ITERATIONS; i++) {
            verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(produce(i, HARDWARE_COMMAND, body.replaceAll(" ", "\0"))));
            verify(clientPair2.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(produce(i, HARDWARE_COMMAND, body.replaceAll(" ", "\0"))));
        }
    }


    @Test
    @Ignore("hard to test this case...")
    public void testTryReachQuotaLimitAndWarningExceededLimit() throws Exception {
        String body = "ar 100 100";

        //within 1 second sending more messages than default limit 100.
        for (int i = 0; i < 1000 / 9; i++) {
            clientPair.appMutualClient.send("hardware " + body, 1);
            sleep(9);
        }

        verify(clientPair.appMutualClient.responseMock, timeout(1000)).channelRead(any(), eq(produce(1, QUOTA_LIMIT_EXCEPTION)));
        verify(clientPair.hardwareClient.responseMock, atLeast(100)).channelRead(any(), eq(produce(1, HARDWARE_COMMAND, body.replaceAll(" ", "\0"))));

        clientPair.appMutualClient.reset();
        clientPair.hardwareClient.reset();

        //waiting to avoid limit.
        sleep(1000);

        for (int i = 0; i < 100000 / 9; i++) {
            clientPair.appMutualClient.send("hardware " + body, 1);
            sleep(9);
        }

        verify(clientPair.appMutualClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, QUOTA_LIMIT_EXCEPTION)));
        verify(clientPair.hardwareClient.responseMock, atLeast(100)).channelRead(any(), eq(produce(1, HARDWARE_COMMAND, body.replaceAll(" ", "\0"))));

    }




}
