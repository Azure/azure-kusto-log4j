// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.azure.kusto.log4j;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.RollingFileManager;
import org.apache.logging.log4j.core.appender.rolling.RolloverDescription;
import org.apache.logging.log4j.core.appender.rolling.action.FileRenameAction;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.status.StatusLogger;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.zip.Deflater;

/*
  The KustoStrategy that extends RolloverStrategy where rolled file will be ingested into ADX tables
 */
@Plugin(name = "KustoStrategy", category = "Core", printObject = true)
public class KustoStrategy extends DefaultRolloverStrategy {

    /**
     * A logger that logs data into Kusto
     */
    protected static final Logger LOGGER = StatusLogger.getLogger();
    private static final int MIN_BACKOFF_TIME_SECONDS = 60;
    private static final int MAX_BACKOFF_TIME_SECONDS = 3 * 60;
    private static final int DEFAULT_BACKOFF_MIN_TIME_MINUTES = 1;
    private static final Boolean DEFAULT_FLUSH_IMMEDIATELY = false;
    private static final Boolean DEFAULT_INTERACTIVE_AUTH = false;
    private static final int DEFAULT_BACKOFF_MAX_TIME_MINUTES = 60;

    /* Constants in use */
    private static final String LOG4J2_ADX_APP_ID = "LOG4J2_ADX_APP_ID";
    private static final String LOG4J2_ADX_APP_KEY = "LOG4J2_ADX_APP_KEY";
    private static final String LOG4J2_ADX_TENANT_ID = "LOG4J2_ADX_TENANT_ID";
    private static final String LOG4J2_ADX_INGEST_CLUSTER_URL = "LOG4J2_ADX_INGEST_CLUSTER_URL";

    protected KustoStrategy(int minIndex, int maxIndex, boolean useMax, int compressionLevel, StrSubstitutor subst,
            KustoLog4jConfig kustoLog4jConfig) {
        super(minIndex, maxIndex, useMax, compressionLevel, subst, null, true, "");
        try {
            KustoClientInstance initializedInstance = KustoClientInstance.getInstance(kustoLog4jConfig);
            Objects.requireNonNull(initializedInstance, "Kusto initialized instance cannot be null");
        } catch (URISyntaxException e) {
            LOGGER.error("Could not initialize ingest client", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * @param clusterIngestUrl       The clusterIngestUrl to which ingestion happens
     * @param appId             The client app id for authentication
     * @param appKey            The client app secret for authentication
     * @param appTenant         The directory where the app id is created
     * @param useInteractiveAuth            If interactive auth has to be used
     * @param dbName            The database name where the log table is
     * @param tableName         The table to ingest data into
     * @param logTableMapping   The mapping for the log table
     * @param mappingType       JSON or CSV mapping type
     * @param flushImmediately  If the ingestion flush happens immediately
     * @param proxyUrl          If the application is behind a proxy, the proxy URL
     * @param backOffMinSeconds The lower bound minutes to wait before retry of ingestion
     * @param backOffMaxSeconds The upper bound minutes to wait for retry before giving up
     * @param config            The config and policy used for the logging
     * @return KustoStrategy that ingests the rolling files created
     */
    @PluginFactory
    public static KustoStrategy createStrategy(@PluginAttribute("clusterIngestUrl") final String clusterIngestUrl,
            @PluginAttribute("appId") final String appId,
            @PluginAttribute("appKey") final String appKey,
            @PluginAttribute("appTenant") final String appTenant,
            @PluginAttribute("useInteractiveAuth") final String useInteractiveAuth,
            @PluginAttribute("dbName") final String dbName,
            @PluginAttribute("tableName") final String tableName,
            @PluginAttribute("logTableMapping") final String logTableMapping,
            @PluginAttribute("mappingType") final String mappingType,
            @PluginAttribute("flushImmediately") final String flushImmediately,
            @PluginAttribute("proxyUrl") final String proxyUrl,
            @PluginAttribute("backOffMinSeconds") String backOffMinSeconds,
            @PluginAttribute("backOffMaxSeconds") String backOffMaxSeconds,
            @PluginConfiguration final Configuration config) {
        Integer backOffMax = backOffMaxSeconds != null && !backOffMaxSeconds.trim().isEmpty() ? Integer.parseInt(backOffMaxSeconds)
                : DEFAULT_BACKOFF_MAX_TIME_MINUTES;
        Integer backOffMin = backOffMinSeconds != null && !Objects.requireNonNull(backOffMinSeconds).trim().isEmpty()
                ? Integer.parseInt(backOffMinSeconds)
                : DEFAULT_BACKOFF_MIN_TIME_MINUTES;
        boolean flushImmediatelyIngestion = flushImmediately != null && !Objects.requireNonNull(flushImmediately).trim().isEmpty()
                ? Boolean.valueOf(flushImmediately)
                : DEFAULT_FLUSH_IMMEDIATELY;
        boolean useInteractiveAuthVal = useInteractiveAuth != null && !Objects.requireNonNull(useInteractiveAuth).trim().isEmpty()
                ? Boolean.valueOf(useInteractiveAuth)
                : DEFAULT_INTERACTIVE_AUTH;

        KustoLog4jConfig kustoLog4jConfig = new KustoLog4jConfig(getOrEnvVar(clusterIngestUrl, LOG4J2_ADX_INGEST_CLUSTER_URL),
                getOrEnvVar(appId, LOG4J2_ADX_APP_ID),
                getOrEnvVar(appKey, LOG4J2_ADX_APP_KEY),
                getOrEnvVar(appTenant, LOG4J2_ADX_TENANT_ID), useInteractiveAuthVal, dbName,
                tableName,
                logTableMapping, mappingType, flushImmediatelyIngestion,
                proxyUrl, backOffMin, backOffMax);
        return new KustoStrategy(MIN_BACKOFF_TIME_SECONDS, MAX_BACKOFF_TIME_SECONDS, true, Deflater.DEFAULT_COMPRESSION,
                config.getStrSubstitutor(),
                kustoLog4jConfig);
    }

    private static String getOrEnvVar(String value, String envVarName) {
        return value != null && !value.trim().isEmpty() ? value : System.getenv(envVarName);
    }

    /**
     * @param manager The rolling file manager, for example a rolling file which renames files for archival
     * @return RolloverDescription
     */
    @Override
    public RolloverDescription rollover(final RollingFileManager manager) {
        RolloverDescription rolloverDescription = super.rollover(manager);
        String path = "";
        if (rolloverDescription.getSynchronous() instanceof FileRenameAction) {
            File file = ((FileRenameAction) rolloverDescription.getSynchronous()).getDestination();
            path = file.getPath();
        }
        return new KustoRolloverDescription(rolloverDescription, path);
    }
}
