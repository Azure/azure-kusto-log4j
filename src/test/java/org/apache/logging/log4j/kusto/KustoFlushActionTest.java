package org.apache.logging.log4j.kusto;

import org.apache.logging.log4j.core.appender.rolling.action.FileRenameAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

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
            Files.copy(Paths.get("src/test/resources/delegate.log"), Paths.get(FILE_SOURCE_ATTRIBUTE),
                    REPLACE_EXISTING);
        } catch (IOException e) {
            fail("Cannot copy delegate.log file for test , all tests will fail subsequently");
        }
    }

    @Test
    void execute() {
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
    void close() {
        Mockito.doNothing().when(kustoClientInstance).close();
        try (MockedStatic<KustoClientInstance> staticSingleton = mockStatic(KustoClientInstance.class)) {
            staticSingleton.when(KustoClientInstance::getInstance).thenReturn(kustoClientInstance);
            kustoFlushAction.close();
            staticSingleton.verify(KustoClientInstance::getInstance);
            assertFalse(kustoFlushAction.isComplete());
        }
    }
}
