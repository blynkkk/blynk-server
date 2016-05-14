package cc.blynk.server.api.http;

import cc.blynk.server.Holder;
import cc.blynk.server.api.http.handlers.HttpHandler;
import cc.blynk.server.api.http.logic.HttpAPILogic;
import cc.blynk.server.api.http.logic.HttpBusinessAPILogic;
import cc.blynk.server.core.BaseServer;
import cc.blynk.server.handlers.http.rest.HandlerRegistry;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 1/12/2015.
 */
public class HttpAPIServer extends BaseServer {

    private final ChannelInitializer<SocketChannel> channelInitializer;

    public HttpAPIServer(Holder holder) {
        super(holder.props.getIntProperty("http.port"));

        HandlerRegistry.register(new HttpAPILogic(holder));
        HandlerRegistry.register(new HttpBusinessAPILogic(holder));

        channelInitializer = new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(
                        new HttpServerCodec(),
                        new HttpObjectAggregator(1024, true),
                        holder.gatheringReadsHandler,
                        holder.gatheringWritesHandler,
                        new HttpHandler(holder.userDao, holder.sessionDao, holder.stats)
                );
            }
        };

        log.info("HTTP API port {}.", port);
    }

    @Override
    public ChannelInitializer<SocketChannel> getChannelInitializer() {
        return channelInitializer;
    }

    @Override
    protected String getServerName() {
        return "HTTP API";
    }

    @Override
    public void close() {
        System.out.println("Shutting down HTTP API server...");
        super.close();
    }

}
