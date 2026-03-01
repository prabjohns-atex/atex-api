package com.atex.desk.api.repository;

import com.atex.desk.api.entity.ChangeListAttribute;
import com.atex.desk.api.entity.ChangeListAttributeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChangeListAttributeRepository extends JpaRepository<ChangeListAttribute, ChangeListAttributeId>
{
    List<ChangeListAttribute> findByIdIn(List<Integer> ids);

    void deleteByIdIn(List<Integer> ids);
}
