// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package org.apache.logging.log4j.kusto;

import org.apache.logging.log4j.core.appender.rolling.action.FileRenameAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.microsoft.azure.kusto.ingest.exceptions.IngestionClientException;
import com.microsoft.azure.kusto.ingest.exceptions.IngestionServiceException;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.failsafe.RetryPolicy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class KustoFlushActionTest {

    private static final String FILE_SOURCE_ATTRIBUTE = String.format("%s%s", System.getProperty("java.io.tmpdir"), "delegate.log");
    private static final String FILE_TARGET_ATTRIBUTE = String.format("%s%s", System.getProperty("java.io.tmpdir"), "delegate-archive.log");

    private static final FileRenameAction DELEGATE_RENAME_ACTION = new FileRenameAction(new File(FILE_SOURCE_ATTRIBUTE),
            new File(FILE_TARGET_ATTRIBUTE), true);
    KustoFlushAction kustoFlushAction;

    KustoClientInstance kustoClientInstance;

    @BeforeEach
    public void beforeEach() {
        kustoClientInstance = mock(KustoClientInstance.class);
        kustoFlushAction = new KustoFlushAction(DELEGATE_RENAME_ACTION, FILE_TARGET_ATTRIBUTE);
        try {
            Files.copy(Paths.get(System.getProperty("user.dir"), "src", "test", "resources", "delegate.log"), Paths.get(FILE_SOURCE_ATTRIBUTE),
                    REPLACE_EXISTING);
        } catch (IOException e) {
            fail("Cannot copy delegate.log file for test , all tests will fail subsequently",e);
        }
    }

    @Test
    void executeSuccess() {
        ArgumentCaptor<String> fileNameCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.doNothing().when(kustoClientInstance).ingestFile(fileNameCaptor.capture());
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

    @Test
    void executeFailure() throws IngestionClientException, IOException, IngestionServiceException {
        String backedOutPath = String.format("%s%s%s%s", System.getProperty("java.io.tmpdir"), "backout", File.separator,
                "delegate-archive.log");
        Path backoutFilePath = Paths.get(backedOutPath);
        Files.deleteIfExists(backoutFilePath);
        ArgumentCaptor<String> fileNameCaptor = ArgumentCaptor.forClass(String.class);
        kustoClientInstance.ingestionRetryPolicy = RetryPolicy.builder().handle(RuntimeException.class)
                .withBackoff(100, 500, ChronoUnit.MILLIS)
                .withMaxRetries(2)
                .build();
        Mockito.doCallRealMethod().when(kustoClientInstance).backOutFile(anyString());
        Mockito.doCallRealMethod().when(kustoClientInstance).ingestFile(anyString());
        Mockito.doThrow(new IngestionServiceException("An ingest exception occurred")).when(kustoClientInstance)
                .ingestLogs(fileNameCaptor.capture());
        try (MockedStatic<KustoClientInstance> staticSingleton = mockStatic(KustoClientInstance.class)) {
            staticSingleton.when(KustoClientInstance::getInstance).thenReturn(kustoClientInstance);
            kustoFlushAction.execute();
            AtomicBoolean isCompleted = new AtomicBoolean();
            await().atMost(5, TimeUnit.SECONDS).until(isActionCompleted());
            assertTrue(Files.exists(backoutFilePath));
            // Ingestion complete backed-out
            assertTrue(kustoFlushAction.isComplete());
        } catch (IOException e) {
            fail("IOException performing ingestFile() test", e);
        }
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

    private Callable<Boolean> isActionCompleted() {
        return () -> kustoFlushAction.isComplete();
    }
}
