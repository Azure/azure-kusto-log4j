// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package org.apache.logging.log4j.kusto;

import org.apache.logging.log4j.core.appender.rolling.RolloverDescription;
import org.apache.logging.log4j.core.appender.rolling.action.Action;

class KustoRolloverDescription implements RolloverDescription {

    private final RolloverDescription delegate;

    private final String fileName;

    KustoRolloverDescription(final RolloverDescription delegate, String fileName) {
        this.delegate = delegate;
        this.fileName = fileName;
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
        return new KustoFlushAction(delegateAction, this.fileName);
    }

    // The asynchronous action is for compressing, we don't need to hook here
    @Override
    public Action getAsynchronous() {
        return delegate.getAsynchronous();
    }
}
