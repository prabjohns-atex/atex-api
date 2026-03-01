package com.atex.desk.api.repository;

import com.atex.desk.api.entity.AppAcl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppAclRepository extends JpaRepository<AppAcl, Integer>
{
    Optional<AppAcl> findByName(String name);
}
