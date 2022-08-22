package org.apache.logging.log4j.kusto;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

import com.microsoft.azure.kusto.data.ClientImpl;
import com.microsoft.azure.kusto.data.KustoOperationResult;
import com.microsoft.azure.kusto.data.KustoResultSetTable;
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder;
import com.microsoft.azure.kusto.data.exceptions.DataClientException;
import com.microsoft.azure.kusto.data.exceptions.DataServiceException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

public class KustoLog4jE2ETest {

    private static final Logger LOGGER;
    private static final String databaseName = Objects.requireNonNull(System.getenv("dbName"), "dbName has to be defined as an env var");
    private static final String ingestUrl = Objects.requireNonNull(System.getenv("clusterUrl"), "clusterUrl has to be defined as an env var");
    private static final String appId = Objects.requireNonNull(System.getenv("appId"), "appId has to be defined as an env var");
    private static final String appKey = Objects.requireNonNull(System.getenv("appKey"), "appKey has to be defined as an env var");
    private static final String tenantId = Objects.requireNonNull(System.getenv("appTenant"), "appTenant has to be defined as an env var");
    private static final String dmClusterPath = Objects.requireNonNull(System.getenv("engineUrl"),
            "engineUrl has to be defined as an env var");
    private static final String log4jCsvTableName = String.format("log4jcsv_%d", System.currentTimeMillis());
    private static ClientImpl queryClient;

    static {
        configureLog4J();
        LOGGER = LogManager.getLogger(KustoLog4jE2ETest.class);
    }

    @BeforeAll
    public static void setUp() {
        ConnectionStringBuilder engineCsb = ConnectionStringBuilder.createWithAadApplicationCredentials(dmClusterPath, appId, appKey,
                tenantId);
        try {
            queryClient = new ClientImpl(engineCsb);
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
            String tableColumns = new String(Files.readAllBytes(Paths.get("src/test/resources/", "csv_columns.txt")));
            queryClient.execute(databaseName, String.format(".create table %s %s", log4jCsvTableName, tableColumns));
            // create a policy for batching
            String ingestionPolicy = new String(Files.readAllBytes(Paths.get("src/test/resources/", "table_policy.txt")));
            queryClient.execute(databaseName,
                    String.format(".alter table %s policy ingestionbatching @'%s'", log4jCsvTableName,
                            ingestionPolicy));
        } catch (Exception ex) {
            Assertions.fail("Failed to drop and create new table", ex);
        }
    }

    @AfterAll
    public static void tearDown() {
        try {
            // queryClient.executeToJsonResult(databaseName, String.format(".drop table %s ifexists", log4jCsvTableName));
        } catch (Exception ex) {
            Assertions.fail("Failed to drop table", ex);
        }
    }

    public static void configureLog4J() {
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.INFO);
        String tmpdir = System.getProperty("java.io.tmpdir");
        // create a rolling file appender
        ComponentBuilder<?> kustoStrategy = builder.newComponent("KustoStrategy").addAttribute("clusterPath", ingestUrl)
                .addAttribute("appId", appId)
                .addAttribute("appKey", appKey).addAttribute("appTenant", tenantId)
                .addAttribute("dbName", databaseName)
                .addAttribute("tableName", log4jCsvTableName);
        LayoutComponentBuilder csvPatternBuilder = builder.newLayout("CsvLogEventLayout").addAttribute("delimiter", ",")
                .addAttribute("quoteMode", "ALL");
        ComponentBuilder<?> triggeringPolicy = builder.newComponent("Policies")
                .addComponent(builder.newComponent("TimeBasedTriggeringPolicy").addAttribute("interval", 10)
                        .addAttribute("modulate", true))
                .addComponent(builder.newComponent("SizeBasedTriggeringPolicy").addAttribute("size", "100 KB"));
        AppenderComponentBuilder appenderBuilder = builder.newAppender("rolling", "RollingFile")
                .addAttribute("fileName", String.format("%s%s", tmpdir, "rolling.log"))
                .addAttribute("filePattern", String.format("%s%s%s%s", tmpdir, "archive", File.separator,
                        "rolling-%d{MM-dd-yy-hh-mm-ss}-%i.log"))
                .addComponent(kustoStrategy).add(csvPatternBuilder).addComponent(triggeringPolicy);
        builder.add(appenderBuilder);
        // create the new logger
        builder.add(
                builder.newLogger("ADXRollingFile", Level.INFO).add(builder.newAppenderRef("rolling"))
                        .addAttribute("additivity", false));
        builder.add(builder.newRootLogger(Level.INFO).add(builder.newAppenderRef("rolling")));
        try {
            builder.writeXmlConfiguration(System.out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Configurator.initialize(builder.build());
    }

    @Test
    public void e2eLogsTest() {
        String logInfoMessage = "log4j info test";
        String logWarnMessage = "log4j warn test";
        String logErrorMessage = "log4j error test";
        int maxLoops = 10000;
        for (int i = 0; i < maxLoops; i++) {
            LOGGER.info("{} - {}", i, logInfoMessage);
            LOGGER.warn("{} - {}", i, logWarnMessage);
            LOGGER.error(logErrorMessage, new RuntimeException(i + " - A Random exception"));
        }
        try {
            Thread.sleep(2 * 60000);
            String[] levelsToCheck = new String[] {logInfoMessage, logWarnMessage, logErrorMessage};
            for (String logLevel : levelsToCheck) {
                KustoOperationResult queryResults = queryClient.execute(databaseName,
                        String.format("%s | where formattedmessage has '%s'| count", log4jCsvTableName, logLevel));
                KustoResultSetTable mainTableResult = queryResults.getPrimaryResults();
                mainTableResult.next();
                Assertions.assertEquals(maxLoops, mainTableResult.getInt(0),
                        String.format("For %s , counts did not match", logLevel));
            }
        } catch (InterruptedException | DataServiceException | DataClientException e) {
            Assertions.fail("Error querying counts from table", e);
        }
    }
}
