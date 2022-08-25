Kusto appender for Log4j.

Motivation and usage.
----------------------
Log4j2 is widely used as logging tool. Kusto implementation
is used in conjunction with RollingFileAppender with KustoStrategy.
The key reason for using a strategy is to have redundancy in storage
of logs and re-transmit the log files.

To provide data transmission redundancy , the rolled over log files are
transmitted to Kusto. Transmission of the files are attempted 3 times
with a configured time window


Adding appender to log4j.properties
----------------------
The key parameters for rolling file are as documented in
the [Rolling file log4j configuration](https://logging.apache.org/log4j/2.x/manual/appenders.html#RollingFileAppender)

- fileName : The file name where the log files will be written locally (e.g C:/logs/logs.log)
- filePattern : The rolled over file name with pattern (e.g C:/logs/logs-%d{yyyy-MM-dd-hh-mm-ss}-%i.log)

Configurations for using the Kusto log4j appender is as follows

- KustoStrategy
    - **clusterPath**: Ingest URL.Configured using environment variable **LOG4J2_ADX_INGEST_CLUSTER_URL**
    - **appId**: Service principal application id.Configured using environment variable **LOG4J2_ADX_APP_ID**
    - **appKey**: Service principal application secret.Configured using environment variable **LOG4J2_ADX_APP_KEY**
    - **appTenant**: Tenant for the Service principal.Configured using environment variable **LOG4J2_ADX_TENANT_ID**
    - **dbName**: Database name.Configured using environment variable **LOG4J2_ADX_DB_NAME**
    - **tableName**: Table name for ingesting the logs
    - **logTableMapping**: Mapping defined in the database to map the log data
    - **mappingType**: json (or) csv is currently supported. Defaults to **_csv_**
    - **flushImmediately**: Boolean indicator to flush the logs immediately. Defaults to **_false_**. Note that making
      this true may cause additional load on the cluster
    - **proxyUrl**: Proxy url in case application is hosted behind a proxy

- To attempt retries in case of ingestion failures, retransmission is attempted with the following configuration. 3
  retries are attempted to ingest the logs

    - **backOffMinMinutes**: Min minutes to back off in the event that ingestion fails
    - **backOffMaxMinutes**: Max minutes to back off in the event that ingestion fails

```xml
<Configuration status="WARN">
    <Appenders>
        <RollingFile name="ADXRollingFile" fileName="<fileName>"
                     filePattern="<filePattern>">
            <KustoStrategy
                    clusterPath="https://ingest-<cluster>.kusto.windows.net"
                    appId=""
                    appKey=""
                    appTenant=""
                    dbName=""
                    tableName=""
                    logTableMapping=""
                    mappingType=""
                    flushImmediately=""
                    proxyUrl=""
                    backOffMinMinutes=""
                    backOffMaxMinutes=""
            />
            <CsvLogEventLayout delimiter="," quoteMode="ALL"/>
            <Policies>
              <!-- Recommended size is 4 MB -->
                <SizeBasedTriggeringPolicy size="4 MB"/>
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
$env:LOG4J2_ADX_ENGINE_URL="https://<cluster>.kusto.windows.net"
$env:LOG4J2_ADX_TENANT_ID="<tenant-id>"                   
$env:LOG4J2_ADX_INGEST_CLUSTER_URL="https://ingest-<cluster>.kusto.windows.net"
$env:LOG4J2_ADX_APP_ID="<app-id>"
$env:LOG4J2_ADX_APP_KEY="<app-key>" 
```

followed by

```mvn clean package```

If you are a maven user, maven dependency plugin can resolve the dependencies.
