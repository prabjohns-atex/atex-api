package com.atex.desk.api.repository;

import com.atex.desk.api.entity.IndexerState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface IndexerStateRepository extends JpaRepository<IndexerState, String>
{
    Optional<IndexerState> findByIndexerId(String indexerId);

    @Query("SELECT s FROM IndexerState s WHERE s.jobType IN :jobTypes AND s.status IN :statuses ORDER BY s.createdAt ASC")
    List<IndexerState> findByJobTypeInAndStatusIn(List<String> jobTypes, List<String> statuses);

    @Query("SELECT s FROM IndexerState s WHERE s.jobType <> 'LIVE' ORDER BY s.createdAt DESC")
    List<IndexerState> findAllReindexJobs();
}

