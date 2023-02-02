# Databricks notebook source
dbutils.fs.put("/databricks/scripts/init-log4j-kusto-logging.sh","""
#!/bin/bash
DB_HOME=/databricks
SPARK_HOME=$DB_HOME/spark
# All the environment variables that need to be set for writing logs to Kusto

LOG4J2_ADX_INGEST_CLUSTER_URL="https://ingest-<>.kusto.windows.net"
LOG4J2_ADX_APP_ID="App Id"
LOG4J2_ADX_APP_KEY="App Key"
LOG4J2_ADX_TENANT_ID="Tenant"
LOG4J2_ADX_DB_NAME="DB"


echo "BEGIN: Downloading Kusto log4j library dependencies"
wget --quiet -O /mnt/driver-daemon/jars/azure-kusto-log4j-1.0.1-jar-with-dependencies.jar https://repo1.maven.org/maven2/com/microsoft/azure/kusto/kusto-log4j-appender/1.0.1/kusto-log4j-appender-1.0.1-jar-with-dependencies.jar

echo "BEGIN: Setting log4j2 property files"
cat >>/tmp/log4j2.properties <<EOL
status=debug
name=PropertiesConfig
appender.console.type=Console
appender.console.name=STDOUT
appender.console.layout.type=PatternLayout
appender.console.layout.pattern=%m%n
appender.rolling.type=RollingFile
appender.rolling.name=RollingFile
appender.rolling.fileName=log4j-kusto-active.log
appender.rolling.filePattern=logs/log4j-kusto-%d{MM-dd-yy-HH-mm-ss}-%i.log.gz
appender.rolling.strategy.type=KustoStrategy
appender.rolling.strategy.clusterIngestUrl=$LOG4J2_ADX_INGEST_CLUSTER_URL
appender.rolling.strategy.appId=$LOG4J2_ADX_APP_ID
appender.rolling.strategy.appKey=$LOG4J2_ADX_APP_KEY
appender.rolling.strategy.appTenant=$LOG4J2_ADX_TENANT_ID
appender.rolling.strategy.dbName=$LOG4J2_ADX_DB_NAME
appender.rolling.strategy.tableName=log4jTest
appender.rolling.strategy.logTableMapping=log4jCsvTestMapping
appender.rolling.strategy.flushImmediately=false
appender.rolling.strategy.mappingType=csv
appender.rolling.layout.type=CsvLogEventLayout
appender.rolling.layout.delimiter=,
appender.rolling.layout.quoteMode=ALL
appender.rolling.policies.type=Policies
appender.rolling.policies.time.type=TimeBasedTriggeringPolicy
appender.rolling.policies.time.interval=60
appender.rolling.policies.time.modulate=true
appender.rolling.policies.size.type=SizeBasedTriggeringPolicy
appender.rolling.policies.size.size=1MB
logger.rolling=debug,RollingFile
logger.rolling.name=com
logger.rolling.additivity=false
rootLogger=debug,STDOUT
EOL
echo "END: Setting log4j2 properties files"

log4jDirectories=( "executor" "driver" "master-worker" )
for log4jDirectory in "${log4jDirectories[@]}"
do
	LOG4J2_CONFIG_FILE="$SPARK_HOME/dbconf/log4j/$log4jDirectory/log4j2.properties"
	echo "BEGIN: Updating $LOG4J2_CONFIG_FILE with Kusto appender"
	cp /tmp/log4j2.properties $LOG4J2_CONFIG_FILE
done
echo "END: Update Log4j Kusto logging init"
""", True)
