package cc.blynk.integration.model;

import cc.blynk.client.core.BaseClient;
import cc.blynk.common.handlers.common.decoders.MessageDecoder;
import cc.blynk.common.handlers.common.encoders.MessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;
import org.mockito.Mock;
import org.mockito.Mockito;

import javax.net.ssl.SSLException;
import java.io.BufferedReader;
import java.io.File;
import java.util.Random;

import static org.mockito.Mockito.spy;

/**
 * The Blynk Project.
 * Created by Andrew Zakordonets.
 * Created on 03/5/2015.
 */
public class TestMutualAppClient extends BaseClient {

    public final SimpleClientHandler responseMock = Mockito.mock(SimpleClientHandler.class);
    protected SslContext sslCtx;
    @Mock
    Random random;
    private int msgId = 0;

    private ChannelPipeline pipeline;
    private boolean disableAppSsl;

    public TestMutualAppClient(String host, int port, boolean disableAppSsl) {
        this(host, port, new Random(), disableAppSsl);
        random = spy(new Random());
        Mockito.when(random.nextInt(Short.MAX_VALUE)).thenReturn(1);
        this.disableAppSsl = false;
    }

    public TestMutualAppClient(String host, int port, Random msgIdGenerator, boolean disableAppSsl) {
        super(host, port, msgIdGenerator);
        log.info("Creating app client. Host {}, sslPort : {}", host, port);

        if (!disableAppSsl) {
            try {
                this.sslCtx = SslContext.newClientContext(SslProvider.JDK,
                        new File(getTestCertificatePath("server.crt")),
                        null,
                        new File(getTestCertificatePath("app.crt")),
                        new File(getTestCertificatePath("app.pem")),
                        "blynkawesome",
                        null, null, IdentityCipherSuiteFilter.INSTANCE, null, 0, 0);
            } catch (SSLException e) {
                log.error("Error initializing SSL context. Reason : {}", e.getMessage());
                log.debug(e);
                throw new RuntimeException();
            }
        }
    }

    @Override
    public void start(BufferedReader commandInputStream) {
        if (commandInputStream == null) {
            nioEventLoopGroup = new NioEventLoopGroup();

            Bootstrap b = new Bootstrap();
            b.group(nioEventLoopGroup).channel(NioSocketChannel.class).handler(getChannelInitializer());

            try {
                // Start the connection attempt.
                this.channel = b.connect(host, port).sync().channel();
            } catch (InterruptedException e) {
                log.error(e);
            }
        } else {
            super.start(commandInputStream);
        }
    }

    private String getTestCertificatePath(String name) {
        return getClass().getResource("/test-certs/mutual/" + name).toString().substring(5);
    }

    @Override
    protected ChannelInitializer<SocketChannel> getChannelInitializer() {
        return new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                TestMutualAppClient.this.pipeline = pipeline;

                if (!disableAppSsl) {
                    pipeline.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                }

                pipeline.addLast(new MessageDecoder());
                pipeline.addLast(new MessageEncoder());
                pipeline.addLast(responseMock);
            }
        };
    }

    public TestMutualAppClient send(String line) {
        send(produceMessageBaseOnUserInput(line, ++msgId));
        return this;
    }

    public TestMutualAppClient send(String line, int id) {
        send(produceMessageBaseOnUserInput(line, id));
        return this;
    }

    public void reset() {
        Mockito.reset(responseMock);
        msgId = 0;
    }

    public void replace(SimpleClientHandler simpleClientHandler) {
        pipeline.removeLast();
        pipeline.addLast(simpleClientHandler);
    }
}
