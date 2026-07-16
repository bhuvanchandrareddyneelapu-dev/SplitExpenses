package com.splitwisemoney.repository;

import com.splitwisemoney.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    /**
     * Fetch memberships by group, with user data eagerly loaded to prevent LazyInitializationException.
     */
    @Query("SELECT gm FROM GroupMember gm JOIN FETCH gm.user WHERE gm.group.id = :groupId")
    List<GroupMember> findByGroupIdWithUser(@Param("groupId") Long groupId);

    /**
     * Fetch memberships by group, without eager loading.
     */
    List<GroupMember> findByGroupId(Long groupId);

    /**
     * Fetch memberships by user with both the Group AND the Group's createdBy User
     * eagerly loaded in a single JOIN FETCH query. This prevents LazyInitializationException
     * when the controller accesses group.getCreatedBy() outside the service transaction.
     */
    @Query("SELECT gm FROM GroupMember gm " +
           "JOIN FETCH gm.group g " +
           "LEFT JOIN FETCH g.createdBy " +
           "WHERE gm.user.id = :userId")
    List<GroupMember> findByUserIdWithGroupAndCreator(@Param("userId") Long userId);

    /**
     * Simple membership lookup by user id (without eager loading).
     * Used where only the relationship existence or group id is needed.
     */
    List<GroupMember> findByUserId(Long userId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM GroupMember gm WHERE gm.group.id = :groupId AND gm.user.id = :userId")
    void deleteByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
