package com.splitwisemoney.service.email;

import com.splitwisemoney.entity.GroupInvitation;
import com.splitwisemoney.service.EmailService;
import org.springframework.stereotype.Service;

/**
 * Adapter delegating to consolidated EmailService.
 */
@Service
public class JavaMailEmailService {

    private final EmailService emailService;

    public JavaMailEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    public void sendRegisteredUserInvitation(GroupInvitation invitation, String inviterName, String groupName) {
        emailService.sendExistingUserInvitation(invitation, inviterName, groupName);
    }

    public void sendUnregisteredUserInvitation(GroupInvitation invitation, String inviterName, String groupName) {
        emailService.sendNewUserInvitation(invitation, inviterName, groupName);
    }
}
