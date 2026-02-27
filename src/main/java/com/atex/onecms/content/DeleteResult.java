package com.atex.onecms.content;

/**
 * Contains information about the outcome of a delete operation.
 */
public class DeleteResult extends Result<Void> {

    public DeleteResult(Status status) {
        super(status);
    }

    @Override
    public Status getStatus() {
        return super.getStatus();
    }
}
