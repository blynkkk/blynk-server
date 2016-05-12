package cc.blynk.server.core.model.auth;

import cc.blynk.server.core.protocol.enums.Response;
import cc.blynk.server.core.session.HardwareStateHolder;
import cc.blynk.server.core.stats.metrics.InstanceLoadMeter;
import cc.blynk.server.handlers.BaseSimpleChannelInboundHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.util.internal.ConcurrentSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.stream.Collectors;

import static cc.blynk.utils.ByteBufUtil.*;
import static cc.blynk.utils.StateHolderUtil.*;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 * DefaultChannelGroup.java too complicated. so doing in simple way for now.
 *
 */
public class Session {

    private static final Logger log = LogManager.getLogger(Session.class);

    public final EventLoop initialEventLoop;
    private final Set<Channel> appChannels = new ConcurrentSet<>();
    private final Set<Channel> hardwareChannels = new ConcurrentSet<>();

    private final ChannelFutureListener appRemover = future -> removeAppChannel(future.channel());
    private final ChannelFutureListener hardRemover = future -> removeHardChannel(future.channel());

    public Session(EventLoop initialEventLoop) {
        this.initialEventLoop = initialEventLoop;
    }

    private static int getRequestRate(Set<Channel> channels) {
        double sum = 0;
        for (Channel c : channels) {
            BaseSimpleChannelInboundHandler handler = c.pipeline().get(BaseSimpleChannelInboundHandler.class);
            if (handler != null) {
                InstanceLoadMeter loadMeter = handler.getQuotaMeter();
                sum += loadMeter.getOneMinuteRateNoTick();
            }
        }
        return (int) sum;
    }

    public static boolean needSync(Channel channel, String sharedToken) {
        BaseSimpleChannelInboundHandler appHandler = channel.pipeline().get(BaseSimpleChannelInboundHandler.class);
        return appHandler != null && appHandler.state.contains(sharedToken);
    }

    public void addAppChannel(Channel appChannel) {
        if (appChannels.add(appChannel)) {
            appChannel.closeFuture().addListener(appRemover);
        }
    }

    public void removeAppChannel(Channel appChannel) {
        if (appChannels.remove(appChannel)) {
            appChannel.closeFuture().removeListener(appRemover);
        }
    }

    public void addHardChannel(Channel hardChannel) {
        if (hardwareChannels.add(hardChannel)) {
            hardChannel.closeFuture().addListener(hardRemover);
        }
    }

    public void removeHardChannel(Channel hardChannel) {
        if (hardwareChannels.remove(hardChannel)) {
            hardChannel.closeFuture().removeListener(hardRemover);
        }
    }

    public boolean sendMessageToHardware(int activeDashId, short cmd, int msgId, String body) {
        Set<Channel> targetChannels = hardwareChannels.stream().
                filter(channel -> {
                            HardwareStateHolder hardwareState = getHardState(channel);
                            return hardwareState != null && hardwareState.dashId == activeDashId;
                        }
                ).
                collect(Collectors.toSet());

        int channelsNum = targetChannels.size();
        if (channelsNum == 0)
            return true; // -> noActiveHardware

        ByteBuf msg = makeStringMessage(cmd, msgId, body);
        if (channelsNum > 1) {
            msg.retain(channelsNum - 1).markReaderIndex();
        }

        for (Channel channel : targetChannels) {
            log.trace("Sending {} to hardware {}", body, channel);
            channel.writeAndFlush(msg, channel.voidPromise());
            if (msg.refCnt() > 0) {
                msg.resetReaderIndex();
            }
        }

        return false; // -> noActiveHardware
    }

    public void sendMessageToHardware(ChannelHandlerContext ctx, int activeDashId, short cmd, int msgId, String body) {
        if (sendMessageToHardware(activeDashId, cmd, msgId, body)) {
            log.debug("No device in session.");
            ctx.writeAndFlush(makeResponse(msgId, Response.DEVICE_NOT_IN_NETWORK), ctx.voidPromise());
        }
    }

    public boolean hasHardwareOnline(int activeDashId) {
        for (Channel channel : hardwareChannels) {
            HardwareStateHolder hardwareState = getHardState(channel);
            if (hardwareState != null) {
                if (hardwareState.dashId == activeDashId) {
                    return true;
                }
            }
        }
        return false;
    }

    public void sendToApps(short cmd, int msgId, String body) {
        int channelsNum = appChannels.size();
        if (channelsNum == 0)
            return;

        ByteBuf msg = makeStringMessage(cmd, msgId, body);
        if (channelsNum > 1) {
            msg.retain(channelsNum - 1).markReaderIndex();
        }

        for (Channel channel : appChannels) {
            log.trace("Sending {} to app {}", body, channel);
            channel.writeAndFlush(msg, channel.voidPromise());
            if (msg.refCnt() > 0) {
                msg.resetReaderIndex();
            }
        }
    }

    public void sendToSharedApps(Channel sendingChannel, String sharedToken, short cmd, int msgId, String body) {
        Set<Channel> targetChannels = appChannels.stream().
                filter(appChannel -> appChannel != sendingChannel && needSync(appChannel, sharedToken)).
                collect(Collectors.toSet());

        int channelsNum = targetChannels.size();
        if (channelsNum == 0)
            return;

        ByteBuf msg = makeStringMessage(cmd, msgId, body);
        if (channelsNum > 1) {
            msg.retain(channelsNum - 1).markReaderIndex();
        }

        for (Channel channel : targetChannels) {
            log.trace("Sending {} to app {}", body, channel);
            channel.writeAndFlush(msg, channel.voidPromise());
            if (msg.refCnt() > 0) {
                msg.resetReaderIndex();
            }
        }
    }

    public boolean isHardwareConnected(int dashId) {
        for (Channel channel : hardwareChannels) {
            HardwareStateHolder hardwareState = getHardState(channel);
            if (hardwareState != null && hardwareState.dashId == dashId) {
                return true;
            }
        }
        return false;
    }


    public boolean isAppConnected() {
        return appChannels.size() > 0;
    }

    public int getAppRequestRate() {
        return getRequestRate(appChannels);
    }

    public int getHardRequestRate() {
        return getRequestRate(hardwareChannels);
    }

    public Set<Channel> getAppChannels() {
        return appChannels;
    }

    public Set<Channel> getHardwareChannels() {
        return hardwareChannels;
    }

    public void closeAll() {
        hardwareChannels.forEach(io.netty.channel.Channel::close);
        appChannels.forEach(io.netty.channel.Channel::close);
    }

}
