// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.azure.kusto.log4j;

import com.microsoft.azure.kusto.data.Client;
import com.microsoft.azure.kusto.data.ClientFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.HttpProxyServerBootstrap;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

import com.microsoft.azure.kusto.data.KustoOperationResult;
import com.microsoft.azure.kusto.data.KustoResultSetTable;
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder;
import com.microsoft.azure.kusto.data.exceptions.DataClientException;
import com.microsoft.azure.kusto.data.exceptions.DataServiceException;

import static org.awaitility.Awaitility.await;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class KustoLog4jE2ETest {

    private static final String databaseName = Objects.requireNonNull(System.getenv("LOG4J2_ADX_DB_NAME"),
            "LOG4J2_ADX_DB_NAME has to be defined as an env var");
    private static final String ingestUrl = Objects.requireNonNull(System.getenv("LOG4J2_ADX_INGEST_CLUSTER_URL"),
            "LOG4J2_ADX_INGEST_CLUSTER_URL has to be defined as an env var");
    private static final String appId = Objects.requireNonNull(System.getenv("LOG4J2_ADX_APP_ID"),
            "LOG4J2_ADX_APP_ID has to be defined as an env var");
    private static final String appKey = Objects.requireNonNull(System.getenv("LOG4J2_ADX_APP_KEY"),
            "LOG4J2_ADX_APP_KEY has to be defined as an env var");
    private static final String tenantId = Objects.requireNonNull(System.getenv("LOG4J2_ADX_TENANT_ID"),
            "LOG4J2_ADX_TENANT_ID has to be defined as an env var");
    private static final String log4jCsvTableName = String.format("log4jcsv_%d", System.currentTimeMillis());
    private static final String fileNameAttribute = String.format("%s%s%s", System.getProperty("java.io.tmpdir"), File.separator, "rolling.log");
    private static final String filePatternAttribute = String.format("%s%s%s%s%s", System.getProperty("java.io.tmpdir"), File.separator, "archive",
            File.separator,
            "rolling-%d{MM-dd-yy-hh-mm}-%i.log");
    private static Logger LOGGER;
    private static Client queryClient;

    private static HttpProxyServer proxy;

    private static LoggerContext context;

    @BeforeAll
    public static void setUp() {
        setupAndStartProxy();
        configureLog4J();

        // ***** Temp *******
        LOGGER = LogManager.getLogger(KustoLog4jE2ETest.class);

        System.out.println("----------------->>>>>>>>>>>>> " + ingestUrl + " : ENV : " + System.getenv("LOG4J2_ADX_INGEST_CLUSTER_URL"));
        LOGGER.warn("LOGGER ----------------->>>>>>>>>>>>> " + ingestUrl + " : ENV : " + System.getenv("LOG4J2_ADX_INGEST_CLUSTER_URL"));
        //

        // Refer: https://github.com/Azure/azure-kusto-java/pull/268/. Creating query client from ingest url
        String queryEndpoint = ingestUrl.replaceFirst("ingest-", "");
        LOGGER.info("Using query endpoint for tests : {} ", queryEndpoint);
        ConnectionStringBuilder engineCsb = ConnectionStringBuilder.createWithAadApplicationCredentials(queryEndpoint, appId, appKey,
                tenantId);
        try {
            queryClient = ClientFactory.createClient(engineCsb);
        } catch (URISyntaxException ex) {
            Assertions.fail("Failed to create query client", ex);
        }
        createLogTableAndPolicy();
    }

    private static void createLogTableAndPolicy() {
        try {
            // To be sure drop the table
            queryClient.executeToJsonResult(databaseName, String.format(".drop table %s ifexists", log4jCsvTableName));
            // Create the table with columns
            String tableColumns = new String(Files.readAllBytes(
                    Paths.get(System.getProperty("user.dir"), "src", "test", "resources", "csv_columns.txt")));
            queryClient.execute(databaseName, String.format(".create table %s %s", log4jCsvTableName, tableColumns));
            // create a policy for batching
            String ingestionPolicy = new String(Files.readAllBytes(
                    Paths.get(System.getProperty("user.dir"), "src", "test", "resources", "table_policy.txt")));
            queryClient.execute(databaseName,
                    String.format(".alter table %s policy ingestionbatching @'%s'", log4jCsvTableName,
                            ingestionPolicy));
            // create a mapping
            String tableCsvMapping = new String(Files.readAllBytes(
                    Paths.get(System.getProperty("user.dir"), "src", "test", "resources", "mappings.txt")));
            queryClient.execute(databaseName,
                    String.format(".create table %s ingestion csv mapping '%s_mapping' '%s' ", log4jCsvTableName,
                            log4jCsvTableName, tableCsvMapping));
        } catch (Exception ex) {
            Assertions.fail("Failed to drop and create new table", ex);
        }
    }

    @AfterAll
    public static void tearDown() {
        context.close();
        Path archiveDirectory = Paths.get(filePatternAttribute).getParent();
        try (Stream<Path> paths = Files.walk(archiveDirectory)) {
            queryClient.executeToJsonResult(databaseName, String.format(".drop table %s ifexists", log4jCsvTableName));
            paths.map(Path::toFile).forEach(File::deleteOnExit);
            File rollingFileToDelete = new File(fileNameAttribute);
            rollingFileToDelete.deleteOnExit();
            KustoClientInstance.getInstance().close();
            shutdownProxy();
        } catch (Exception ex) {
            LOGGER.error("Failed to run clean up tasks!", ex);
            Assertions.fail("Failed to run clean up tasks!", ex);
        }
    }

    public static void configureLog4J() {
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.INFO);
        // create a rolling file appender
        ComponentBuilder<?> kustoStrategy = builder.newComponent("KustoStrategy")
                .addAttribute("clusterIngestUrl", ingestUrl)
                .addAttribute("appId", appId)
                .addAttribute("appKey", appKey).addAttribute("appTenant", tenantId)
                .addAttribute("dbName", databaseName)
                .addAttribute("backOffMinSeconds", 5)
                .addAttribute("backOffMaxSeconds", 10)
                .addAttribute("tableName", log4jCsvTableName)
                .addAttribute("proxyUrl", String.format("http://%s:%d", proxy.getListenAddress().getHostName(),
                        proxy.getListenAddress().getPort()))
                .addAttribute("logTableMapping", String.format("%s_mapping", log4jCsvTableName))
                .addAttribute("flushImmediately", "true");
        LayoutComponentBuilder csvPatternBuilder = builder.newLayout("CsvLogEventLayout").addAttribute("delimiter", ",")
                .addAttribute("quoteMode", "ALL");
        ComponentBuilder<?> triggeringPolicy = builder.newComponent("Policies")
                // .addComponent(builder.newComponent("TimeBasedTriggeringPolicy").addAttribute("interval", 5)
                // .addAttribute("modulate", true));
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
        context = Configurator.initialize(builder.build());
    }

    private static void setupAndStartProxy() {
        HttpProxyServerBootstrap httpProxyServerBootstrap = DefaultHttpProxyServer.bootstrap()
                .withAllowLocalOnly(true) // only run on localhost
                .withAuthenticateSslClients(false); // we aren't checking client certs
        // Start the proxy server
        proxy = httpProxyServerBootstrap.start();
    }

    private static void shutdownProxy() {
        proxy.stop();
    }

    @Test
    void e2eLogsTest() {
        String logInfoMessage = "log4j info test";
        String logWarnMessage = "log4j warn test";
        String logErrorMessage = "log4j error test";
        int maxLoops = 100;
        // shutdownProxy();
        try {
            for (int i = 0; i < maxLoops; i++) {
                LOGGER.info("{} - {}", i, logInfoMessage);
                LOGGER.warn("{} - {}", i, logWarnMessage);
                LOGGER.error(String.format("%s-%s", logErrorMessage, i),
                        new RuntimeException(i + " - A Random exception"));
            }
            await().atMost(90, TimeUnit.SECONDS).until(ingestionCompleted());
            String[] levelsToCheck = new String[] {logInfoMessage, logWarnMessage, logErrorMessage};
            for (String logLevel : levelsToCheck) {
                String queryToExecute = String.format("%s | where formattedmessage has '%s'| summarize dcount(formattedmessage)",
                        log4jCsvTableName, logLevel);
                KustoOperationResult queryResults = queryClient.execute(databaseName, queryToExecute);
                KustoResultSetTable mainTableResult = queryResults.getPrimaryResults();
                mainTableResult.next();
                int countsRetrieved = mainTableResult.getInt(0);
                LOGGER.warn("Query {} yielded count {} ", queryToExecute, countsRetrieved);
                Assertions.assertEquals(maxLoops, countsRetrieved,
                        String.format("For %s , counts did not match", logLevel));
            }
        } catch (DataServiceException | DataClientException e) {
            Assertions.fail("Error querying counts from table", e);
        }
    }

    private Callable<Boolean> ingestionCompleted() {
        return () -> {
            String queryToExecute = String.format("%s | where formattedmessage == '99 - %s'|count",
                    log4jCsvTableName, "log4j info test");
            KustoOperationResult queryResults = queryClient.execute(databaseName, queryToExecute);
            KustoResultSetTable mainTableResult = queryResults.getPrimaryResults();
            mainTableResult.next();
            int countsRetrieved = mainTableResult.getInt(0);
            return countsRetrieved == 1;
        };
    }
}
