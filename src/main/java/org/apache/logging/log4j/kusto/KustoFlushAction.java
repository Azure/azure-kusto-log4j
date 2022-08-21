package org.apache.logging.log4j.kusto;

import org.apache.logging.log4j.core.appender.rolling.action.Action;

import java.io.IOException;

class KustoFlushAction implements Action {

    private final Action delegate;
    private final String fileName;

    public KustoFlushAction(final Action delegate, final String fileName) {
        this.delegate = delegate;
        this.fileName = fileName;
    }

    @Override
    public void run() {
        delegate.run();
    }

    @Override
    public boolean execute() throws IOException {
        boolean execute = delegate.execute();
        if (execute) {
            KustoClientInstance.getInstance().ingestFile(fileName);
        }
        return execute;
    }

    @Override
    public void close() {
        KustoClientInstance.getInstance().close();
        delegate.close();
    }

    @Override
    public boolean isComplete() {
        return delegate.isComplete();
    }
}
