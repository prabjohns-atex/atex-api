package com.atex.desk.api.auth;

public class InvalidTokenException extends Exception
{
    public InvalidTokenException(String message)
    {
        super(message);
    }
}
