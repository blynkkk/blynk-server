package cc.blynk.server.core.reporting.elastic;

import cc.blynk.server.core.model.enums.GraphType;
import cc.blynk.server.core.reporting.average.AggregationKey;
import cc.blynk.server.core.reporting.average.AggregationValue;
import cc.blynk.utils.JsonParser;
import cc.blynk.utils.ServerProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * @author gig.
 */
public class ElasticClient {

    public static final String ELASTIC_PROPERTIES = "elastic.properties";
    private TransportClient client;

    private static final Logger log = LogManager.getLogger(ElasticClient.class);

    public ElasticClient(ServerProperties props) {
        if (!props.getBoolProperty("elastic.enabled")) {
            return;
        }
        final String host = props.getProperty("elastic.host");
        final int port = props.getIntProperty("elastic.port");

        try {
            Settings settings = Settings.builder()
                    .put("cluster.name", props.getProperty("elastic.cluster.name")).build();
            this.client = new PreBuiltTransportClient(settings)
                    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port));
        } catch (UnknownHostException e) {
            log.error("Failed to init elastic client: {}", e);
        }

        log.info("Elastic client successfully initialized on {}:{}", host, port);
    }

    public void write(Map<AggregationKey, AggregationValue> data, GraphType graphType) {
        if (data == null || data.isEmpty()) {
            return;
        }

        final BulkRequestBuilder bulkRequest = client.prepareBulk();

        data.forEach((key, value) -> {
            final Map keyMap = JsonParser.mapper.convertValue(key, Map.class);
            final Map valueMap = JsonParser.mapper.convertValue(value, Map.class);
            valueMap.putAll(keyMap);
            bulkRequest.add(client.prepareIndex("blynk", graphType.name()).setSource(valueMap));
        });

        BulkResponse bulkResponse = bulkRequest.get();
        if (bulkResponse.hasFailures()) {
            log.error("Failed writing to elasticsearch: {}", bulkResponse.buildFailureMessage());
        }
    }
}
