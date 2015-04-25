package cc.blynk.server.handlers.hardware;

import cc.blynk.common.enums.Command;
import cc.blynk.common.model.messages.MessageFactory;
import cc.blynk.common.model.messages.protocol.hardware.MailMessage;
import cc.blynk.common.utils.ServerProperties;
import cc.blynk.server.TestBase;
import cc.blynk.server.exceptions.IllegalCommandException;
import cc.blynk.server.exceptions.NotAllowedException;
import cc.blynk.server.model.Profile;
import cc.blynk.server.model.auth.User;
import cc.blynk.server.model.widgets.others.Mail;
import cc.blynk.server.workers.notifications.NotificationsProcessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 07.04.15.
 */
@RunWith(MockitoJUnitRunner.class)
public class MailHandlerTest extends TestBase {

    @Mock
    private NotificationsProcessor notificationsProcessor;

    @InjectMocks
    private MailHandler mailHandler;

	@Mock
	private ChannelHandlerContext ctx;

    @Mock
    private User user;

	@Mock
	ServerProperties serverProperties;

    @Mock
    private Profile profile;

    @Mock
    private Channel channel;

	@Before
	public void before() {
		props = new ServerProperties();
	}

    @Test(expected = NotAllowedException.class)
	public void testNoEmailWidget() throws InterruptedException {
		MailMessage mailMessage = (MailMessage) MessageFactory.produce(1, Command.EMAIL, "body");

        when(user.getProfile()).thenReturn(profile);
        when(profile.getActiveDashboardEmailWidget()).thenReturn(null);

        mailHandler.messageReceived(ctx, user, mailMessage);
    }

    @Test(expected = IllegalCommandException.class)
	public void testNoToBody() throws InterruptedException {
		MailMessage mailMessage = (MailMessage) MessageFactory.produce(1, Command.EMAIL, "".replaceAll(" ", "\0"));

        when(user.getProfile()).thenReturn(profile);
        Mail mail = new Mail();
        when(profile.getActiveDashboardEmailWidget()).thenReturn(mail);

        mailHandler.messageReceived(ctx, user, mailMessage);
    }

    @Test(expected = IllegalCommandException.class)
	public void testNoBody() throws InterruptedException {
		MailMessage mailMessage = (MailMessage) MessageFactory.produce(1, Command.EMAIL, "body".replaceAll(" ", "\0"));

        when(user.getProfile()).thenReturn(profile);
        when(profile.getActiveDashboardEmailWidget()).thenReturn(new Mail());

        mailHandler.messageReceived(ctx, user, mailMessage);
    }

    @Test
	public void sendEmptyBodyMailYoUseDefaults() throws InterruptedException {
		MailMessage mailMessage = (MailMessage) MessageFactory.produce(1, Command.EMAIL, "");

        when(user.getProfile()).thenReturn(profile);
        Mail mail = new Mail("me@example.com", "Yo", "MyBody");
        when(profile.getActiveDashboardEmailWidget()).thenReturn(mail);

        when(ctx.channel()).thenReturn(channel);

        mailHandler.messageReceived(ctx, user, mailMessage);
        verify(notificationsProcessor).mail(eq("me@example.com"), eq("Yo"), eq("MyBody"), eq(1));
        verify(ctx).writeAndFlush(any());
    }

    @Test
	public void sendEmptyBodyMailYoUseDefaultsExceptBody() throws InterruptedException {
		MailMessage mailMessage = (MailMessage) MessageFactory.produce(1, Command.EMAIL, "body".replaceAll(" ", "\0"));

        when(user.getProfile()).thenReturn(profile);
        Mail mail = new Mail("me@example.com", "Yo", "MyBody");
        when(profile.getActiveDashboardEmailWidget()).thenReturn(mail);

        when(ctx.channel()).thenReturn(channel);

        mailHandler.messageReceived(ctx, user, mailMessage);
        verify(notificationsProcessor).mail(eq("me@example.com"), eq("Yo"), eq("body"), eq(1));
        verify(ctx).writeAndFlush(any());
    }

    @Test
	public void sendEmptyBodyMailYoUseDefaultsExceptBodyAndSubj() throws InterruptedException {
		MailMessage mailMessage = (MailMessage) MessageFactory.produce(1, Command.EMAIL, "subj body".replaceAll(" ", "\0"));

        when(user.getProfile()).thenReturn(profile);
        Mail mail = new Mail("me@example.com", "Yo", "MyBody");
        when(profile.getActiveDashboardEmailWidget()).thenReturn(mail);

        when(ctx.channel()).thenReturn(channel);

        mailHandler.messageReceived(ctx, user, mailMessage);
        verify(notificationsProcessor).mail(eq("me@example.com"), eq("subj"), eq("body"), eq(1));
        verify(ctx).writeAndFlush(any());
    }

    @Test
	public void sendEmptyBodyMailYoNoDefaults() throws InterruptedException {
		MailMessage mailMessage = (MailMessage) MessageFactory.produce(1, Command.EMAIL, "pupkin@example.com subj body".replaceAll(" ", "\0"));

        when(user.getProfile()).thenReturn(profile);
        Mail mail = new Mail("me@example.com", "Yo", "MyBody");
        when(profile.getActiveDashboardEmailWidget()).thenReturn(mail);

        when(ctx.channel()).thenReturn(channel);

        mailHandler.messageReceived(ctx, user, mailMessage);
        verify(notificationsProcessor).mail(eq("pupkin@example.com"), eq("subj"), eq("body"), eq(1));
        verify(ctx).writeAndFlush(any());
    }

	@Test
	public void testSendQuotaLimitationIsWorking() throws InterruptedException {
		final int defaultMessageQuotaLimit = props.getIntProperty("email.notifications.user.quota.limit");
		MailMessage mailMessage1 = (MailMessage) MessageFactory.produce(1, Command.EMAIL, "pupkin@example.com subj body".replaceAll(" ", "\0"));
		MailMessage mailMessage2 = (MailMessage) MessageFactory.produce(2, Command.EMAIL, "pupkin2@example.com subj2 body2".replaceAll(" ", "\0"));

		User user = spy(new User());
		when(user.getProfile()).thenReturn(profile);
		Mail mail = new Mail("me@example.com", "Yo", "MyBody");
		when(profile.getActiveDashboardEmailWidget()).thenReturn(mail);

		when(ctx.channel()).thenReturn(channel);

		mailHandler.messageReceived(ctx, user, mailMessage1);
		final long message1Ts = System.currentTimeMillis();
		mailHandler.messageReceived(ctx, user, mailMessage2);
		final long message2Ts = System.currentTimeMillis();
		Assert.assertTrue((message2Ts - message1Ts) / 1000 >= defaultMessageQuotaLimit);
	}

}
