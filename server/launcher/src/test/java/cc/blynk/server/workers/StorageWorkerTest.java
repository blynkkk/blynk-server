package cc.blynk.server.workers;

import cc.blynk.server.core.BlockingIOProcessor;
import cc.blynk.server.core.dao.ReportingDao;
import cc.blynk.server.core.model.enums.GraphType;
import cc.blynk.server.core.model.enums.PinType;
import cc.blynk.server.core.reporting.average.AggregationKey;
import cc.blynk.server.core.reporting.average.AggregationValue;
import cc.blynk.server.core.reporting.average.AverageAggregator;
import cc.blynk.server.core.reporting.elastic.ElasticClient;
import cc.blynk.server.db.DBManager;
import cc.blynk.utils.ReportingUtil;
import cc.blynk.utils.ServerProperties;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import static cc.blynk.server.core.dao.ReportingDao.*;
import static cc.blynk.utils.ReportingUtil.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 11.08.15.
 */
@RunWith(MockitoJUnitRunner.class)
public class StorageWorkerTest {

    private final String reportingFolder = getReportingFolder(System.getProperty("java.io.tmpdir"));

    @Mock
    public AverageAggregator averageAggregator;

    @Mock
    public ServerProperties properties;

    @Mock
    private ElasticClient elasticClient;

    private BlockingIOProcessor blockingIOProcessor;

    @Before
    public void cleanup() throws IOException {
        blockingIOProcessor = new BlockingIOProcessor(1, 1, null);
        Path dataFolder1 = Paths.get(reportingFolder, "test");
        FileUtils.deleteDirectory(dataFolder1.toFile());
        ReportingUtil.createReportingFolder(reportingFolder, "test");

        Path dataFolder2 = Paths.get(reportingFolder, "test2");
        FileUtils.deleteDirectory(dataFolder2.toFile());
        ReportingUtil.createReportingFolder(reportingFolder, "test2");
    }

    @Test
    public void testStore() throws IOException {
        StorageWorker storageWorker = new StorageWorker(averageAggregator, reportingFolder, new DBManager(blockingIOProcessor), elasticClient);

        ConcurrentHashMap<AggregationKey, AggregationValue> map = new ConcurrentHashMap<>();

        long ts = getTS() / AverageAggregator.HOUR;

        AggregationKey aggregationKey = new AggregationKey("test", 1, PinType.ANALOG, (byte) 1, ts);
        AggregationValue aggregationValue = new AggregationValue();
        aggregationValue.update(100);
        AggregationKey aggregationKey2 = new AggregationKey("test", 1, PinType.ANALOG, (byte) 1, ts - 1);
        AggregationValue aggregationValue2 = new AggregationValue();
        aggregationValue2.update(150.54);
        AggregationKey aggregationKey3 = new AggregationKey("test2", 2, PinType.ANALOG, (byte) 2, ts);
        AggregationValue aggregationValue3 = new AggregationValue();
        aggregationValue3.update(200);

        map.put(aggregationKey, aggregationValue);
        map.put(aggregationKey2, aggregationValue2);
        map.put(aggregationKey3, aggregationValue3);

        when(averageAggregator.getMinute()).thenReturn(new ConcurrentHashMap<>());
        when(averageAggregator.getHourly()).thenReturn(map);
        when(averageAggregator.getDaily()).thenReturn(new ConcurrentHashMap<>());

        storageWorker.run();

        assertTrue(Files.exists(Paths.get(reportingFolder, "test", generateFilename(1, PinType.ANALOG, (byte) 1, GraphType.HOURLY))));
        assertTrue(Files.exists(Paths.get(reportingFolder, "test2", generateFilename(2, PinType.ANALOG, (byte) 2, GraphType.HOURLY))));

        ByteBuffer data = ReportingDao.getByteBufferFromDisk(reportingFolder, "test", 1, PinType.ANALOG, (byte) 1, 2, GraphType.HOURLY);
        assertNotNull(data);
        data.flip();
        assertEquals(32, data.capacity());

        assertEquals(150.54, data.getDouble(), 0.001);
        assertEquals((ts - 1) * AverageAggregator.HOUR, data.getLong());

        assertEquals(100.0, data.getDouble(), 0.001);
        assertEquals(ts * AverageAggregator.HOUR, data.getLong());

        data = ReportingDao.getByteBufferFromDisk(reportingFolder, "test2", 2, PinType.ANALOG, (byte) 2, 1, GraphType.HOURLY);
        assertNotNull(data);
        data.flip();
        assertEquals(16, data.capacity());
        assertEquals(200.0, data.getDouble(), 0.001);
        assertEquals(ts * AverageAggregator.HOUR, data.getLong());
        verify(elasticClient, times(3)).write(any(), any());
    }

