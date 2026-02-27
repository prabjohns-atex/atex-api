package com.atex.desk.api.repository;

import com.atex.desk.api.entity.IdType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdTypeRepository extends JpaRepository<IdType, Integer>
{
    Optional<IdType> findByName(String name);
}
