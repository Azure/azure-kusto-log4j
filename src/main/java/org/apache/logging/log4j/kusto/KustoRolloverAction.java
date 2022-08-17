package org.apache.logging.log4j.kusto;

import org.apache.logging.log4j.core.appender.rolling.RolloverDescription;
import org.apache.logging.log4j.core.appender.rolling.action.Action;

import com.microsoft.azure.kusto.ingest.IngestClient;
import com.microsoft.azure.kusto.ingest.IngestionProperties;

public class KustoRolloverAction {

    // Wrapper class only for setting a hook to getSynchronous().execute()
    static class KustoRolloverDescription implements RolloverDescription {

        private final RolloverDescription delegate;
        private final IngestClient ingestClient;
        private final IngestionProperties ingestionProperties;

        KustoRolloverDescription(final RolloverDescription delegate, final IngestClient ingestClient,
                final IngestionProperties ingestionProperties) {
            this.delegate = delegate;
            this.ingestClient = ingestClient;
            this.ingestionProperties = ingestionProperties;
        }

        @Override
        public String getActiveFileName() {
            return delegate.getActiveFileName();
        }

        @Override
        public boolean getAppend() {
            return true;
        }

        // The synchronous action is for renaming, here we want to hook
        @Override
        public Action getSynchronous() {
            Action delegateAction = delegate.getSynchronous();
            if (delegateAction == null) {
                return null;
            }
            return new KustoFlushAction(delegateAction, delegate.getActiveFileName(), ingestClient, ingestionProperties);
        }

        // The asynchronous action is for compressing, we don't need to hook here
        @Override
        public Action getAsynchronous() {
            return delegate.getAsynchronous();
        }
    }
}
