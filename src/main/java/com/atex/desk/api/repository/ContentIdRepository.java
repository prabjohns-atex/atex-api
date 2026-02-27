package com.atex.desk.api.repository;

import com.atex.desk.api.entity.ContentId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentIdRepository extends JpaRepository<ContentId, String>
{
}
