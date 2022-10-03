// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package org.apache.logging.log4j.ext.kusto;

import org.apache.logging.log4j.core.appender.rolling.action.FileRenameAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.microsoft.azure.kusto.data.exceptions.DataServiceException;
import com.microsoft.azure.kusto.data.exceptions.KustoDataExceptionBase;
import com.microsoft.azure.kusto.ingest.exceptions.IngestionClientException;
import com.microsoft.azure.kusto.ingest.exceptions.IngestionServiceException;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.logging.log4j.ext.kusto.Constants.INGESTION_RETRIES;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

class KustoFlushActionTest {

    private static final String FILE_SOURCE_ATTRIBUTE = String.format("%s%s%s", System.getProperty("java.io.tmpdir"), File.separator, "delegate.log");
    private static final String FILE_TARGET_ATTRIBUTE = String.format("%s%s%s", System.getProperty("java.io.tmpdir"), File.separator, "delegate-archive.log");

    private static final FileRenameAction DELEGATE_RENAME_ACTION = new FileRenameAction(new File(FILE_SOURCE_ATTRIBUTE),
            new File(FILE_TARGET_ATTRIBUTE), true);
    KustoFlushAction kustoFlushAction;

    KustoClientInstance kustoClientInstance;

    @BeforeEach
    public void beforeEach() {
        kustoClientInstance = mock(KustoClientInstance.class);
        kustoFlushAction = new KustoFlushAction(DELEGATE_RENAME_ACTION, FILE_TARGET_ATTRIBUTE);
        try {
            Files.copy(Paths.get(System.getProperty("user.dir"), "src", "test", "resources", "delegate.log"),
                    Paths.get(FILE_SOURCE_ATTRIBUTE),
                    REPLACE_EXISTING);
        } catch (IOException e) {
            fail("Cannot copy delegate.log file for test , all tests will fail subsequently", e);
        }
    }

    @Test
    void executeSuccess() {
        ArgumentCaptor<String> fileNameCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.doNothing().when(kustoClientInstance).ingestRolledFile(fileNameCaptor.capture());
        try (MockedStatic<KustoClientInstance> staticSingleton = mockStatic(KustoClientInstance.class)) {
            staticSingleton.when(KustoClientInstance::getInstance).thenReturn(kustoClientInstance);
            kustoFlushAction.execute();
            staticSingleton.verify(KustoClientInstance::getInstance);
            assertTrue(Files.exists(Paths.get(FILE_TARGET_ATTRIBUTE)));
            assertTrue(kustoFlushAction.isComplete());
            assertEquals(FILE_TARGET_ATTRIBUTE, fileNameCaptor.getValue());
            // on execute the file will be renamed
        } catch (IOException e) {
            fail("IOException performing ingestFile() test");
        }
    }

    @ParameterizedTest
    @CsvSource({"false,3", "true,1"})
    void executeFailure(boolean isPermanent, int retries) throws IngestionClientException, IOException, IngestionServiceException {
        String backedOutPath = String.format("%s%s%s%s%s", System.getProperty("java.io.tmpdir"), File.separator, "backout",
                File.separator,
                "delegate-archive.log");
        Path backoutFilePath = Paths.get(backedOutPath);
        Files.deleteIfExists(backoutFilePath);
        ArgumentCaptor<String> fileNameCaptor = ArgumentCaptor.forClass(String.class);
        RetryConfig retryConfig = RetryConfig.custom()
                .intervalFunction(IntervalFunction.ofExponentialBackoff(1,
                        IntervalFunction.DEFAULT_MULTIPLIER, 5))
                .retryOnException(this::isTransientException)
                .failAfterMaxAttempts(false).maxAttempts(3)
                .build();
        Exception exceptionToThrow = isPermanent ? new RuntimeException(new DataServiceException("file", "Bad mapping", true))
                : new IngestionServiceException("An ingestion exception has occurred");
        kustoClientInstance.ingestionRetry = Retry.of(INGESTION_RETRIES, retryConfig);
        Mockito.doCallRealMethod().when(kustoClientInstance).backOutFile(anyString());
        Mockito.doCallRealMethod().when(kustoClientInstance).ingestRolledFile(anyString());
        when(kustoClientInstance.ingestLogs(fileNameCaptor.capture())).thenThrow(exceptionToThrow);
        try (MockedStatic<KustoClientInstance> staticSingleton = mockStatic(KustoClientInstance.class)) {
            staticSingleton.when(KustoClientInstance::getInstance).thenReturn(kustoClientInstance);
            kustoFlushAction.execute();
            await().atMost(10, TimeUnit.SECONDS).until(isActionCompleted());
            assertTrue(Files.exists(backoutFilePath));
            // Ingestion complete backed-out
            assertTrue(kustoFlushAction.isComplete());
        } catch (IOException e) {
            fail("IOException performing ingestFile() test", e);
        }
        verify(kustoClientInstance, times(retries)).ingestLogs(anyString());
    }

    @Test
    void close() {
        Mockito.doNothing().when(kustoClientInstance).close();
        try (MockedStatic<KustoClientInstance> staticSingleton = mockStatic(KustoClientInstance.class)) {
            staticSingleton.when(KustoClientInstance::getInstance).thenReturn(kustoClientInstance);
            kustoFlushAction.close();
            staticSingleton.verify(KustoClientInstance::getInstance);
            assertFalse(kustoFlushAction.isComplete());
        }
    }

    private boolean isTransientException(Throwable exception) {
        Throwable innerException = exception.getCause();
        return !(innerException instanceof KustoDataExceptionBase &&
                ((KustoDataExceptionBase) innerException).isPermanent());
    }

    private Callable<Boolean> isActionCompleted() {
        return () -> kustoFlushAction.isComplete();
    }
}
