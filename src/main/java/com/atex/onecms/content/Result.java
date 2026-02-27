package com.atex.onecms.content;

/**
 * Common result class encapsulating a Status and optional data.
 */
public class Result<T> {
    private Status status;
    private T data;

    public Result(Status status, T data) {
        if (status == null) {
            throw new NullPointerException("Status may not be null");
        }
        this.status = status;
        this.data = data;
    }

    public Result(Status status) {
        this(status, null);
    }

    public Result(T data) {
        this(Status.OK, data);
    }

    public Result(Result<T> result) {
        this.status = result.status;
        this.data = result.data;
    }

    public Status getStatus() {
        return status;
    }

    public T getData() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Result<?> result = (Result<?>) o;
        if (status != result.status) return false;
        return data != null ? data.equals(result.data) : result.data == null;
    }

    @Override
    public int hashCode() {
        int result = status.hashCode();
        result = 31 * result + (data != null ? data.hashCode() : 0);
        return result;
    }
}
