package org.apache.logging.log4j.kusto;

class KustoConfig {

    final String clusterPath;
    final String appId;
    final String appKey;
    final String appTenant;
    final String dbName;
    final String tableName;
    final String logTableMapping;
    final String mappingType;

    KustoConfig(String clusterPath, String appId, String appKey, String appTenant, String dbName, String tableName, String logTableMapping,
            String mappingType) {
        this.clusterPath = clusterPath;
        this.appId = appId;
        this.appKey = appKey;
        this.appTenant = appTenant;
        this.dbName = dbName;
        this.tableName = tableName;
        this.logTableMapping = logTableMapping;
        this.mappingType = mappingType;
    }
}
