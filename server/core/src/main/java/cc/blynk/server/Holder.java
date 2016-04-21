package cc.blynk.server;

import cc.blynk.server.core.BlockingIOProcessor;
import cc.blynk.server.core.batch.FlushWritesTaskHolder;
import cc.blynk.server.core.batch.GatheringReadsHandler;
import cc.blynk.server.core.batch.GatheringWritesHandler;
import cc.blynk.server.core.dao.FileManager;
import cc.blynk.server.core.dao.ReportingDao;
import cc.blynk.server.core.dao.SessionDao;
import cc.blynk.server.core.dao.UserDao;
import cc.blynk.server.core.reporting.average.AverageAggregator;
import cc.blynk.server.core.stats.GlobalStats;
import cc.blynk.server.db.DBManager;
import cc.blynk.server.notifications.mail.MailWrapper;
import cc.blynk.server.notifications.push.GCMWrapper;
import cc.blynk.server.notifications.sms.SMSWrapper;
import cc.blynk.server.notifications.twitter.TwitterWrapper;
import cc.blynk.server.workers.ProfileSaverWorker;
import cc.blynk.utils.FileLoaderUtil;
import cc.blynk.utils.ServerProperties;
import io.netty.channel.ChannelHandler;

import static cc.blynk.utils.ReportingUtil.getReportingFolder;

/**
 * Just a holder for all necessary objects for server instance creation.
 * <p>
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 28.09.15.
 */
public class Holder {

    public final FileManager fileManager;

    public final SessionDao sessionDao;

    public final UserDao userDao;

    public final ReportingDao reportingDao;

    public final DBManager dbManager;

    public final GlobalStats stats;

    public final ServerProperties props;

    public final AverageAggregator averageAggregator;

    public final BlockingIOProcessor blockingIOProcessor;

    public ProfileSaverWorker profileSaverWorker;

    public final TwitterWrapper twitterWrapper;
    public final MailWrapper mailWrapper;
    public final GCMWrapper gcmWrapper;
    public final SMSWrapper smsWrapper;

    @SuppressWarnings("FieldCanBeLocal")
    private FlushWritesTaskHolder flushWritesTaskHolder;
    public ChannelHandler gatheringReadsHandler;
    public ChannelHandler gatheringWritesHandler;

    public Holder(ServerProperties serverProperties) {
        this.props = serverProperties;

        String dataFolder = serverProperties.getProperty("data.folder");

        this.fileManager = new FileManager(dataFolder);
        this.sessionDao = new SessionDao();
        this.userDao = new UserDao(fileManager.deserialize());
        this.stats = new GlobalStats();
        final String reportingFolder = getReportingFolder(dataFolder);
        this.averageAggregator = new AverageAggregator(reportingFolder);
        this.reportingDao = new ReportingDao(reportingFolder, averageAggregator, serverProperties);

        this.twitterWrapper = new TwitterWrapper();
        this.mailWrapper = new MailWrapper(new ServerProperties(MailWrapper.MAIL_PROPERTIES_FILENAME));
        this.gcmWrapper = new GCMWrapper(new ServerProperties(GCMWrapper.GCM_PROPERTIES_FILENAME));
        this.smsWrapper = new SMSWrapper(new ServerProperties(SMSWrapper.SMS_PROPERTIES_FILENAME));

        this.blockingIOProcessor = new BlockingIOProcessor(
                serverProperties.getIntProperty("blocking.processor.thread.pool.limit", 5),
                serverProperties.getIntProperty("notifications.queue.limit", 10000),
                FileLoaderUtil.readFileAsString(BlockingIOProcessor.TOKEN_MAIL_BODY)
        );

        this.dbManager = new DBManager(blockingIOProcessor);

        setupBatching(serverProperties.getBoolProperty("enable.channel.batchWrites"));
    }

    //for tests only
    public Holder(ServerProperties serverProperties, TwitterWrapper twitterWrapper, MailWrapper mailWrapper, GCMWrapper gcmWrapper, SMSWrapper smsWrapper) {
        this.props = serverProperties;

        String dataFolder = serverProperties.getProperty("data.folder");

        this.fileManager = new FileManager(dataFolder);
        this.sessionDao = new SessionDao();
        this.userDao = new UserDao(fileManager.deserialize());
        this.stats = new GlobalStats();
        final String reportingFolder = getReportingFolder(dataFolder);
        this.averageAggregator = new AverageAggregator(reportingFolder);
        this.reportingDao = new ReportingDao(reportingFolder, averageAggregator, serverProperties);

        this.twitterWrapper = twitterWrapper;
        this.mailWrapper = mailWrapper;
        this.gcmWrapper = gcmWrapper;
        this.smsWrapper = smsWrapper;

        this.blockingIOProcessor = new BlockingIOProcessor(
                serverProperties.getIntProperty("blocking.processor.thread.pool.limit", 5),
                serverProperties.getIntProperty("notifications.queue.limit", 10000),
                FileLoaderUtil.readFileAsString(BlockingIOProcessor.TOKEN_MAIL_BODY)
        );

        this.dbManager = new DBManager(blockingIOProcessor);

        setupBatching(serverProperties.getBoolProperty("enable.channel.batchWrites"));
    }

    private void setupBatching(boolean batchWritesEnabled) {
        flushWritesTaskHolder = new FlushWritesTaskHolder();
        gatheringReadsHandler = new GatheringReadsHandler(flushWritesTaskHolder, batchWritesEnabled);
        gatheringWritesHandler = new GatheringWritesHandler(flushWritesTaskHolder, batchWritesEnabled);
    }
}
