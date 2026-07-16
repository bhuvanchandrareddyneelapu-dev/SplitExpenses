package com.splitwisemoney.repository;

import com.splitwisemoney.entity.GroupInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {
    @Query("SELECT gi FROM GroupInvitation gi JOIN FETCH gi.group JOIN FETCH gi.sender WHERE gi.receiver.id = :receiverId AND gi.status = :status")
    List<GroupInvitation> findByReceiverIdAndStatus(@Param("receiverId") Long receiverId, @Param("status") String status);

    @Query("SELECT gi FROM GroupInvitation gi JOIN FETCH gi.group JOIN FETCH gi.sender WHERE gi.group.id = :groupId AND gi.receiver.id = :receiverId")
    Optional<GroupInvitation> findByGroupIdAndReceiverId(@Param("groupId") Long groupId, @Param("receiverId") Long receiverId);
}
