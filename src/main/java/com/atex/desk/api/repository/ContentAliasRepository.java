package com.atex.desk.api.repository;

import com.atex.desk.api.entity.ContentAlias;
import com.atex.desk.api.entity.ContentAliasId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ContentAliasRepository extends JpaRepository<ContentAlias, ContentAliasId>
{
    List<ContentAlias> findByIdtypeAndId(Integer idtype, String id);

    @Query("""
        SELECT ca FROM ContentAlias ca
        JOIN Alias a ON ca.aliasId = a.aliasId
        WHERE a.name = :aliasName AND ca.value = :value
        """)
    Optional<ContentAlias> findByAliasNameAndValue(String aliasName, String value);
}
