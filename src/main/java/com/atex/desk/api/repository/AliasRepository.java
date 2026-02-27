package com.atex.desk.api.repository;

import com.atex.desk.api.entity.Alias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AliasRepository extends JpaRepository<Alias, Integer>
{
    Optional<Alias> findByName(String name);
}
