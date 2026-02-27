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
}
