package com.atex.desk.api.repository;

import com.atex.desk.api.entity.ContentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentVersionRepository extends JpaRepository<ContentVersion, Integer>
{
    Optional<ContentVersion> findByIdtypeAndIdAndVersion(Integer idtype, String id, String version);

    List<ContentVersion> findByIdtypeAndIdOrderByVersionIdDesc(Integer idtype, String id);

    Optional<ContentVersion> findFirstByIdtypeAndIdOrderByVersionIdDesc(Integer idtype, String id);
}
