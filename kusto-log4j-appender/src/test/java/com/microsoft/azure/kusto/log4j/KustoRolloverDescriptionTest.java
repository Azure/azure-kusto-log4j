// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.azure.kusto.log4j;

import org.apache.logging.log4j.core.appender.rolling.RolloverDescription;
import org.apache.logging.log4j.core.appender.rolling.action.Action;
import org.apache.logging.log4j.core.appender.rolling.action.FileRenameAction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;

class KustoRolloverDescriptionTest {
    private static final String FILE_SOURCE_ATTRIBUTE = String.format("%s%s%s", System.getProperty("java.io.tmpdir"), File.separator, "delegate.log");
    private static final String FILE_TARGET_ATTRIBUTE = String.format("%s%s%s", System.getProperty("java.io.tmpdir"), File.separator, "delegate-archive.log");

    private static final FileRenameAction DELEGATE_RENAME_ACTION = new FileRenameAction(new File(FILE_SOURCE_ATTRIBUTE),
            new File(FILE_TARGET_ATTRIBUTE), true);

    private static KustoRolloverDescription kustoRolloverDescription;

    @BeforeAll
    public static void setUp() {
        RolloverDescription delegate = mock(RolloverDescription.class);
        when(delegate.getSynchronous()).thenReturn(DELEGATE_RENAME_ACTION);
        when(delegate.getActiveFileName()).thenReturn(FILE_TARGET_ATTRIBUTE);
        kustoRolloverDescription = new KustoRolloverDescription(delegate, FILE_TARGET_ATTRIBUTE);
    }

    @Test
    void getActiveFileName() {
        // The active file is the file that is being renamed!
        assertEquals(FILE_TARGET_ATTRIBUTE, kustoRolloverDescription.getActiveFileName());
    }

    @Test
    void getAppend() {
        // Is hardcoded to true , should return the same
        assertTrue(kustoRolloverDescription.getAppend());
    }

    @Test
    void getSynchronous() {
        Action action = kustoRolloverDescription.getSynchronous();
        // A Kusto sync options should have been initiated
        assertTrue(action instanceof KustoFlushAction);
    }
}
