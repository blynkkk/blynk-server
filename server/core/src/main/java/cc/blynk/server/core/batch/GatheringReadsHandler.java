package cc.blynk.server.core.batch;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * The Blynk Project.
 * Created by Artem Vysochyn.
 * Created on 20.04.16.
 */
@ChannelHandler.Sharable
public final class GatheringReadsHandler extends ChannelInboundHandlerAdapter {
    private final FlushWritesTaskHolder flushWritesTaskHolder;
    private final boolean isEnabled;

    public GatheringReadsHandler(FlushWritesTaskHolder flushWritesTaskHolder, boolean isEnabled) {
        this.flushWritesTaskHolder = flushWritesTaskHolder;
        this.isEnabled = isEnabled;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        flushWritesTaskHolder.setOnChannelRead(ctx.channel().eventLoop());
        super.channelRead(ctx, msg);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        if (!isEnabled) {
            ctx.pipeline().remove(this);
        }
    }
}
