package com.marklogic.kafka.connect.source;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.datamovement.DataMovementManager;
import com.marklogic.client.datamovement.ExportListener;
import com.marklogic.client.datamovement.QueryBatcher;
import com.marklogic.client.impl.StringQueryDefinitionImpl;
import com.marklogic.client.io.StringHandle;
import com.marklogic.client.query.StringQueryDefinition;
import com.marklogic.kafka.connect.DefaultDatabaseClientCreator;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MarkLogicSourceTask extends SourceTask {

    private static final Logger logger = LoggerFactory.getLogger(MarkLogicSourceTask.class);

    private Map<String, String> config;
    private String topic;
    private String query;

    private DatabaseClient databaseClient;
    private DataMovementManager dataMovementManager;
    private QueryBatcher queryBatcher;

    private ArrayList<SourceRecord> records = new ArrayList<>();

    @Override
    public String version() {
        return MarkLogicSourceConnector.MARKLOGIC_SOURCE_CONNECTOR_VERSION;
    }

    @Override
    public void start(Map<String, String> props) {
        logger.info("MarkLogicSourceTask Starting");

        config = props;
        topic = config.get(MarkLogicSourceConfig.KAFKA_TOPIC);
        query = config.get(MarkLogicSourceConfig.QUERY);

        databaseClient = new DefaultDatabaseClientCreator().createDatabaseClient(config);
        dataMovementManager = databaseClient.newDataMovementManager();
        final StringQueryDefinition stringQueryDefinition = databaseClient.newQueryManager().newStringDefinition();
        stringQueryDefinition.setCriteria(query);
        queryBatcher = dataMovementManager.newQueryBatcher(stringQueryDefinition)
                .onUrisReady(getExportListener())
                .onQueryFailure(Throwable::printStackTrace);

        logger.info("MarkLogicSourceTask Started");
    }

    private ExportListener getExportListener() {
        return new ExportListener()
                .onDocumentReady(doc -> {
                    String documentContent = doc.getContent(new StringHandle()).get();
                    Map<String, String> sourcePartition = Collections.singletonMap("filename", doc.getUri());
                    Map<String, Integer> sourceOffset = Collections.singletonMap("position", 1);
                    records.add(new SourceRecord(sourcePartition, sourceOffset, topic, Schema.STRING_SCHEMA, documentContent));
                });
    }


    @Override
    public List<SourceRecord> poll() throws InterruptedException {
        records = new ArrayList<>();
        dataMovementManager.startJob(queryBatcher);
        queryBatcher.awaitCompletion();
        return records;
    }

    @Override
    public void stop() {
        dataMovementManager.stopJob(queryBatcher);
    }
}