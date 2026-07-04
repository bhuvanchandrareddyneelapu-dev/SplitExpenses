package com.splitwisemoney.repository;

import com.splitwisemoney.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    List<Settlement> findByGroupId(Long groupId);

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Settlement s WHERE s.group.id = :groupId AND s.fromUser.id = :userId AND s.status = 'SETTLED'")
    BigDecimal sumSettledAmountByGroupIdAndFromUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Settlement s WHERE s.group.id = :groupId AND s.toUser.id = :userId AND s.status = 'SETTLED'")
    BigDecimal sumSettledAmountByGroupIdAndToUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
