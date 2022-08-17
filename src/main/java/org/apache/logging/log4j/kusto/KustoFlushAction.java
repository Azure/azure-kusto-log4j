package org.apache.logging.log4j.kusto;

import org.apache.logging.log4j.core.appender.rolling.action.Action;

import com.microsoft.azure.kusto.ingest.IngestClient;
import com.microsoft.azure.kusto.ingest.IngestionProperties;
import com.microsoft.azure.kusto.ingest.result.IngestionResult;
import com.microsoft.azure.kusto.ingest.source.FileSourceInfo;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class KustoFlushAction implements Action {
    // Wrapper class only for setting a hook to execute()

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
        File rolledFile = new File(fileName);
        try {
            String logDirectory = rolledFile.getParent();
            FileSourceInfo fileSourceInfo = new FileSourceInfo(rolledFile.getPath(), 0);
            IngestionResult ingestionResult = ingestClient.ingestFromFile(fileSourceInfo, ingestionProperties);
            ingestionResult.getIngestionStatusCollection().forEach(ingestionStatus -> {
                KustoStrategy.LOGGER.debug("Ingestion status {} , Ingestion failure status {} , Ingestion error code {} ",
                        ingestionStatus.getStatus(), ingestionStatus.getFailureStatus(), ingestionStatus.getErrorCode());
            });
        } catch (Throwable e) {
            // In the event that there is an error , move this file to a back-out directory from where it
            // will be retried
            String logDirectory = rolledFile.getParent();
            String backOutPath = String.format("backout/%d-%s", System.currentTimeMillis(), rolledFile.getName());
            Files.move(Paths.get(rolledFile.getPath()), Paths.get(logDirectory, backOutPath), ATOMIC_MOVE);
            KustoStrategy.LOGGER.error("Error ingesting file {} with exception ", fileName, e);
        }
        return delegate.execute();
    }

    @Override
    public void close() {
        delegate.close();
        try {
            ingestClient.close();
        } catch (IOException e) {
            KustoStrategy.LOGGER.warn("Error closing ingest client", e);
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
