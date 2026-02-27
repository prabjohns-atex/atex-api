package com.atex.desk.api.repository;

import com.atex.desk.api.entity.View;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ViewRepository extends JpaRepository<View, Integer>
{
    Optional<View> findByName(String name);
}
