package com.splitwisemoney.repository;

import com.splitwisemoney.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user"})
    Page<ActivityLog> findByUserId(Long userId, Pageable pageable);
}
