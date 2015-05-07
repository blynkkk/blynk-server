package cc.blynk.integration;

import cc.blynk.integration.model.ClientPair;
import cc.blynk.integration.model.SimpleClientHandler;
import cc.blynk.integration.model.TestAppClient;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/2/2015.
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class SimplePerformanceTest extends IntegrationBase {

    private final AtomicInteger counter = new AtomicInteger();

    @Test
    @Ignore
    public void emulateSlider() throws Exception {
        TestAppClient appClient = new TestAppClient("localhost", 8443);
        appClient.start(null);

        appClient.send("login dima@dima.ua 1");

        verify(appClient.responseMock, timeout(500)).channelRead(any(), any());

        for (int i = 0; i < 255; i++) {
            appClient.send("hardware aw 9 " + i);
            sleep(5);
        }
    }

    @Test
    @Ignore
    public void testConnectAppAndHardware() throws Exception {
        int clientNumber = 250;
        ExecutorService executorService = Executors.newFixedThreadPool(4);

        ClientPair[] clients = new ClientPair[clientNumber];
        List<Future<ClientPair>> futures = new ArrayList<>();

        long start = System.currentTimeMillis();
        for (int i = 0; i < clientNumber; i++) {
            Future<ClientPair> future = executorService.submit(
                    () -> initAppAndHardPair("localhost", 8443, 8442, "dima" + counter.incrementAndGet() + "@mail.ua 1", null, false)
            );
            futures.add(future);
        }

        int counter = 0;
        for (Future<ClientPair> clientPairFuture : futures) {
            clients[counter] = clientPairFuture.get();
            //removing mocks, replace with real class
            clients[counter].appClient.replace(new SimpleClientHandler());
            clients[counter].hardwareClient.replace(new SimpleClientHandler());
            counter++;
        }

        System.out.println(clientNumber + " client pairs created in " + (System.currentTimeMillis() - start));

        System.currentTimeMillis();
        while (true) {
            for (ClientPair clientPair : clients) {
                clientPair.appClient.send("hardware aw 10 10");
            }
            sleep(10);
        }
    }

}
