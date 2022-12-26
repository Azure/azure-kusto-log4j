# Getting Started with Kusto Log4j Appender Sample Project

### Prerequisites

- A Java Developer Kit (JDK), version 11 or later
- Maven
- Clone the project and enter the samples directory:

```sh
      git clone https://github.com/Azure/azure-kusto-log4j.git
      cd samples
```

## Configure log4j for ingesting logs to Kusto

- The following steps will guide on how to set up a sample application which connects to kusto cluster and ingests logs
  into it.

### Prerequisites

- [Create Azure Data Explorer Cluster and DB](https://docs.microsoft.com/en-us/azure/data-explorer/create-cluster-database-portal)
- [Create Azure Active Directory App Registration and grant it permissions to DB](https://docs.microsoft.com/en-us/azure/kusto/management/access-control/how-to-provision-aad-app) (
  save the app key and the application ID for later)

### Selecting the log4j configuration format

- For configuring the log4j we need select from the following format xml/json/properties/yaml.
- In the sample project the default format is log4j2.xml. Inorder to try other formats, please replace the log4j2.xml
  with the desired format. Other log4j2 config formats can be found under the following directory
   ```
  samples
   ├── README.md
   ├── pom.xml
   ├── src
   │     └── main
   │           ├── java
   │           │   └── org
   │           │       └── example
   │           │           └── KustoLog4JSampleApp.java
   │           └── resources
   │               ├── configformats
   │               │   ├── log4j2.json
   │               │   ├── log4j2.properties
   │               │   └── log4j2.yaml
   │               └── log4j2.xml
  ```                                         

Log4j has the ability to automatically configure itself during initialization. When Log4j starts it will locate all the
ConfigurationFactory plugins and arrange them in weighted order from highest to lowest. As delivered, Log4j contains
four ConfigurationFactory implementations: one for JSON, one for YAML, one for properties, and one for XML.
The hierarchy of configuration is as follows : properties -> yaml -> json -> xml

Further details on the auto-configuration can be
found [here](https://logging.apache.org/log4j/2.x/manual/configuration.html) in the official documentation of log4j.

### How to run this sample

If the selected log4j configuration format is xml, the following attributes of KustoStrategy needs to be configured

```
<KustoStrategy
   clusterIngestUrl="${sys:LOG4J2_ADX_INGEST_CLUSTER_URL}"
   appId="${sys:LOG4J2_ADX_APP_ID}"
   appKey="${sys:LOG4J2_ADX_APP_KEY}"
   appTenant="${sys:LOG4J2_ADX_TENANT_ID}"
   dbName="${sys:LOG4J2_ADX_DB_NAME}"
   tableName="Name-of-created-table-in-ADX"
   logTableMapping="Name-of-mapping-created-in-ADX-for-the-table"
   mappingType="json/csv-default-is-csv"
   flushImmediately="whether-flush-logs-immediately-default-is-false"
/>

```

If the selected log4j configuration format is json, the following attributes of KustoStrategy needs to be configured

```
"KustoStrategy": {
        "clusterIngestUrl": "${sys:LOG4J2_ADX_INGEST_CLUSTER_URL}",
        "appId": "${sys:LOG4J2_ADX_APP_ID}",
        "appKey": "${sys:LOG4J2_ADX_APP_KEY}",
        "appTenant": "${sys:LOG4J2_ADX_TENANT_ID}",
        "dbName": "${sys:LOG4J2_ADX_DB_NAME}",
        "tableName": "Name-of-created-table-in-ADX",
        "logTableMapping": "Name-of-mapping-created-in-ADX-for-the-table",
        "mappingType": "json/csv-default-is-csv",
        "flushImmediately": "whether-flush-logs-immediately-default-is-false"
      }
```

If the selected log4j configuration format is yaml, the following attributes of KustoStrategy needs to be configured

```
      KustoStrategy:
        clusterIngestUrl: ${sys:LOG4J2_ADX_INGEST_CLUSTER_URL}
        appId: ${sys:LOG4J2_ADX_APP_ID}
        appKey: ${sys:LOG4J2_ADX_APP_KEY}
        appTenant: ${sys:LOG4J2_ADX_TENANT_ID}
        dbName: ${sys:LOG4J2_ADX_DB_NAME}
        tableName: Name-of-created-table-in-ADX
        logTableMapping: Name-of-mapping-created-in-ADX-for-the-table
        mappingType: json/csv-default-is-csv
        flushImmediately: whether-flush-logs-immediately-default-is-false
```

If the selected log4j configuration format is properties, the following attributes of KustoStrategy needs to be
configured

```
appender.rolling.strategy.type=KustoStrategy
appender.rolling.strategy.clusterIngestUrl=${sys:LOG4J2_ADX_INGEST_CLUSTER_URL}
appender.rolling.strategy.appId=${sys:LOG4J2_ADX_APP_ID}
appender.rolling.strategy.appKey=${sys:LOG4J2_ADX_APP_KEY}
appender.rolling.strategy.appTenant=${sys:LOG4J2_ADX_TENANT_ID}
appender.rolling.strategy.dbName=${sys:LOG4J2_ADX_DB_NAME}
appender.rolling.strategy.tableName=Name-of-created-table-in-ADX
appender.rolling.strategy.logTableMapping=Name-of-mapping-created-in-ADX-for-the-table
appender.rolling.strategy.mappingType=json/csv-default-is-csv
appender.rolling.strategy.flushImmediately=whether-flush-logs-immediately-default-is-false
```

```sh
cd samples
mvn compile exec:java -Dexec.mainClass="org.example.KustoLog4JSampleApp" 
                      -DLOG4J2_ADX_DB_NAME="ADX-database-name" 
                      -DLOG4J2_ADX_TENANT_ID="tenant-id" 
                      -DLOG4J2_ADX_INGEST_CLUSTER_URL="ADX-ingest-url" 
                      -DLOG4J2_ADX_APP_ID="appId" 
                      -DLOG4J2_ADX_APP_KEY="appKey"

```

### More information

[Kusto Log4J Connector](https://github.com/Azure/azure-kusto-log4j)

If you don't have a Microsoft Azure subscription you can get a FREE trial
account [here](http://go.microsoft.com/fwlink/?LinkId=330212)

---

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/).
For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or
contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.