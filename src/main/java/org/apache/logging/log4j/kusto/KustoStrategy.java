// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package org.apache.logging.log4j.kusto;

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

import static org.apache.logging.log4j.kusto.Constants.*;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.zip.Deflater;

/**
 * dfsdgsd.
 */
@Plugin(name = "KustoStrategy", category = "Core", printObject = true)
public class KustoStrategy extends DefaultRolloverStrategy {

    /**
     * A logger
     */
    protected static final Logger LOGGER = StatusLogger.getLogger();
    private static final int MIN_WINDOW_SIZE = 1;
    private static final int DEFAULT_WINDOW_SIZE = 3;
    private static final int DEFAULT_BACKOFF_MIN_TIME_MINUTES = 1;
    private static final Boolean DEFAULT_FLUSH_IMMEDIATELY = false;
    private static final int DEFAULT_BACKOFF_MAX_TIME_MINUTES = 60;

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
     * @param clusterPath
     * @param appId
     * @param appKey
     * @param appTenant
     * @param dbName
     * @param tableName
     * @param logTableMapping
     * @param mappingType
     * @param config
     * @return KustoStrategy
     */
    @PluginFactory
    public static KustoStrategy createStrategy(@PluginAttribute("clusterPath") final String clusterPath,
            @PluginAttribute("appId") final String appId,
            @PluginAttribute("appKey") final String appKey,
            @PluginAttribute("appTenant") final String appTenant,
            @PluginAttribute("dbName") final String dbName,
            @PluginAttribute("tableName") final String tableName,
            @PluginAttribute("logTableMapping") final String logTableMapping,
            @PluginAttribute("mappingType") final String mappingType,
            @PluginAttribute("flushImmediately") final String flushImmediately,
            @PluginAttribute("proxyUrl") final String proxyUrl,
            @PluginAttribute("backOffMinMinutes") String backOffMinMinutes,
            @PluginAttribute("backOffMaxMinutes") String backOffMaxMinutes,
            @PluginConfiguration final Configuration config) {
        Integer backOffMax = backOffMaxMinutes != null && backOffMaxMinutes.trim().length() > 0 ? Integer.parseInt(backOffMaxMinutes)
                : DEFAULT_BACKOFF_MAX_TIME_MINUTES;
        Integer backOffMin = backOffMinMinutes != null && Objects.requireNonNull(backOffMinMinutes).trim().length() > 0
                ? Integer.parseInt(backOffMinMinutes)
                : DEFAULT_BACKOFF_MIN_TIME_MINUTES;
        boolean flushImmediatelyIngestion = flushImmediately != null && Objects.requireNonNull(flushImmediately).trim().length() > 0
                ? Boolean.valueOf(flushImmediately)
                : DEFAULT_FLUSH_IMMEDIATELY;
        KustoLog4jConfig kustoLog4jConfig = new KustoLog4jConfig(getOrEnvVar(clusterPath, LOG4J2_ADX_INGEST_CLUSTER_URL),
                getOrEnvVar(appId, LOG4J2_ADX_APP_ID),
                getOrEnvVar(appKey, LOG4J2_ADX_APP_KEY), getOrEnvVar(appTenant, LOG4J2_ADX_TENANT_ID), dbName,
                tableName,
                logTableMapping, mappingType, flushImmediatelyIngestion,
                proxyUrl, backOffMin, backOffMax);
        return new KustoStrategy(MIN_WINDOW_SIZE, DEFAULT_WINDOW_SIZE, true, Deflater.DEFAULT_COMPRESSION,
                config.getStrSubstitutor(),
                kustoLog4jConfig);
    }

    private static String getOrEnvVar(String value, String envVarName) {
        return value != null && value.trim().length() > 0 ? value : System.getenv(envVarName);
    }

    /**
     * A rollover.
     *
     * @param manager
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
