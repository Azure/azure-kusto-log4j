// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.azure.kusto.log4j;

class KustoLog4jConfig {

    final String clusterIngestUrl;
    final String appId;
    final String appKey;
    final String appTenant;
    final String dbName;
    final String tableName;
    final String logTableMapping;
    final String mappingType;

    final String proxyUrl;

    final Integer backOffMinSeconds;

    final Integer backOffMaxSeconds;

    final Boolean flushImmediately;

    KustoLog4jConfig(String clusterIngestUrl, String appId, String appKey, String appTenant, String dbName, String tableName,
            String logTableMapping, String mappingType, Boolean flushImmediately, String proxyUrl,
            Integer backOffMinSeconds,
            Integer backOffMaxSeconds) {
        this.clusterIngestUrl = clusterIngestUrl;
        this.appId = appId;
        this.appKey = appKey;
        this.appTenant = appTenant;
        this.dbName = dbName;
        this.tableName = tableName;
        this.logTableMapping = logTableMapping;
        this.mappingType = mappingType;
        this.flushImmediately = flushImmediately;
        this.proxyUrl = proxyUrl;
        this.backOffMaxSeconds = backOffMaxSeconds;
        this.backOffMinSeconds = backOffMinSeconds;
    }
}
