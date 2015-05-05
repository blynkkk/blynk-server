package cc.blynk.integration;

import cc.blynk.integration.model.ClientPair;
import cc.blynk.server.TransportTypeHolder;
import cc.blynk.server.core.application.AppServer;
import cc.blynk.server.core.hardware.ssl.HardwareSSLServer;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The Blynk Project.
 * Created by Andrew Zakordonets.
 * Created on 03/5/2015.
 */
public class MainWorkflowWithMutualSslTest extends IntegrationBase {

    private AppServer appServer;
    private HardwareSSLServer hardwareSSLServer;
    private ClientPair clientPair;

    private String getTestCertificatePath(String name) {
        return getClass().getResource("/test-certs/mutual/"+name).toString().substring(5);
    }

    @Before
    public void init() throws Exception {
        initServerStructures();

        FileUtils.deleteDirectory(fileManager.getDataDir().toFile());
        properties.put("server.ssl.key", getTestCertificatePath("server.pem"));
        properties.put("server.ssl.cert", getTestCertificatePath("server.crt"));
        properties.put("client.ssl.cert", getTestCertificatePath("app.crt"));


        hardwareSSLServer = new HardwareSSLServer(properties, userRegistry, sessionsHolder, stats, notificationsProcessor, new TransportTypeHolder(properties));
        appServer = new AppServer(properties, userRegistry, sessionsHolder, stats, new TransportTypeHolder(properties));

        new Thread(hardwareSSLServer).start();
        new Thread(appServer).start();

        //todo improve this
        //wait util server starts.
        sleep(500);

        clientPair = initMutualAppAndHardPair();
    }

    @After
    public void shutdown() {
        appServer.stop();
        hardwareSSLServer.stop();
        clientPair.stop();
    }

    @Test
    public void testConnectAppAndHardware() throws Exception {

    }




}
