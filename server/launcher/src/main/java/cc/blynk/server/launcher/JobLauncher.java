package cc.blynk.server.launcher;

import cc.blynk.server.Holder;
import cc.blynk.server.core.BaseServer;
import cc.blynk.server.core.reporting.average.AverageAggregator;
import cc.blynk.server.workers.ShutdownHookWorker;
import cc.blynk.server.workers.StatsWorker;
import cc.blynk.server.workers.StorageWorker;
import cc.blynk.server.workers.timer.TimerWorker;
import cc.blynk.utils.ReportingUtil;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Launches a bunch of separate jobs/schedulers responsible for different aspects of business logic
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 28.09.15.
 */
class JobLauncher {

    public static void start(Holder holder, BaseServer[] servers) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        long startDelay;

        StorageWorker storageWorker = new StorageWorker(
                holder.averageAggregator,
                ReportingUtil.getReportingFolder(holder.props.getProperty("data.folder")),
                holder.dbManager,
                holder.elasticClient
        );

        //to start at the beggining of an minute
        startDelay = AverageAggregator.MINUTE - (System.currentTimeMillis() % AverageAggregator.MINUTE);
        scheduler.scheduleAtFixedRate(storageWorker, startDelay, AverageAggregator.MINUTE, TimeUnit.MILLISECONDS);

        scheduler.scheduleAtFixedRate(holder.profileSaverWorker, 1000,
                holder.props.getIntProperty("profile.save.worker.period"), TimeUnit.MILLISECONDS);

        StatsWorker statsWorker = new StatsWorker(holder.stats, holder.sessionDao, holder.userDao);
        scheduler.scheduleAtFixedRate(statsWorker, 1000,
                holder.props.getIntProperty("stats.print.worker.period"), TimeUnit.MILLISECONDS);

        //millis we need to wait to start scheduler at the beginning of a second.
        startDelay = 1000 - (System.currentTimeMillis() % 1000);

        //separate thread for timer.
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(
                new TimerWorker(holder.userDao, holder.sessionDao), startDelay, 1000, TimeUnit.MILLISECONDS);

        //shutdown hook thread catcher
        Runtime.getRuntime().addShutdownHook(new Thread(
                new ShutdownHookWorker(servers, holder, scheduler, holder.profileSaverWorker)
        ));
    }


}
