// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.azure.kusto.log4j;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.status.StatusLogger;

import com.microsoft.azure.kusto.data.HttpClientProperties;
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder;
import com.microsoft.azure.kusto.data.exceptions.KustoDataExceptionBase;
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

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A singleton class that does all the work to ingest data. It instantiates the Ingest client and all operations that
 * are performed from the perspective of ingestion. Uses an external library for performing queued ingestion retries
 * and falls back in case of retries are exhausted to write data to a backout directory where the data can be manually
 * uploaded in case of transient failures manually. To avoid configuration where too many retries can be configured,
 * the retry attempts have been "hard-limited" (opinionated view) at 3 by coding it in the application. The time gap
 * between retries are however configurable.
 */
public final class KustoClientInstance {

    private static final Logger LOGGER = StatusLogger.getLogger();
    private static final AtomicReference<KustoClientInstance> ATOMIC_INSTANCE = new AtomicReference<>();
    private static final RetryRegistry RETRY_REGISTRY = RetryRegistry.ofDefaults();

    static final String INGESTION_RETRIES = "INGESTION_RETRIES";

    private final IngestClient ingestClient;
    private final IngestionProperties ingestionProperties;
    Retry ingestionRetry;

    private KustoClientInstance(KustoLog4jConfig kustoLog4jConfig) throws URISyntaxException {
        // default max attempts is 3 !
        RetryConfig retryConfig = RetryConfig.custom()
                .intervalFunction(IntervalFunction.ofExponentialBackoff(kustoLog4jConfig.backOffMinSeconds,
                        IntervalFunction.DEFAULT_MULTIPLIER, kustoLog4jConfig.backOffMaxSeconds))
                .retryOnException(this::isTransientException)
                .failAfterMaxAttempts(false)
                .build();
        ingestionRetry = RETRY_REGISTRY.retry(INGESTION_RETRIES, retryConfig);
        boolean useManagedIdentity = StringUtils.isBlank(kustoLog4jConfig.appKey) || StringUtils.isBlank(kustoLog4jConfig.appTenant);
        ConnectionStringBuilder csb = useManagedIdentity
                ? ConnectionStringBuilder.createWithAadManagedIdentity(kustoLog4jConfig.clusterIngestUrl,
                        kustoLog4jConfig.appId)
                : ConnectionStringBuilder.createWithAadApplicationCredentials(kustoLog4jConfig.clusterIngestUrl,
                        kustoLog4jConfig.appId,
                        kustoLog4jConfig.appKey, kustoLog4jConfig.appTenant);
        csb.setClientVersionForTracing(String.format("Kusto.Log4j.Connector:%s", getPackageVersion()));
        if (StringUtils.isNotBlank(kustoLog4jConfig.proxyUrl)) {
            HttpClientProperties proxy = HttpClientProperties.builder().proxy(HttpHost.create(kustoLog4jConfig.proxyUrl)).build();
            LOGGER.info("Using proxy : {} ", kustoLog4jConfig.proxyUrl);
            ingestClient = IngestClientFactory.createClient(csb, proxy);
        } else {
            ingestClient = IngestClientFactory.createClient(csb);
        }
        LOGGER.info("Ingesting logs to database : {} & table {}", kustoLog4jConfig.dbName,
                kustoLog4jConfig.tableName);
        ingestionProperties = new IngestionProperties(kustoLog4jConfig.dbName, kustoLog4jConfig.tableName);
        ingestionProperties.setFlushImmediately(kustoLog4jConfig.flushImmediately);
        boolean useMapping = StringUtils.isNotEmpty(kustoLog4jConfig.logTableMapping) && StringUtils.isNotEmpty(kustoLog4jConfig.mappingType);
        if (useMapping) {
            LOGGER.info("Using mapping {}  of type  {} ", kustoLog4jConfig.logTableMapping,
                    kustoLog4jConfig.mappingType);
            IngestionMapping.IngestionMappingKind mappingType = "json".equalsIgnoreCase(kustoLog4jConfig.mappingType) ? JSON : CSV;
            ingestionProperties.getIngestionMapping()
                    .setIngestionMappingReference(kustoLog4jConfig.logTableMapping, mappingType);
        }
    }

    /**
     * The instance is created based on the KustoLog4jConfig
     *
     * @param kustoLog4jConfig The config as passed by the application
     * @return The KustoClientInstance that will be operated on in the rest of the application
     * @throws URISyntaxException When the URI passed is invalid
     */
    static KustoClientInstance getInstance(KustoLog4jConfig kustoLog4jConfig) throws URISyntaxException {
        KustoClientInstance result = ATOMIC_INSTANCE.get();
        if (result != null) {
            return result;
        }
        synchronized (KustoClientInstance.class) {
            if (ATOMIC_INSTANCE.get() == null) {
                ATOMIC_INSTANCE.set(new KustoClientInstance(kustoLog4jConfig));
            }
            return ATOMIC_INSTANCE.get();
        }
    }

    /**
     * The instance that is created with the config is returned for use in parts of the application
     *
     * @return The KustoClientInstance that will be operated on in the rest of the application
     */
    static KustoClientInstance getInstance() {
        return ATOMIC_INSTANCE.get();
    }

    void ingestRolledFile(String filePath) {
        try {
            ingestionRetry.executeCheckedSupplier(() -> ingestLogs(filePath));
        } catch (Throwable e) {
            backOutFile(filePath);
        }
    }

    IngestionResult ingestLogs(String filePath) throws IngestionClientException, IngestionServiceException,
            IOException {
        try (InputStream inputStream = Files.newInputStream(Paths.get(filePath))) {
            StreamSourceInfo streamSourceInfo = new StreamSourceInfo(inputStream);
            return ingestClient.ingestFromStream(streamSourceInfo, ingestionProperties);
        }
    }

    /**
     * In case ingestion fails after retries, the logfile is moved to a backout directory that is in the same folder
     * as the path where the log file is being processed from (this allows for manual retry)
     * @param filePath The file that failed processing
     */
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

    private boolean isTransientException(Throwable exception) {
        Throwable innerException = exception.getCause();
        return !(innerException instanceof KustoDataExceptionBase &&
                ((KustoDataExceptionBase) innerException).isPermanent());
    }

    private static String getPackageVersion() {
        try {
            Properties props = new Properties();
            try (InputStream versionFileStream = KustoClientInstance.class.getResourceAsStream("/app.properties")) {
                props.load(versionFileStream);
                return props.getProperty("version").trim();
            }
        } catch (Exception ignored) {
            LOGGER.error("Error occured while getting the package version");
        }
        return "";
    }
}
