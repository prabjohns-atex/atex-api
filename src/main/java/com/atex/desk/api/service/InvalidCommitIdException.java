package com.atex.desk.api.service;

public class InvalidCommitIdException extends RuntimeException
{
    public InvalidCommitIdException(String message) {
        super(message);
    }
}
