package com.atex.desk.api.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Short unique ID generator modelled after Twitter's Camflake.
 * Ported from adm-content-service ShortUniqueIDGenerator.
 *
 * IDs are base-36 encoded, typically 15-18 characters.
 * Versions are base-36 encoded, typically 8-10 characters.
 */
@Component
public class IdGenerator
{
    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    private static final SecureRandom RND = new SecureRandom();

    private static final long TIME_MAX = (1L << 41) - 1L;

    private static final int SEQUENCE_MAX_BITS = 24;
    private static final long SEQUENCE_MAX = (1L << SEQUENCE_MAX_BITS) - 1L;

    private static final int MINOR_SEQUENCE_MAX_BITS = 14;
    private static final long MINOR_SEQUENCE_MAX = (1L << MINOR_SEQUENCE_MAX_BITS) - 1L;

    private static final int RANDOM_OFFSET_BITS = Integer.SIZE;

    private final long baseTime;
    private volatile long elapsedTime;

    private final Object lock = new Object();
    private final Object majorLock = new Object();

    private final byte[] seed = new byte[20];
    private final AtomicLong majorCounter = new AtomicLong(0);
    private final AtomicInteger minorCounter = new AtomicInteger(0);
    private final AtomicLong rndGenerator = new AtomicLong(0);

    public IdGenerator()
    {
        this(ZonedDateTime.of(2019, 6, 1, 0, 0, 0, 0, ZoneId.of("UTC")).toInstant());
    }

    public IdGenerator(Instant baseTime)
    {
        Instant now = Instant.now();
        if (baseTime.isBefore(Instant.EPOCH) || baseTime.isAfter(now))
        {
            throw new RuntimeException("Base time must be between epoch and now");
        }
        long elapsed = now.toEpochMilli() - baseTime.toEpochMilli();
        if (elapsed > TIME_MAX)
        {
            throw new RuntimeException("Exceeded the time limit");
        }
        this.baseTime = baseTime.toEpochMilli();
        this.elapsedTime = elapsed;
        RND.nextBytes(this.seed);
    }

    public String nextId()
    {
        long elapsed = getElapsedTime();
        int sequence = getMajorSequence(elapsed);
        if (sequence > SEQUENCE_MAX)
        {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2L));
            elapsed = getElapsedTime();
            sequence = getMajorSequence(elapsed);
            if (sequence > SEQUENCE_MAX)
            {
                throw new RuntimeException("Failed to issue sequence id");
            }
        }

        final byte[] bytes;
        try
        {
            ByteBuffer b = ByteBuffer.allocate(Long.SIZE + seed.length);
            b.putLong(rndGenerator.incrementAndGet());
            b.put(seed);
            bytes = calculateHMAC(b.array(), seed);
        }
        catch (SignatureException e)
        {
            throw new RuntimeException(e);
        }

        BigInteger b = new BigInteger(1, bytes)
            .shiftRight((bytes.length * 8) - RANDOM_OFFSET_BITS);

        BigInteger bs = BigInteger.valueOf(elapsed)
            .shiftLeft(SEQUENCE_MAX_BITS + RANDOM_OFFSET_BITS)
            .or(BigInteger.valueOf(sequence).shiftLeft(RANDOM_OFFSET_BITS))
            .or(b);
        return bs.toString(36);
    }

    public String nextVersion()
    {
        long elapsed = getElapsedTime();
        long sequence = getMinorSequence(elapsed);
        int count = 10;
        while (sequence > MINOR_SEQUENCE_MAX && count-- > 0)
        {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2L));
            elapsed = getElapsedTime();
            sequence = getMinorSequence(elapsed);
            if (sequence > MINOR_SEQUENCE_MAX && count == 0)
            {
                throw new RuntimeException("Failed to issue sequence id");
            }
        }
        return BigInteger.valueOf(elapsed)
            .shiftLeft(MINOR_SEQUENCE_MAX_BITS)
            .or(BigInteger.valueOf(sequence))
            .toString(36);
    }

    private long getElapsedTime()
    {
        long now = Instant.now().toEpochMilli();
        long elapsed = now - baseTime;
        if (elapsed > TIME_MAX)
        {
            throw new RuntimeException("Exceeded the time limit");
        }
        return elapsed;
    }

    private int getMinorSequence(long elapsed)
    {
        synchronized (lock)
        {
            if (this.elapsedTime < elapsed)
            {
                this.elapsedTime = elapsed;
                minorCounter.set(0);
            }
            return minorCounter.getAndIncrement();
        }
    }

    private int getMajorSequence(long elapsed)
    {
        synchronized (majorLock)
        {
            if (this.elapsedTime < elapsed)
            {
                this.elapsedTime = elapsed;
                majorCounter.set(0);
            }
            return (int) majorCounter.getAndIncrement();
        }
    }

    private byte[] calculateHMAC(byte[] data, byte[] key) throws SignatureException
    {
        try
        {
            SecretKeySpec signingKey = new SecretKeySpec(key, HMAC_SHA1_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            return mac.doFinal(data);
        }
        catch (Exception e)
        {
            throw new SignatureException("Failed to generate HMAC: " + e.getMessage());
        }
    }
}
