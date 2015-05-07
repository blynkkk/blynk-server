package cc.blynk.server.handlers.common;

import cc.blynk.common.model.messages.protocol.HardwareMessage;
import cc.blynk.common.utils.ServerProperties;
import cc.blynk.server.dao.GraphInMemoryStorage;
import cc.blynk.server.dao.SessionsHolder;
import cc.blynk.server.dao.Storage;
import cc.blynk.server.dao.UserRegistry;
import cc.blynk.server.exceptions.DeviceNotInNetworkException;
import cc.blynk.server.exceptions.NoActiveDashboardException;
import cc.blynk.server.handlers.BaseSimpleChannelInboundHandler;
import cc.blynk.server.model.auth.ChannelState;
import cc.blynk.server.model.auth.Session;
import cc.blynk.server.model.auth.User;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
@ChannelHandler.Sharable
public class HardwareHandler extends BaseSimpleChannelInboundHandler<HardwareMessage> {

    private final Storage storage;

    public HardwareHandler(ServerProperties props, UserRegistry userRegistry, SessionsHolder sessionsHolder) {
        super(props, userRegistry, sessionsHolder);
        this.storage = new GraphInMemoryStorage(props.getIntProperty("user.in.memory.storage.limit"));
    }

    private static boolean pinModeMessage(String body) {
        return body.length() > 0 && body.charAt(0) == 'p';
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, User user, HardwareMessage message) {
        Session session = sessionsHolder.userSession.get(user);

        if (ctx.channel().attr(ChannelState.IS_HARD_CHANNEL).get()) {
            //if message from hardware, check if it belongs to graph. so we need save it in that case
            String body = storage.store(user, ctx.channel().attr(ChannelState.DASH_ID).get(), message.body, message.id);
            if (session.appChannels.size() > 0) {
                session.sendMessageToApp(message.updateMessageBody(body));
            }
        } else {
            if (user.getProfile().getActiveDashId() == null) {
                throw new NoActiveDashboardException(message.id);
            }

            if (pinModeMessage(message.body)) {
                log.trace("No device and Pin Mode message catch. Remembering.");
                user.getProfile().setPinModeMessage(message);
            }

            if (session.hardwareChannels.size() == 0) {
                throw new DeviceNotInNetworkException(message.id);
            }

            session.sendMessageToHardware(user.getProfile().getActiveDashId(), message);
        }

    }

}
