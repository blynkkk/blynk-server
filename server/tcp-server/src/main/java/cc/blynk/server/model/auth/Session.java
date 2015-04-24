package cc.blynk.server.model.auth;

import cc.blynk.common.model.messages.MessageBase;
import cc.blynk.server.exceptions.UserAlreadyLoggedIn;
import io.netty.channel.Channel;
import io.netty.util.internal.ConcurrentSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;

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

    public final Set<Channel> appChannels = new ConcurrentSet<>();
    public final Set<Channel> hardwareChannels = new ConcurrentSet<>();

    public void sendMessageToHardware(Integer activeDashId, MessageBase message) {
        for (Channel channel : hardwareChannels) {
            Integer dashId = channel.attr(ChannelState.DASH_ID).get();
            if (dashId.equals(activeDashId)) {
                log.trace("Sending {} to {}", message, channel);
                channel.writeAndFlush(message);
            }
        }
    }

    public void addChannel(Channel channel, int msgId) {
        if (channel.attr(ChannelState.IS_HARD_CHANNEL).get()) {
            addChannel(hardwareChannels, channel, msgId);
        } else {
            addChannel(appChannels, channel, msgId);
        }
    }

    //todo not sure, but netty processes same channel in same thread, so no sync
    private void addChannel(Set<Channel> channelSet, Channel channel, int msgId) {
        //if login from same channel again - do not allow.
        if (channelSet.contains(channel)) {
            throw new UserAlreadyLoggedIn(msgId);
        }
        channelSet.add(channel);
    }

    public void pendMessageToHardware(MessageBase message) {
        for (Channel channel : hardwareChannels) {
            log.trace("Pending {} to {}", message, channel);
            channel.write(message);
        }
    }

    public void flushPendingMessagesToHardware() {
        for (Channel channel : hardwareChannels) {
            log.trace("Flush on {}", channel);
            channel.flush();
        }
    }

    public void sendMessageToHardware(MessageBase message) {
        for (Channel channel : hardwareChannels) {
            log.trace("Sending {} to {}", message, channel);
            channel.writeAndFlush(message);
        }
    }

    public void sendMessageToApp(MessageBase message) {
        for (Channel channel : appChannels) {
            log.trace("Sending {} to {}", message, channel);
            channel.writeAndFlush(message);
        }
    }

    public void remove(Channel channel) {
        if (channel.attr(ChannelState.IS_HARD_CHANNEL).get()) {
            hardwareChannels.remove(channel);
        } else {
            appChannels.remove(channel);
        }
    }

}
