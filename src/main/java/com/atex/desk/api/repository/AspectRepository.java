package com.atex.desk.api.repository;

import com.atex.desk.api.entity.Aspect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AspectRepository extends JpaRepository<Aspect, Integer>
{
    @Query("""
        SELECT a FROM Aspect a
        JOIN AspectLocation al ON a.aspectId = al.aspectId
        WHERE al.contentId = :contentId
        """)
    List<Aspect> findByContentEntryId(Integer contentId);

    /**
     * Get aspects for a specific version (by versionId in the contents table).
     * Used for MD5 comparison during updates to reuse unchanged aspects.
     */
    @Query("""
        SELECT a FROM Aspect a
        JOIN AspectLocation al ON a.aspectId = al.aspectId
        JOIN Content c ON al.contentId = c.contentId
        WHERE c.versionId = :versionId
        """)
    List<Aspect> findByVersionId(Integer versionId);
}
