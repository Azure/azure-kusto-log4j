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

    private static final RetryPolicy<Object> INGESTION_RETRY_POLICY = RetryPolicy.builder()
            .handle(IngestionClientException.class, IngestionServiceException.class, StorageException.class, URISyntaxException.class)
            .withBackoff(1, 60, ChronoUnit.MINUTES).withMaxRetries(3).build();
    private static final Logger LOGGER = StatusLogger.getLogger();
    private final Action delegate;
    private final String fileName;
    private final IngestClient ingestClient;
    private final IngestionProperties ingestionProperties;

    KustoFlushAction(final Action delegate, final String fileName, final IngestClient ingestClient,
            final IngestionProperties ingestionProperties) {
        this.delegate = delegate;
        this.fileName = fileName;
        this.ingestClient = ingestClient;
        this.ingestionProperties = ingestionProperties;
    }

    @Override
    public void run() {
        delegate.run();
    }

    @Override
    public boolean execute() throws IOException {
        Failsafe.with(INGESTION_RETRY_POLICY).run(this::ingestLogs);
        // } catch (Throwable e) {
        // // In the event that there is an error , move this file to a back-out directory from where it
        // // will be retried
        // String logDirectory = rolledFile.getParent();
        // String backOutPath = String.format("backout/%d-%s", System.currentTimeMillis(), rolledFile.getName());
        // Files.move(Paths.get(rolledFile.getPath()), Paths.get(logDirectory, backOutPath), ATOMIC_MOVE);
        // LOGGER.error("Error ingesting file {} with exception ", fileName, e);
        // }
        return delegate.execute();
    }

    private void ingestLogs()
            throws IngestionClientException, IngestionServiceException, StorageException, URISyntaxException, IOException {
        try (InputStream inputStream = Files.newInputStream(Paths.get(this.fileName))) {
            StreamSourceInfo streamSourceInfo = new StreamSourceInfo(inputStream);
            IngestionResult ingestionResult = ingestClient.ingestFromStream(streamSourceInfo, ingestionProperties);
            ingestionResult.getIngestionStatusCollection().forEach(ingestionStatus -> {
                LOGGER.warn("Ingestion status {} , Ingestion failure status {} , Ingestion error code {} ", ingestionStatus.getStatus(),
                        ingestionStatus.getFailureStatus(), ingestionStatus.getErrorCode());
            });

        }
    }

    @Override
    public void close() {
        delegate.close();
        try {
            ingestClient.close();
        } catch (IOException e) {
            LOGGER.warn("Error closing ingest client", e);
        }
    }

    @Override
    public boolean isComplete() {
        return delegate.isComplete();
    }

    // private void moveFileToBackout(String logDirectory, String fileToBackout,boolean isRolledFile) throws
    // IOException{
    // if(isRolledFile){
    // String backOutPath=String.format("%s%sbackout%s%d-%s",logDirectory,File.separator,File.separator,
    // System.currentTimeMillis(),fileToBackout);
    // LOGGER.error("Moving file to backout {} , {} ", fileName , backOutPath);
    // Files.createDirectories(Paths.get(String.format("%s%sbackout",logDirectory,File.separator)));
    // Files.move(Paths.get(rolledFile.getPath()),Paths.get(backOutPath),ATOMIC_MOVE);
    // }
    // }
    // private String[] getFilesToRetry(){
    // return null;
    // }

}
