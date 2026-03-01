package com.atex.desk.api.repository;

import com.atex.desk.api.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String>
{
    Optional<AppUser> findByLoginName(String loginName);
}
