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
- Create a table in Azure Data Explorer which will be used to store log data.
  For example, We have created a table with name log4jTest

```
  .create table log4jTest (timenanos:long,timemillis:long,level:string,threadid:string,threadname:string,threadpriority:int,formattedmessage:string,loggerfqcn:string,loggername:string,marker:string,thrownproxy:string,source:string,contextmap:string,contextstack:string)
```

- Create a mapping for the table created in Azure Data Explorer
  For example, I have created a csv mapping with name log4jCsvTestMapping

```
  .create table log4jTest ingestion csv mapping 'log4jCsvTestMapping' '[{"Name":"timenanos","DataType":"","Ordinal":"0","ConstValue":null},{"Name":"timemillis","DataType":"","Ordinal":"1","ConstValue":null},{"Name":"level","DataType":"","Ordinal":"2","ConstValue":null},{"Name":"threadid","DataType":"","Ordinal":"3","ConstValue":null},{"Name":"threadname","DataType":"","Ordinal":"4","ConstValue":null},{"Name":"threadpriority","DataType":"","Ordinal":"5","ConstValue":null},{"Name":"formattedmessage","DataType":"","Ordinal":"6","ConstValue":null},{"Name":"loggerfqcn","DataType":"","Ordinal":"7","ConstValue":null},{"Name":"loggername","DataType":"","Ordinal":"8","ConstValue":null},{"Name":"marker","DataType":"","Ordinal":"9","ConstValue":null},{"Name":"thrownproxy","DataType":"","Ordinal":"10","ConstValue":null},{"Name":"source","DataType":"","Ordinal":"11","ConstValue":null},{"Name":"contextmap","DataType":"","Ordinal":"12","ConstValue":null},{"Name":"contextstack","DataType":"","Ordinal":"13","ConstValue":null}]'
```

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

For Windows, in power-shell the following environment variables need to be set

```sh
$env:LOG4J2_ADX_DB_NAME="<db-name>"
$env:LOG4J2_ADX_TENANT_ID="<tenant-id>"                   
$env:LOG4J2_ADX_INGEST_CLUSTER_URL="https://ingest-<cluster>.kusto.windows.net"
$env:LOG4J2_ADX_APP_ID="<app-id>"
$env:LOG4J2_ADX_APP_KEY="<app-key>" 
```

For Mac/Linux, the following environment variables need to be set in the terminal

```sh
export LOG4J2_ADX_DB_NAME="<db-name>"
export LOG4J2_ADX_TENANT_ID="<tenant-id>"
export LOG4J2_ADX_INGEST_CLUSTER_URL="https://ingest-<cluster>.kusto.windows.net"
export LOG4J2_ADX_APP_ID="<app-id>"
export LOG4J2_ADX_APP_KEY="<app-key>"
```

If the selected log4j configuration format is xml, the following attributes of KustoStrategy needs to be configured

Note : As mentioned above in the prerequisites, we have created a ADX table with name log4jTest and mapping with name
log4jCsvTestMapping. We will be using those below.

```
<KustoStrategy
   clusterIngestUrl="${env:LOG4J2_ADX_INGEST_CLUSTER_URL}"
   appId="${env:LOG4J2_ADX_APP_ID}"
   appKey="${env:LOG4J2_ADX_APP_KEY}"
   appTenant="${env:LOG4J2_ADX_TENANT_ID}"
   dbName="${env:LOG4J2_ADX_DB_NAME}"
   tableName="log4jTest"
   logTableMapping="log4jCsvTestMapping"
   mappingType="csv"
   flushImmediately="false"
/>

```

If the selected log4j configuration format is json, the following attributes of KustoStrategy needs to be configured

```
"KustoStrategy": {
        "clusterIngestUrl": "${env:LOG4J2_ADX_INGEST_CLUSTER_URL}",
        "appId": "${env:LOG4J2_ADX_APP_ID}",
        "appKey": "${env:LOG4J2_ADX_APP_KEY}",
        "appTenant": "${env:LOG4J2_ADX_TENANT_ID}",
        "dbName": "${env:LOG4J2_ADX_DB_NAME}",
        "tableName": "log4jTest",
        "logTableMapping": "log4jCsvTestMapping",
        "mappingType": "csv",
        "flushImmediately": "false"
      }
```

If the selected log4j configuration format is yaml, the following attributes of KustoStrategy needs to be configured

```
KustoStrategy:
      clusterIngestUrl: ${env:LOG4J2_ADX_INGEST_CLUSTER_URL}
      appId: ${env:LOG4J2_ADX_APP_ID}
      appKey: ${env:LOG4J2_ADX_APP_KEY}
      appTenant: ${env:LOG4J2_ADX_TENANT_ID}
      dbName: ${env:LOG4J2_ADX_DB_NAME}
      tableName: log4jTest
      logTableMapping: log4jCsvTestMapping
      mappingType: csv
      flushImmediately: false
```

If the selected log4j configuration format is properties, the following attributes of KustoStrategy needs to be
configured

```
appender.rolling.strategy.type=KustoStrategy
appender.rolling.strategy.clusterIngestUrl=${env:LOG4J2_ADX_INGEST_CLUSTER_URL}
appender.rolling.strategy.appId=${env:LOG4J2_ADX_APP_ID}
appender.rolling.strategy.appKey=${env:LOG4J2_ADX_APP_KEY}
appender.rolling.strategy.appTenant=${env:LOG4J2_ADX_TENANT_ID}
appender.rolling.strategy.dbName=${env:LOG4J2_ADX_DB_NAME}
appender.rolling.strategy.tableName=log4jTest
appender.rolling.strategy.logTableMapping=log4jCsvTestMapping
appender.rolling.strategy.mappingType=json/csv-default-is-csv
appender.rolling.strategy.flushImmediately=whether-flush-logs-immediately-default-is-false
```

```sh
cd samples
mvn compile exec:java -Dexec.mainClass="com.microsoft.azure.kusto.log4j.sample.KustoLog4JSampleApp"

```

### More information

Please refer to [Kusto Log4J Connector](https://github.com/Azure/azure-kusto-log4j)

If you don't have a Microsoft Azure subscription you can get a FREE trial
account [here](http://go.microsoft.com/fwlink/?LinkId=330212)

---

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/).
For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or
contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.
