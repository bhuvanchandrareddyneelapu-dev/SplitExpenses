package com.splitwisemoney.repository;

import com.splitwisemoney.entity.GroupInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

    // ── Existing eager-loading queries ──────────────────────────────────────

    @Query("SELECT gi FROM GroupInvitation gi JOIN FETCH gi.group JOIN FETCH gi.sender WHERE gi.receiver.id = :receiverId AND gi.status = :status")
    List<GroupInvitation> findByReceiverIdAndStatus(@Param("receiverId") Long receiverId, @Param("status") String status);

    @Query("SELECT gi FROM GroupInvitation gi JOIN FETCH gi.group JOIN FETCH gi.sender WHERE gi.group.id = :groupId AND gi.receiver.id = :receiverId")
    Optional<GroupInvitation> findByGroupIdAndReceiverId(@Param("groupId") Long groupId, @Param("receiverId") Long receiverId);

    // ── New token-based queries ──────────────────────────────────────────────

    /** Look up invitation by UUID token, eagerly loading group and sender. */
    @Query("SELECT gi FROM GroupInvitation gi JOIN FETCH gi.group g JOIN FETCH gi.sender s LEFT JOIN FETCH g.createdBy WHERE gi.invitationToken = :token")
    Optional<GroupInvitation> findByInvitationToken(@Param("token") String token);

    /** Check for an existing (any-status) invitation to a given email for a group. */
    @Query("SELECT gi FROM GroupInvitation gi JOIN FETCH gi.group JOIN FETCH gi.sender WHERE gi.group.id = :groupId AND gi.inviteeEmail = :email")
    Optional<GroupInvitation> findByGroupIdAndInviteeEmail(@Param("groupId") Long groupId, @Param("email") String email);

    /** Find all PENDING invitations for a given email (for auto-join after registration). */
    @Query("SELECT gi FROM GroupInvitation gi JOIN FETCH gi.group JOIN FETCH gi.sender WHERE gi.inviteeEmail = :email AND gi.status = :status")
    List<GroupInvitation> findByInviteeEmailAndStatus(@Param("email") String email, @Param("status") String status);
}
