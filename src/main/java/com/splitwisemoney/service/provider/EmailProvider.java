package com.splitwisemoney.service.provider;

import java.time.LocalDateTime;

/**
 * Common abstraction interface for all email delivery providers.
 *
 * <p>Supports switching between SMTP (Gmail/Port 587/Port 465) and HTTP API providers
 * (Brevo, Resend, SendGrid) via configuration alone, ensuring cloud containers (Railway)
 * never fail due to outbound port blocking.
 */
public interface EmailProvider {

    /** Identifier for the provider (e.g. "gmail", "brevo", "resend", "sendgrid"). */
    String getProviderName();

    /** True if the provider has all required configuration (API keys or credentials). */
    boolean isConfigured();

    /** Send HTML invitation email for an existing registered user. */
    void sendExistingUserInvitation(String toEmail, String inviterName, String groupName, String token, LocalDateTime expiresAt);

    /** Send HTML invitation email for a new non-registered user (includes registration link). */
    void sendNewUserInvitation(String toEmail, String inviterName, String groupName, String token, LocalDateTime expiresAt);

    /** Send notification when an invitation is accepted. */
    void sendInvitationAccepted(String toEmail, String accepterName, String groupName);

    /** Send notification when an invitation is rejected. */
    void sendInvitationRejected(String toEmail, String rejectorName, String groupName);

    /** Send plain-text diagnostic test email. */
    void sendTestEmail(String toEmail);
}
