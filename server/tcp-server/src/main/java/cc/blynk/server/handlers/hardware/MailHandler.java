package cc.blynk.server.handlers.hardware;

import cc.blynk.common.model.messages.protocol.hardware.MailMessage;
import cc.blynk.common.utils.ServerProperties;
import cc.blynk.server.dao.SessionsHolder;
import cc.blynk.server.dao.UserRegistry;
import cc.blynk.server.exceptions.IllegalCommandException;
import cc.blynk.server.exceptions.NotAllowedException;
import cc.blynk.server.exceptions.QuotaLimitException;
import cc.blynk.server.handlers.BaseSimpleChannelInboundHandler;
import cc.blynk.server.model.auth.User;
import cc.blynk.server.model.widgets.others.Mail;
import cc.blynk.server.workers.notifications.NotificationsProcessor;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static cc.blynk.common.enums.Response.OK;
import static cc.blynk.common.model.messages.MessageFactory.produce;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
@ChannelHandler.Sharable
public class MailHandler extends BaseSimpleChannelInboundHandler<MailMessage> {

	private final long defaultMailQuotaLimit;
    private final NotificationsProcessor notificationsProcessor;

    public MailHandler(ServerProperties props, UserRegistry userRegistry, SessionsHolder sessionsHolder,
                       NotificationsProcessor notificationsProcessor) {
        super(props, userRegistry, sessionsHolder);
        this.notificationsProcessor = notificationsProcessor;
		defaultMailQuotaLimit = props.getLongProperty("email.notifications.user.quota.limit") * 1000;
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, User user, MailMessage message) {
        Mail mail = user.getProfile().getActiveDashboardEmailWidget();

        if (mail == null) {
            throw new NotAllowedException("User has no mail widget or active dashboard.", message.id);
        }

        if (message.body.equals("") && (mail.to == null || mail.to.equals(""))) {
            throw new IllegalCommandException("Invalid mail notification body.", message.id);
        }

        String[] bodyParts = message.body.split("\0");

        if (bodyParts.length != 3 && (mail.to == null || mail.to.equals(""))) {
            throw new IllegalCommandException("Invalid mail notification body.", message.id);
        }

        String to;
        String subj;
        String body;

        switch (bodyParts.length) {
            case 1 :
                to = mail.to;
                subj = mail.subj;
                body = bodyParts[0].equals("") ? mail.body : bodyParts[0];
                break;
            case 2 :
                to = mail.to;
                subj = bodyParts[0];
                body = bodyParts[1];
                break;
            case 3 :
                to = bodyParts[0];
                subj = bodyParts[1];
                body = bodyParts[2];
                break;
            default :
                throw new IllegalCommandException("Invalid mail notification body.", message.id);

        }

        log.trace("Sending Mail for user {}, with message : '{}'.", user.getName(), message.body);
        notificationsProcessor.mail(to, subj, body, message.id);

		final long currentTs = System.currentTimeMillis();
		final long timePassedSinceLastMessage = (currentTs - user.getLastEmailSentTs());
		if(timePassedSinceLastMessage < defaultMailQuotaLimit) {
			throw new QuotaLimitException(String.format("Only 1 mail per %s seconds is allowed", defaultMailQuotaLimit), message.id);
		}
		user.setLastEmailSentTs(currentTs);
		ctx.writeAndFlush(produce(message.id, OK));

    }

}
