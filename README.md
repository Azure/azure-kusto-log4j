Kusto appender for Log4j.

Motivation and usage.
----------------------
Log4j2 is widely used as logging tool. Kusto implementation
is used in conjunction with RollingFileAppender with KustoStrategy.
The key reason for using a strategy is to have redundancy in storage
of logs and re-transmit the log files.

To provide data transmission redundancy, the rolled over log files are
transmitted to Kusto. Transmission of the files are attempted 3 times
with a configured time window


Adding appender to log4j.properties
----------------------
The key parameters for rolling file are as documented in
the [Rolling file log4j configuration](https://logging.apache.org/log4j/2.x/manual/appenders.html#RollingFileAppender)

- fileName: The file name where the log files will be written locally. This is a fully qualified path and not a
  relative path (e.g C:/logs/logs.log)
- filePattern: The rolled over file name with pattern. This is a fully qualified path and not a relative path (e.g C:
  /logs/logs-%d{yyyy-MM-dd-hh-mm-ss}-%i.log)

Configurations for using the Kusto log4j appender is as follows

- KustoStrategy
    - **clusterIngestUrl**: Ingest URL. Configured using environment variable **LOG4J2_ADX_INGEST_CLUSTER_URL**
    - **appId**: Service principal application id. Configured using environment variable **LOG4J2_ADX_APP_ID**
    - **appKey**: Service principal application secret. Configured using environment variable **LOG4J2_ADX_APP_KEY**
    - **appTenant**: Tenant for the Service principal. Configured using environment variable **LOG4J2_ADX_TENANT_ID**
    - **dbName**: Database name. Configured using environment variable **LOG4J2_ADX_DB_NAME**
    - **tableName**: Table name for ingesting the logs
    - **logTableMapping**: Mapping defined in the database to map the log data
    - **mappingType**: json (or) csv is currently supported. Defaults to **_csv_**
    - **flushImmediately**: Boolean indicator to flush the logs immediately. Defaults to **_false_**. Note that making
      this true may cause additional load on the cluster
    - **proxyUrl**: Proxy url in case application is hosted behind a proxy

- To attempt retries in case of ingestion failures, retransmission is attempted with the following configuration. 3
  retries are attempted to ingest the logs. In the event that the file cannot be ingested it gets moved to the backout
  directory in the same path defined in fileName

    - **backOffMinMinutes**: Min minutes to back off in the event that ingestion fails
    - **backOffMaxMinutes**: Max minutes to back off in the event that ingestion fails

```xml

<Configuration status="WARN">
  <Appenders>
    <RollingFile name="ADXRollingFile" fileName="<fileName>"
                 filePattern="<filePattern>">
      <KustoStrategy
              clusterIngestUrl="${sys:LOG4J2_ADX_INGEST_CLUSTER_URL}"
              appId="${sys:LOG4J2_ADX_APP_ID}"
              appKey="${sys:LOG4J2_ADX_APP_KEY}"
              appTenant="${sys:LOG4J2_ADX_TENANT_ID}"
              dbName="${sys:LOG4J2_ADX_DB_NAME}"
              tableName=""
              logTableMapping=""
              mappingType=""
              flushImmediately=""
              proxyUrl=""
              backOffMinMinutes=""
              backOffMaxMinutes=""
      />
      <CsvLogEventLayout delimiter="," quoteMode="ALL"/>
      <!-- References policies from https://logging.apache.org/log4j/2.x/manual/appenders.html -->
      <Policies>
        <!-- Recommended size is 4 MB -->
        <SizeBasedTriggeringPolicy size="4 MB"/>
        <!-- 
        The interval determines in conjunction with file pattern the time for rollup. If file has pattern
        file-yyyy-MM-dd-hh-mm.log then rollover happens evey 5 minutes (interval below)
        With a date pattern file-yyyy-MM-dd-hh.log with hours as the most specific item, rollover would happen
        every 5 hours 
        -->
        <TimeBasedTriggeringPolicy interval="5" modulate="true"/>
            </Policies>
        </RollingFile>
    </Appenders>
    <Loggers>
        <Root level="debug" additivity="false">
            <AppenderRef ref="ADXRollingFile"/>
        </Root>
    </Loggers>
</Configuration>
```

How to build:
----------------------
In power-shell the following can be set

```sh
$env:LOG4J2_ADX_DB_NAME="<db-name>"
$env:LOG4J2_ADX_TENANT_ID="<tenant-id>"                   
$env:LOG4J2_ADX_INGEST_CLUSTER_URL="https://ingest-<cluster>.kusto.windows.net"
$env:LOG4J2_ADX_APP_ID="<app-id>"
$env:LOG4J2_ADX_APP_KEY="<app-key>" 
```
followed by 

```mvn clean compiler:compile compiler:testCompile surefire:test```

If you are a maven user, maven dependency plugin can resolve the dependencies. 
Note : The library uses resilience4j which is brought in automatically by the dependencies to perform retries.

Maven co-ordinates:
----------------------
To use the library in an application, add the following dependency in maven
```xml
<dependency>
    <groupId>com.microsoft.azure.kusto</groupId>
    <artifactId>azure-kusto-log4j</artifactId>
    <version>1.0.0</version>
</dependency>
```
The library expects that `log4j-core` is provided as a dependency in the application. This needs to be included as a 
dependency, this provides flexibility in using a custom log4j version core library in the application.
```xml
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
    <version>${log4j.version}</version>
</dependency>
```