    @Test
    public void testStore2() throws IOException {
        StorageWorker storageWorker = new StorageWorker(averageAggregator, reportingFolder, new DBManager(blockingIOProcessor), elasticClient);

        ConcurrentHashMap<AggregationKey, AggregationValue> map = new ConcurrentHashMap<>();

        long ts = getTS() / AverageAggregator.HOUR;

        AggregationKey aggregationKey = new AggregationKey("test", 1, PinType.ANALOG, (byte) 1, ts);
        AggregationValue aggregationValue = new AggregationValue();
        aggregationValue.update(100);
        AggregationKey aggregationKey2 = new AggregationKey("test", 1, PinType.ANALOG, (byte) 1, ts - 1);
        AggregationValue aggregationValue2 = new AggregationValue();
        aggregationValue2.update(150.54);
        AggregationKey aggregationKey3 = new AggregationKey("test", 1, PinType.ANALOG, (byte) 1, ts - 2);
        AggregationValue aggregationValue3 = new AggregationValue();
        aggregationValue3.update(200);

        map.put(aggregationKey, aggregationValue);
        map.put(aggregationKey2, aggregationValue2);
        map.put(aggregationKey3, aggregationValue3);

        when(averageAggregator.getMinute()).thenReturn(new ConcurrentHashMap<>());
        when(averageAggregator.getHourly()).thenReturn(map);
        when(averageAggregator.getDaily()).thenReturn(new ConcurrentHashMap<>());

        storageWorker.run();

        assertTrue(Files.exists(Paths.get(reportingFolder, "test", generateFilename(1, PinType.ANALOG, (byte) 1, GraphType.HOURLY))));

        //take less
        ByteBuffer data = ReportingDao.getByteBufferFromDisk(reportingFolder, "test", 1, PinType.ANALOG, (byte) 1, 1, GraphType.HOURLY);
        assertNotNull(data);
        data.flip();
        assertEquals(16, data.capacity());

        assertEquals(100.0, data.getDouble(), 0.001);
        assertEquals(ts * AverageAggregator.HOUR, data.getLong());


        //take more
        data = ReportingDao.getByteBufferFromDisk(reportingFolder, "test", 1, PinType.ANALOG, (byte) 1, 24, GraphType.HOURLY);
        assertNotNull(data);
        data.flip();
        assertEquals(48, data.capacity());

        assertEquals(200.0, data.getDouble(), 0.001);
        assertEquals((ts - 2) * AverageAggregator.HOUR, data.getLong());

        assertEquals(150.54, data.getDouble(), 0.001);
        assertEquals((ts - 1) * AverageAggregator.HOUR, data.getLong());

        assertEquals(100.0, data.getDouble(), 0.001);
        assertEquals(ts * AverageAggregator.HOUR, data.getLong());

        verify(elasticClient, times(3)).write(any(), any());
    }


    @Test
    public void testDeleteCommand() throws IOException {
        StorageWorker storageWorker = new StorageWorker(averageAggregator, reportingFolder, new DBManager(blockingIOProcessor), elasticClient);

        ConcurrentHashMap<AggregationKey, AggregationValue> map = new ConcurrentHashMap<>();

        long ts = getTS() / AverageAggregator.HOUR;

        AggregationKey aggregationKey = new AggregationKey("test", 1, PinType.ANALOG, (byte) 1, ts);
        AggregationValue aggregationValue = new AggregationValue();
        aggregationValue.update(100);
        AggregationKey aggregationKey2 = new AggregationKey("test", 1, PinType.ANALOG, (byte) 1, ts - 1);
        AggregationValue aggregationValue2 = new AggregationValue();
        aggregationValue2.update(150.54);
        AggregationKey aggregationKey3 = new AggregationKey("test2", 2, PinType.ANALOG, (byte) 2, ts);
        AggregationValue aggregationValue3 = new AggregationValue();
        aggregationValue3.update(200);

        map.put(aggregationKey, aggregationValue);
        map.put(aggregationKey2, aggregationValue2);
        map.put(aggregationKey3, aggregationValue3);

        when(averageAggregator.getMinute()).thenReturn(new ConcurrentHashMap<>());
        when(averageAggregator.getHourly()).thenReturn(map);
        when(averageAggregator.getDaily()).thenReturn(new ConcurrentHashMap<>());
        when(properties.getProperty("data.folder")).thenReturn(System.getProperty("java.io.tmpdir"));

        storageWorker.run();

        assertTrue(Files.exists(Paths.get(reportingFolder, "test", generateFilename(1, PinType.ANALOG, (byte) 1, GraphType.HOURLY))));
        assertTrue(Files.exists(Paths.get(reportingFolder, "test2", generateFilename(2, PinType.ANALOG, (byte) 2, GraphType.HOURLY))));

        new ReportingDao(reportingFolder, null, properties).delete("test", 1, PinType.ANALOG, (byte) 1);
        assertFalse(Files.exists(Paths.get(reportingFolder, "test", generateFilename(1, PinType.ANALOG, (byte) 1, GraphType.HOURLY))));

        verify(elasticClient, times(3)).write(any(), any());
    }

    private long getTS() {
        SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss");
        String dateInString = "Aug 10, 2015 12:10:56";

        try {

            Date date = formatter.parse(dateInString);
            return date.getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return 0;
    }

}
