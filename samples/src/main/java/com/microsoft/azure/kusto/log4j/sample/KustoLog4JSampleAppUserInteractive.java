package com.microsoft.azure.kusto.log4j.sample;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sample Application which continuously produces application logs and these logs are then ingested into Azure Data Explorer.
 * Uses interactive login which is handy for Development scenarios
 */
public class KustoLog4JSampleAppUserInteractive {
    /*
     * // Set up the tables .create table log4jcsv_user_interactive (timenanos:long ,timemillis:long,level:string
     * ,threadid:string,threadname:string,threadpriority:int,formattedmessage:string,loggerfqcn:string,loggername:string,marker:string,thrownproxy:string,source
     * :string,contextmap:string,contextstack:string ) .create table log4jcsv_user_interactive ingestion csv mapping "log4jcsv_user_interactive_mapping" '[
     * {"Column": "timenanos", "Properties": {"Ordinal": "0"}}, {"Column": "timemillis", "Properties": {"Ordinal": "1"}}, {"Column": "level", "Properties":
     * {"Ordinal": "2"}}, {"Column": "threadid", "Properties": {"Ordinal": "3"}}, {"Column": "threadname", "Properties": {"Ordinal": "4"}}, {"Column":
     * "threadpriority", "Properties": {"Ordinal": "5"}}, {"Column": "formattedmessage", "Properties": {"Ordinal": "6"}}, {"Column": "loggerfqcn", "Properties":
     * {"Ordinal": "7"}}, {"Column": "loggername", "Properties": {"Ordinal": "8"}}, {"Column": "marker", "Properties": {"Ordinal": "9"}}, {"Column":
     * "thrownproxy", "Properties": {"Ordinal": "10"}}, {"Column": "source", "Properties": {"Ordinal": "11"}}, {"Column": "contextmap", "Properties":
     * {"Ordinal": "12"}}, {"Column": "contextstack", "Properties": {"Ordinal": "13"}}]' .alter table log4jcsv_user_interactive policy ingestionbatching
     * '{"MaximumBatchingTimeSpan":"00:00:05", "MaximumNumberOfItems": 1, "MaximumRawDataSizeMB": 100}'
     */

    private static Logger initializeLogger() {
        String fileNameAttribute = String.format("%s%s%s", System.getProperty("java.io.tmpdir"), File.separator, "rolling.log");
        String filePatternAttribute = String.format("%s%s%s%s%s", System.getProperty("java.io.tmpdir"), File.separator, "archive",
                File.separator, "rolling-%d{MM-dd-yy-hh-mm}-%i.log");

        String log4jCsvTableName = "log4jcsv_user_interactive";
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.TRACE);
        // create a rolling file appender
        ComponentBuilder<?> kustoStrategy = builder.newComponent("KustoStrategy")
                .addAttribute("clusterIngestUrl", System.getenv("LOG4J2_ADX_INGEST_CLUSTER_URL"))
                .addAttribute("useInteractiveAuth", "true")
                .addAttribute("dbName", System.getenv("LOG4J2_ADX_DB_NAME"))
                .addAttribute("backOffMinSeconds", 5)
                .addAttribute("backOffMaxSeconds", 10)
                .addAttribute("tableName", log4jCsvTableName)
                .addAttribute("logTableMapping", String.format("log4jcsv_user_interactive_mapping", log4jCsvTableName))
                .addAttribute("flushImmediately", "true");
        LayoutComponentBuilder csvPatternBuilder = builder.newLayout("CsvLogEventLayout").addAttribute("delimiter", ",")
                .addAttribute("quoteMode", "ALL");
        ComponentBuilder<?> triggeringPolicy = builder.newComponent("Policies")
                .addComponent(builder.newComponent("CronTriggeringPolicy").addAttribute("schedule", "0 0/1 * 1/1 * ? *")
                        .addAttribute("evaluateOnStartup", true));
        // .addComponent(builder.newComponent("SizeBasedTriggeringPolicy").addAttribute("size", "80 KB"));
        AppenderComponentBuilder appenderBuilder = builder.newAppender("rolling", "RollingFile")
                .addAttribute("fileName", fileNameAttribute)
                .addAttribute("filePattern", filePatternAttribute)
                .addComponent(kustoStrategy).add(csvPatternBuilder).addComponent(triggeringPolicy);
        builder.add(appenderBuilder);
        // create the new logger
        builder.add(
                builder.newLogger("ADXRollingFile", Level.INFO).add(builder.newAppenderRef("rolling"))
                        .addAttribute("additivity", false));
        builder.add(builder.newRootLogger(Level.INFO).add(builder.newAppenderRef("rolling")));
        LoggerContext context = Configurator.initialize(builder.build());
        context.reconfigure();
        return context.getLogger(KustoLog4JSampleAppUserInteractive.class);
    }

    public static void main(String[] args) {
        Logger logger = initializeLogger();
        Runnable loggingTask = () -> {
            logger.trace(".....read_physical_netif: Home list entries returned = 7");
            logger.debug(".....api_reader: api request SENDER");
            logger.info(".....read_physical_netif: index #0, interface VLINK1 has address 129.1.1.1, ifidx 0");
            logger.warn(".....mailslot_create: setsockopt(MCAST_ADD) failed - EDC8116I Address not available.");
            logger.error(".....error_policyAPI: APIInitializeError:  ApiHandleErrorCode = 98BDFB0,  errconnfd = 22");
            logger.fatal(".....fatal_error_timerAPI: APIShutdownError:  ReadBuffer = 98BDFB0,  RSVPGetTSpec = error");
        };
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(loggingTask, 0, 3, TimeUnit.SECONDS);
    }
}
