package com.atex.desk.api.repository;

import com.atex.desk.api.entity.Attribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttributeRepository extends JpaRepository<Attribute, Integer>
{
    Optional<Attribute> findByName(String name);
}
