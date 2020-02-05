package cc.blynk.server.application.handlers.main.logic;

import cc.blynk.server.Holder;
import cc.blynk.server.application.handlers.main.auth.MobileStateHolder;
import cc.blynk.server.core.BlockingIOProcessor;
import cc.blynk.server.core.dao.UserDao;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.widgets.notifications.Notification;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.utils.StringUtils;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.internal.CommonByteBufUtil.notAllowed;
import static cc.blynk.server.internal.CommonByteBufUtil.ok;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
public final class MobileAddPushLogic {

    private static final Logger log = LogManager.getLogger(MobileAddPushLogic.class);

    private final BlockingIOProcessor blockingIOProcessor;
    private final UserDao userDao;

    public MobileAddPushLogic(Holder holder) {
        this.blockingIOProcessor = holder.blockingIOProcessor;
        this.userDao = holder.userDao;
    }

    public void messageReceived(ChannelHandlerContext ctx, MobileStateHolder state, StringMessage message) {
        String[] splitBody = StringUtils.split3(message.body);

        int dashId = Integer.parseInt(splitBody[0]);
        String uid = splitBody[1];
        String token = splitBody[2];

        User stateUser = state.user;
        DashBoard dash = stateUser.profile.getDashByIdOrThrow(dashId);

        Notification notification = dash.getNotificationWidget();

        if (notification == null) {
            log.error("No notification widget.");
            ctx.writeAndFlush(notAllowed(message.id), ctx.voidPromise());
            return;
        }

        switch (state.version.osType) {
            case ANDROID :
                notification.androidTokens.put(uid, token);
                break;
            case IOS :
                //this fix is only for iOS, because it doesn't have enough control
                //over the notifications, so we have to manage this on the server
                blockingIOProcessor.execute(() -> {
                    try {
                        for (User user : userDao.users.values()) {
                            if (user.equals(stateUser)) {
                                continue;
                            }
                            for (DashBoard dashBoard : user.profile.dashBoards) {
                                Notification tempNotifWidget = dashBoard.getNotificationWidget();
                                if (tempNotifWidget != null && tempNotifWidget != notification) {
                                    tempNotifWidget.iOSTokens.remove(uid);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Fail on ios token cleanup.", e);
                    }
                });
                notification.iOSTokens.put(uid, token);
                break;
        }

        ctx.writeAndFlush(ok(message.id), ctx.voidPromise());
    }
}
