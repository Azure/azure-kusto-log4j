package com.microsoft.azure.kusto.log4j.sample;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sample Application which continuously produces application logs and these logs are then ingested into Azure Data Explorer.
 * The ingestion of logs happen through a custom strategy implementation of RollingFileAppender in log4j which is configured in the log4j2.xml/json/properties/yaml file.
 * The log4j2 configuration file ie log4j2.xml/log4j2.json/log4j2.properties/log4j2.yaml needs to present in the classpath
 */
public class KustoLog4JSampleApp {
    private static final Logger logger = LogManager.getLogger(KustoLog4JSampleApp.class);

    public static void main(String[] args) {

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
