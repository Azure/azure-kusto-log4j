// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package org.apache.logging.log4j.ext.kusto;

class KustoLog4jConfig {

    final String clusterPath;
    final String appId;
    final String appKey;
    final String appTenant;
    final String dbName;
    final String tableName;
    final String logTableMapping;
    final String mappingType;

    final String proxyUrl;

    final Integer backOffMinMinutes;

    final Integer backOffMaxMinutes;

    final Boolean flushImmediately;

    KustoLog4jConfig(String clusterPath, String appId, String appKey, String appTenant, String dbName, String tableName,
            String logTableMapping, String mappingType, Boolean flushImmediately, String proxyUrl,
            Integer backOffMinMinutes,
            Integer backOffMaxMinutes) {
        this.clusterPath = clusterPath;
        this.appId = appId;
        this.appKey = appKey;
        this.appTenant = appTenant;
        this.dbName = dbName;
        this.tableName = tableName;
        this.logTableMapping = logTableMapping;
        this.mappingType = mappingType;
        this.flushImmediately = flushImmediately;
        this.proxyUrl = proxyUrl;
        this.backOffMaxMinutes = backOffMaxMinutes;
        this.backOffMinMinutes = backOffMinMinutes;
    }
}
