/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.kusto;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.RollingFileManager;
import org.apache.logging.log4j.core.appender.rolling.RolloverDescription;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.status.StatusLogger;

import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder;
import com.microsoft.azure.kusto.ingest.IngestClient;
import com.microsoft.azure.kusto.ingest.IngestClientFactory;
import com.microsoft.azure.kusto.ingest.IngestionMapping;
import com.microsoft.azure.kusto.ingest.IngestionProperties;

import static com.microsoft.azure.kusto.ingest.IngestionMapping.IngestionMappingKind.CSV;
import static com.microsoft.azure.kusto.ingest.IngestionMapping.IngestionMappingKind.JSON;

import java.net.URISyntaxException;
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

    private final IngestClient ingestClient;
    private final IngestionProperties ingestionProperties;

    protected KustoStrategy(int minIndex, int maxIndex, boolean useMax, int compressionLevel, StrSubstitutor subst,
            KustoConfig kustoConfig) {
        super(minIndex, maxIndex, useMax, compressionLevel, subst, null, true, "");
        ConnectionStringBuilder csb = "".equals(kustoConfig.appKey) || "".equals(kustoConfig.appTenant)
                ? ConnectionStringBuilder.createWithAadManagedIdentity(kustoConfig.clusterPath, kustoConfig.appId)
                : ConnectionStringBuilder.createWithAadApplicationCredentials(kustoConfig.clusterPath, kustoConfig.appId,
                        kustoConfig.appKey, kustoConfig.appTenant);
        try {
            ingestClient = IngestClientFactory.createClient(csb);
            ingestionProperties = new IngestionProperties(kustoConfig.dbName, kustoConfig.tableName);
            if (kustoConfig.logTableMapping != null && kustoConfig.mappingType != null && !"".equals(kustoConfig.logTableMapping.trim())
                    && !"".equals(kustoConfig.mappingType.trim())) {
                LOGGER.error("Using mapping " + kustoConfig.logTableMapping + " of type " + kustoConfig.mappingType);
                IngestionMapping.IngestionMappingKind mappingType = "json".equalsIgnoreCase(kustoConfig.mappingType) ? JSON : CSV;
                ingestionProperties.getIngestionMapping().setIngestionMappingReference(kustoConfig.logTableMapping, mappingType);
            }
        } catch (URISyntaxException e) {
            LOGGER.error("Could not initialize ingest client", e);
            throw new RuntimeException(e);
        }

    }

    /**
     * @param fileIndex
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
    public static KustoStrategy createStrategy(@PluginAttribute("fileIndex") final String fileIndex,
            @PluginAttribute("clusterPath") final String clusterPath, @PluginAttribute("appId") final String appId,
            @PluginAttribute("appKey") final String appKey, @PluginAttribute("appTenant") final String appTenant,
            @PluginAttribute("dbName") final String dbName, @PluginAttribute("tableName") final String tableName,
            @PluginAttribute("logTableMapping") final String logTableMapping, @PluginAttribute("mappingType") final String mappingType,
            @PluginConfiguration final Configuration config) {
        KustoConfig kustoConfig = new KustoConfig(clusterPath, appId, appKey, appTenant, dbName, tableName, logTableMapping, mappingType);
        return new KustoStrategy(MIN_WINDOW_SIZE, DEFAULT_WINDOW_SIZE, true, Deflater.DEFAULT_COMPRESSION, config.getStrSubstitutor(),
                kustoConfig);
    }

    /**
     * A rollover.
     *
     * @param manager
     * @return RolloverDescription
     */
    public RolloverDescription rollover(final RollingFileManager manager) {
        RolloverDescription ret = super.rollover(manager);
        return new KustoRolloverAction.KustoRolloverDescription(ret, ingestClient, ingestionProperties);
    }

}
