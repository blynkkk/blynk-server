package cc.blynk.client.handlers;

import cc.blynk.client.handlers.decoders.ClientMessageDecoder;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/20/2015.
 */
public class ClientReplayingMessageDecoder extends ClientMessageDecoder {

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        throw new IOException("Server closed client connection.");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //server goes down
        if (IOException.class.isInstance(cause)) {
            ctx.close();
            log.error("Client socket closed. Reason : {}", cause.getMessage());
            //todo find better way
            System.exit(0);
        }
    }
}
