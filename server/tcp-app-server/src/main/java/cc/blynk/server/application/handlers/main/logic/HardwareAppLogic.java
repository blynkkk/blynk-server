package cc.blynk.server.application.handlers.main.logic;

import cc.blynk.server.application.handlers.main.auth.AppStateHolder;
import cc.blynk.server.core.dao.SessionDao;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.Session;
import cc.blynk.server.core.model.widgets.Widget;
import cc.blynk.server.core.model.widgets.outputs.FrequencyWidget;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.server.core.protocol.model.messages.appllication.sharing.SyncMessage;
import cc.blynk.server.core.protocol.model.messages.common.HardwareMessage;
import cc.blynk.utils.ParseUtil;
import cc.blynk.utils.StringUtils;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for handling incoming hardware commands from applications and forwarding it to
 * appropriate hardware.
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
public class HardwareAppLogic {

    private static final Logger log = LogManager.getLogger(HardwareAppLogic.class);

    private final SessionDao sessionDao;

    public HardwareAppLogic(SessionDao sessionDao) {
        this.sessionDao = sessionDao;
    }

    public void messageReceived(ChannelHandlerContext ctx, AppStateHolder state, StringMessage message) {
        Session session = sessionDao.userSession.get(state.user);

        //if no active dashboards at all - do nothing. this could happen only in case of app. bug
        if (!state.user.hasActive()) {
            //throw new NoActiveDashboardException(message.id);
            return;
        }

        String[] split = message.body.split(StringUtils.BODY_SEPARATOR_STRING, 2);
        int dashId = ParseUtil.parseInt(split[0], message.id);

        char operation = split[1].charAt(1);

        DashBoard dash = state.user.profile.getDashById(dashId, message.id);

        switch (operation) {
            case 'm' :
                log.trace("Pin Mode message catch. Remembering.");
                //check PM command not empty
                if (split[1].length() > 3) {
                    dash.pinModeMessage = new HardwareMessage(message.id, split[1]);
                }
                session.sendMessageToHardware(ctx, dashId, new HardwareMessage(message.id, split[1]));
                break;
            case 'w' :
                dash.update(split[1], message.id);

                //if dash was shared. check for shared channels
                String sharedToken = state.user.dashShareTokens.get(dashId);
                session.sendToSharedApps(ctx, sharedToken, new SyncMessage(message.id, message.body));
                session.sendMessageToHardware(ctx, dashId, new HardwareMessage(message.id, split[1]));
                break;
            case 'r' :
            	String[] splitted = split[1].split(StringUtils.BODY_SEPARATOR_STRING);
                FrequencyWidget frequencyWidget = dash.findReadingWidget(splitted, message.id);
                if (frequencyWidget.isTicked(split[1])) {
                    session.sendMessageToHardware(ctx, dashId, new HardwareMessage(message.id, split[1]));
                } else {
                    final PinType type = PinType.getPingType(splitted[0].charAt(0));
                    final byte pin = ParseUtil.parseByte(splitted[1], message.id);
                	Widget widget = dash.findWidgetByPin(pin, type);
                	if (widget != null) {
                        session.sendMessageToHardware(ctx, dashId, new HardwareMessage(message.id, split[1]));
                	}
                }
                break;
        }
    }

}
