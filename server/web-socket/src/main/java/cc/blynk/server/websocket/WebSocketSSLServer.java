package cc.blynk.server.websocket;

import cc.blynk.server.Holder;
import cc.blynk.server.core.BaseServer;
import cc.blynk.server.core.protocol.handlers.decoders.MessageDecoder;
import cc.blynk.server.handlers.common.UserNotLoggedHandler;
import cc.blynk.server.hardware.handlers.hardware.HardwareChannelStateHandler;
import cc.blynk.server.hardware.handlers.hardware.auth.HardwareLoginHandler;
import cc.blynk.server.websocket.handlers.WebSocketEncoder;
import cc.blynk.server.websocket.handlers.WebSocketHandler;
import cc.blynk.server.websocket.handlers.WebSocketWrapperEncoder;
import cc.blynk.utils.SslUtil;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.DomainNameMapping;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 13/01/2016.
 */
public class WebSocketSSLServer extends BaseServer {

    private final ChannelInitializer<SocketChannel> channelInitializer;

    public WebSocketSSLServer(Holder holder) {
        super(holder.props.getIntProperty("ssl.websocket.port"));

        final int hardTimeoutSecs = holder.props.getIntProperty("hard.socket.idle.timeout", 0);
        final HardwareLoginHandler hardwareLoginHandler = new HardwareLoginHandler(holder);
        final HardwareChannelStateHandler hardwareChannelStateHandler = new HardwareChannelStateHandler(holder.sessionDao, holder.blockingIOProcessor, holder.gcmWrapper);
        final UserNotLoggedHandler userNotLoggedHandler = new UserNotLoggedHandler();

        final DomainNameMapping<SslContext> mappings = SslUtil.getDomainMappings(holder.props);

        channelInitializer = new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                if (hardTimeoutSecs > 0) {
                    pipeline.addLast(new ReadTimeoutHandler(hardTimeoutSecs));
                }
                ch.pipeline().addLast(
                        new SniHandler(mappings),
                        new HttpServerCodec(),
                        new HttpObjectAggregator(65536),
                        new WebSocketHandler(true),

                        //hardware handlers
                        hardwareChannelStateHandler,
                        new MessageDecoder(holder.stats),
                        new WebSocketWrapperEncoder(),
                        new WebSocketEncoder(holder.stats),
                        holder.gatheringReadsHandler,
                        holder.gatheringWritesHandler,
                        hardwareLoginHandler,
                        userNotLoggedHandler
                );
            }
        };

        log.info("Web SSL Sockets port {}.", port);
    }

    @Override
    public ChannelInitializer<SocketChannel> getChannelInitializer() {
        return channelInitializer;
    }

    @Override
    protected String getServerName() {
        return "Web SSL Sockets";
    }

    @Override
    public void close() {
        System.out.println("Shutting down Web SSL Sockets server...");
        super.close();
    }

}
