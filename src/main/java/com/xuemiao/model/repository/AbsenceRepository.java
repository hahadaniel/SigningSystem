package com.xuemiao.model.repository;

import com.xuemiao.model.pdm.AbsenceEntity;
import com.xuemiao.model.pdm.StudentIdAndOperDateKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Created by dzj on 9/30/2016.
 */
public interface AbsenceRepository extends JpaRepository<AbsenceEntity, StudentIdAndOperDateKey> {
    @Transactional
    @Modifying
    @Query("delete from AbsenceEntity s where s.studentId = :studentId")
    void deleteByStudentId(@Param("studentId")Long studentId);
}
