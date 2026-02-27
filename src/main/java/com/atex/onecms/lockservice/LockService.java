package com.atex.onecms.lockservice;
public interface LockService {
    boolean lock(String path, String owner, long timeout) throws Exception;
    boolean unlock(String path, String owner) throws Exception;
    boolean isLocked(String path) throws Exception;
}
