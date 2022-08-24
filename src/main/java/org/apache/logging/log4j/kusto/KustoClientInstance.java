package org.apache.logging.log4j.kusto;

import org.apache.http.HttpHost;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.status.StatusLogger;

import com.microsoft.azure.kusto.data.HttpClientProperties;
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder;
import com.microsoft.azure.kusto.ingest.IngestClient;
import com.microsoft.azure.kusto.ingest.IngestClientFactory;
import com.microsoft.azure.kusto.ingest.IngestionMapping;
import com.microsoft.azure.kusto.ingest.IngestionProperties;
import com.microsoft.azure.kusto.ingest.exceptions.IngestionClientException;
import com.microsoft.azure.kusto.ingest.exceptions.IngestionServiceException;
import com.microsoft.azure.kusto.ingest.result.IngestionResult;
import com.microsoft.azure.kusto.ingest.source.StreamSourceInfo;

import static com.microsoft.azure.kusto.ingest.IngestionMapping.IngestionMappingKind.CSV;
import static com.microsoft.azure.kusto.ingest.IngestionMapping.IngestionMappingKind.JSON;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import dev.failsafe.Failsafe;
import dev.failsafe.Fallback;
import dev.failsafe.RetryPolicy;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;

public final class KustoClientInstance {

    private static final Logger LOGGER = StatusLogger.getLogger();

    private static volatile KustoClientInstance instance;
    private final IngestClient ingestClient;
    private final IngestionProperties ingestionProperties;
    RetryPolicy<Object> ingestionRetryPolicy;

    private KustoClientInstance(KustoLog4jConfig kustoLog4jConfig) throws URISyntaxException {
        ingestionRetryPolicy = RetryPolicy.builder().handle(Throwable.class)
                .withBackoff(kustoLog4jConfig.backOffMinMinutes, kustoLog4jConfig.backOffMaxMinutes, ChronoUnit.MINUTES)
                .withMaxRetries(3)
                .build();
        ConnectionStringBuilder csb = "".equals(kustoLog4jConfig.appKey) || "".equals(kustoLog4jConfig.appTenant)
                ? ConnectionStringBuilder.createWithAadManagedIdentity(kustoLog4jConfig.clusterPath,
                        kustoLog4jConfig.appId)
                : ConnectionStringBuilder.createWithAadApplicationCredentials(kustoLog4jConfig.clusterPath,
                        kustoLog4jConfig.appId,
                        kustoLog4jConfig.appKey, kustoLog4jConfig.appTenant);
        if (kustoLog4jConfig.proxyUrl != null && !kustoLog4jConfig.proxyUrl.trim().equals("")) {
            HttpClientProperties proxy = HttpClientProperties.builder().proxy(HttpHost.create(kustoLog4jConfig.proxyUrl)).build();
            LOGGER.warn("Using proxy : {} ", kustoLog4jConfig.proxyUrl);
            ingestClient = IngestClientFactory.createClient(csb, proxy);
        } else {
            ingestClient = IngestClientFactory.createClient(csb);
        }
        LOGGER.info("Using database : {}  & table {} to ingest logs", kustoLog4jConfig.dbName,
                kustoLog4jConfig.tableName);
        ingestionProperties = new IngestionProperties(kustoLog4jConfig.dbName, kustoLog4jConfig.tableName);
        ingestionProperties.setFlushImmediately(kustoLog4jConfig.flushImmediately);
        if (kustoLog4jConfig.logTableMapping != null && kustoLog4jConfig.mappingType != null
                && !"".equals(kustoLog4jConfig.logTableMapping.trim()) &&
                !"".equals(kustoLog4jConfig.mappingType.trim())) {
            LOGGER.error("Using mapping {}  of type  {} ", kustoLog4jConfig.logTableMapping,
                    kustoLog4jConfig.mappingType);
            IngestionMapping.IngestionMappingKind mappingType = "json".equalsIgnoreCase(kustoLog4jConfig.mappingType) ? JSON : CSV;
            ingestionProperties.getIngestionMapping()
                    .setIngestionMappingReference(kustoLog4jConfig.logTableMapping, mappingType);
        }
    }

    static KustoClientInstance getInstance(KustoLog4jConfig kustoLog4jConfig) throws URISyntaxException {
        KustoClientInstance result = instance;
        if (result != null) {
            return result;
        }
        synchronized (KustoClientInstance.class) {
            if (instance == null) {
                instance = new KustoClientInstance(kustoLog4jConfig);
            }
            return instance;
        }
    }

    static KustoClientInstance getInstance() {
        return instance;
    }

    void ingestFile(String filePath) {
        Fallback<Object> fallback = Fallback.builder(() -> backOutFile(filePath)).withAsync().build();
        Failsafe.with(ingestionRetryPolicy, fallback)
                .onComplete(e -> {
                    if (e.getResult() != null) {
                        IngestionResult ingestionResult = (IngestionResult) e.getResult();
                        ingestionResult.getIngestionStatusCollection()
                                .forEach(ingestionStatus -> LOGGER.warn(
                                        "Ingestion status {} , Ingestion failure status {} , Ingestion error code {} ",
                                        ingestionStatus.getStatus(),
                                        ingestionStatus.getFailureStatus(), ingestionStatus.getErrorCode()));

                    } else if (e.getException() != null) {
                        backOutFile(filePath);
                    }
                })
                .get(() -> ingestLogs(filePath));

    }

    IngestionResult ingestLogs(String filePath) throws IngestionClientException, IngestionServiceException,
            IOException {
        try (InputStream inputStream = Files.newInputStream(Paths.get(filePath))) {
            StreamSourceInfo streamSourceInfo = new StreamSourceInfo(inputStream);
            return ingestClient.ingestFromStream(streamSourceInfo, ingestionProperties);
        }
    }

    void backOutFile(String filePath) {
        LOGGER.warn("Ingestion failed post retries for file {}. Attempting to move this file to backout", filePath);
        Path pathOfFile = Paths.get(filePath);
        String targetDirectory = String.format("%s%sbackout%s", pathOfFile.getParent(), File.separator, File.separator);
        String targetPath = String.format("%s%s", targetDirectory, pathOfFile.getFileName());
        try {
            Files.createDirectories(Paths.get(targetDirectory));
            Files.move(pathOfFile, Paths.get(targetPath), REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Ingestion failed post retries for file {}. Backout failed for the file to path {}", filePath,
                    targetPath, e);
        }
    }

    void close() {
        try {
            ingestClient.close();
        } catch (IOException e) {
            LOGGER.warn("Closing ingest client caused an error.", e);
        }
    }
}
