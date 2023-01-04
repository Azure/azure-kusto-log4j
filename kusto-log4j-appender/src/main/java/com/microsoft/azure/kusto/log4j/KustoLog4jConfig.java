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

    KustoLog4jConfig(KustoLog4jConfigBuilder kustoLog4jConfigBuilder) {
        clusterIngestUrl = kustoLog4jConfigBuilder.clusterIngestUrl;
        appId = kustoLog4jConfigBuilder.appId;
        appKey = kustoLog4jConfigBuilder.appKey;
        appTenant = kustoLog4jConfigBuilder.appTenant;
        dbName = kustoLog4jConfigBuilder.dbName;
        tableName = kustoLog4jConfigBuilder.tableName;
        logTableMapping = kustoLog4jConfigBuilder.logTableMapping;
        mappingType = kustoLog4jConfigBuilder.mappingType;
        flushImmediately = kustoLog4jConfigBuilder.flushImmediately;
        proxyUrl = kustoLog4jConfigBuilder.proxyUrl;
        backOffMaxSeconds = kustoLog4jConfigBuilder.backOffMaxSeconds;
        backOffMinSeconds = kustoLog4jConfigBuilder.backOffMinSeconds;
    }

    public static class KustoLog4jConfigBuilder {

        private final String clusterIngestUrl;
        private final String appId;
        private final String appKey;
        private final String appTenant;
        private final String dbName;
        private final String tableName;
        private String logTableMapping;
        private String mappingType;

        private String proxyUrl;

        private Integer backOffMinSeconds;

        private Integer backOffMaxSeconds;

        private Boolean flushImmediately;

        public KustoLog4jConfigBuilder(String clusterIngestUrl, String appId, String appKey, String appTenant, String dbName, String tableName) {
            this.clusterIngestUrl = clusterIngestUrl;
            this.appId = appId;
            this.appKey = appKey;
            this.appTenant = appTenant;
            this.dbName = dbName;
            this.tableName = tableName;
        }

        public KustoLog4jConfigBuilder addMapping(String mapping) {
            this.logTableMapping = mapping;
            return this;
        }

        public KustoLog4jConfigBuilder addMappingType(String mappingType) {
            this.mappingType = mappingType;
            return this;
        }

        public KustoLog4jConfigBuilder addProxyUrl(String proxyUrl) {
            this.proxyUrl = proxyUrl;
            return this;
        }

        public KustoLog4jConfigBuilder addBackOffMinSeconds(Integer backOffMinSeconds) {
            this.backOffMinSeconds = backOffMinSeconds;
            return this;
        }

        public KustoLog4jConfigBuilder addBackOffMaxSeconds(Integer backOffMaxSeconds) {
            this.backOffMaxSeconds = backOffMaxSeconds;
            return this;
        }

        public KustoLog4jConfigBuilder addFlushImmediately(Boolean flushImmediately) {
            this.flushImmediately = flushImmediately;
            return this;
        }

        public KustoLog4jConfig build() {
            return new KustoLog4jConfig(this);
        }

    }

}
