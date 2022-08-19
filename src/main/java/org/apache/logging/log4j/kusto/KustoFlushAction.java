package org.apache.logging.log4j.kusto;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.rolling.action.Action;
import org.apache.logging.log4j.status.StatusLogger;

import com.microsoft.azure.kusto.ingest.IngestClient;
import com.microsoft.azure.kusto.ingest.IngestionProperties;
import com.microsoft.azure.kusto.ingest.exceptions.IngestionClientException;
import com.microsoft.azure.kusto.ingest.exceptions.IngestionServiceException;
import com.microsoft.azure.kusto.ingest.result.IngestionResult;
import com.microsoft.azure.kusto.ingest.source.StreamSourceInfo;
import com.microsoft.azure.storage.StorageException;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;

class KustoFlushAction implements Action {

    private static final Logger LOGGER = StatusLogger.getLogger();

    private final Action delegate;
    private final String fileName;

    KustoFlushAction(final Action delegate, final String fileName) {
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
            LOGGER.warn("************************** {}", Files.readAllLines(Paths.get(fileName)).get(0));
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
