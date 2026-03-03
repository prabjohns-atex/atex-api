package com.atex.desk.api.repository;

import com.atex.desk.api.entity.Content;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

public interface ContentRepository extends JpaRepository<Content, Integer>
{
    Optional<Content> findByVersionId(Integer versionId);

    @Modifying
    void deleteByVersionId(Integer versionId);
}
