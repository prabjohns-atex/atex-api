package com.atex.onecms.lockservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LockServiceUtil {
    private static final Logger LOG = LoggerFactory.getLogger(LockServiceUtil.class);

    private static LockService lockService;

    public static void setLockService(LockService service) {
        lockService = service;
    }

    public static LockService getLockService() throws LockServiceMissingException {
        if (lockService == null) {
            throw new LockServiceMissingException("LockService not configured");
        }
        return lockService;
    }

    public static boolean isLockServiceAvailable() {
        return lockService != null;
    }

    public static boolean tryLock(String path, String owner, long timeout) {
        try {
            LockService svc = getLockService();
            return svc.lock(path, owner, timeout);
        } catch (Exception e) {
            LOG.debug("Lock service not available: {}", e.getMessage());
            return true; // proceed without lock
        }
    }

    public static boolean tryUnlock(String path, String owner) {
        try {
            LockService svc = getLockService();
            return svc.unlock(path, owner);
        } catch (Exception e) {
            LOG.debug("Lock service not available: {}", e.getMessage());
            return true;
        }
    }
}
