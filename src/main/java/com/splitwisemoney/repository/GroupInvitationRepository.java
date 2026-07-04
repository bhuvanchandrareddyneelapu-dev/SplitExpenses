package com.splitwisemoney.repository;

import com.splitwisemoney.entity.GroupInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {
    List<GroupInvitation> findByReceiverIdAndStatus(Long receiverId, String status);
    Optional<GroupInvitation> findByGroupIdAndReceiverId(Long groupId, Long receiverId);
}
