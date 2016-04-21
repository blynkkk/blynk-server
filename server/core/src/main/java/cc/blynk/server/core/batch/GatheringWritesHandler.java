package cc.blynk.server.core.batch;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * The Blynk Project.
 * Created by Artem Vysochyn.
 * Created on 20.04.16.
 */
@ChannelHandler.Sharable
public final class GatheringWritesHandler extends ChannelDuplexHandler {
    private final FlushWritesTaskHolder flushWritesTaskHolder;
    private final boolean isEnabled;

    public GatheringWritesHandler(FlushWritesTaskHolder flushWritesTaskHolder, boolean isEnabled) {
        this.flushWritesTaskHolder = flushWritesTaskHolder;
        this.isEnabled = isEnabled;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
        if (flushWritesTaskHolder.addDirtyChannel(ctx.channel())) {
            ctx.write(msg, promise);
        } else {
            ctx.writeAndFlush(msg, promise);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == FlushWritesTaskHolder.FLUSH_EVENT) {
            ctx.flush();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        // no-op; used custom mechanism in write()
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        if (!isEnabled) {
            ctx.pipeline().remove(this);
        }
    }
}
