// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package org.apache.logging.log4j.ext.kusto;

import org.apache.logging.log4j.core.appender.rolling.action.AbstractAction;
import org.apache.logging.log4j.core.appender.rolling.action.Action;

import java.io.IOException;

class KustoFlushAction extends AbstractAction {

    private final Action delegate;
    private final String fileName;

    private boolean ingestComplete;

    public KustoFlushAction(final Action delegate, final String fileName) {
        this.delegate = delegate;
        this.fileName = fileName;
        this.ingestComplete = false;
    }

    @Override
    public boolean execute() throws IOException {
        boolean execute = delegate.execute();
        if (execute) {
            KustoClientInstance.getInstance().ingestFile(fileName);
        }
        // reaches here on completion , else IOException gets thrown
        ingestComplete = true;
        return execute;
    }

    @Override
    public synchronized void close() {
        KustoClientInstance.getInstance().close();
        delegate.close();
    }

    @Override
    public boolean isComplete() {
        return this.ingestComplete;
    }
}
