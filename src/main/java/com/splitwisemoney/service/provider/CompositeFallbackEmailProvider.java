package com.splitwisemoney.service.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Primary Email Provider Router that implements automatic provider fallback and configuration-based switching.
 *
 * <p>Supports:
 * <ul>
 *   <li>Configuration switching via {@code EMAIL_PROVIDER=gmail|brevo|resend|sendgrid}</li>
 *   <li>Automatic Fallback Chain: Gmail SMTP (587 -> 465) -> Brevo API -> Resend API -> SendGrid API</li>
 * </ul>
 */
@Primary
@Service("compositeFallbackEmailProvider")
public class CompositeFallbackEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(CompositeFallbackEmailProvider.class);

    @Value("${app.email.provider:${EMAIL_PROVIDER:gmail}}")
    private String configuredProviderName;

    private final Map<String, EmailProvider> providerMap = new LinkedHashMap<>();

    public CompositeFallbackEmailProvider(List<EmailProvider> providers) {
        for (EmailProvider provider : providers) {
            if (provider != this) {
                providerMap.put(provider.getProviderName().toLowerCase(), provider);
            }
        }
    }

    @Override
    public String getProviderName() {
        return "composite-fallback";
    }

    @Override
    public boolean isConfigured() {
        EmailProvider target = getTargetProvider();
        return target != null && target.isConfigured();
    }

    @Override
    public void sendExistingUserInvitation(String toEmail, String inviterName, String groupName, String token, LocalDateTime expiresAt) {
        executeWithFallback("sendExistingUserInvitation", provider ->
                provider.sendExistingUserInvitation(toEmail, inviterName, groupName, token, expiresAt));
    }

    @Override
    public void sendNewUserInvitation(String toEmail, String inviterName, String groupName, String token, LocalDateTime expiresAt) {
        executeWithFallback("sendNewUserInvitation", provider ->
                provider.sendNewUserInvitation(toEmail, inviterName, groupName, token, expiresAt));
    }

    @Override
    public void sendInvitationAccepted(String toEmail, String accepterName, String groupName) {
        executeWithFallback("sendInvitationAccepted", provider ->
                provider.sendInvitationAccepted(toEmail, accepterName, groupName));
    }

    @Override
    public void sendInvitationRejected(String toEmail, String rejectorName, String groupName) {
        executeWithFallback("sendInvitationRejected", provider ->
                provider.sendInvitationRejected(toEmail, rejectorName, groupName));
    }

    @Override
    public void sendTestEmail(String toEmail) {
        executeWithFallback("sendTestEmail", provider -> provider.sendTestEmail(toEmail));
    }

    @FunctionalInterface
    private interface ProviderAction {
        void execute(EmailProvider provider);
    }

    private void executeWithFallback(String actionName, ProviderAction action) {
        EmailProvider primary = getTargetProvider();
        List<EmailProvider> fallbackChain = getOrderedFallbackChain(primary);

        Exception lastException = null;

        for (EmailProvider provider : fallbackChain) {
            if (!provider.isConfigured()) {
                log.debug("[CompositeEmailProvider] Provider '{}' is not configured — skipping", provider.getProviderName());
                continue;
            }

            try {
                log.info("[CompositeEmailProvider] Dispatching [{}] using provider '{}'...", actionName, provider.getProviderName());
                action.execute(provider);
                log.info("[CompositeEmailProvider] ✓ Action [{}] succeeded via provider '{}'", actionName, provider.getProviderName());
                return;
            } catch (Exception e) {
                lastException = e;
                log.warn("[CompositeEmailProvider] ⚠️ Provider '{}' failed for [{}]: {}",
                        provider.getProviderName(), actionName, e.getMessage());
            }
        }

        if (lastException == null) {
            // No configured provider was attempted (e.g. mail is disabled in test/dev environment)
            log.warn("[CompositeEmailProvider] ⚠️ SKIPPING DISPATCH for [{}] — No configured email provider available (mail disabled or missing credentials).", actionName);
            return;
        }

        String msg = "All configured email providers failed to deliver [" + actionName + "]. Last error: " + lastException.getMessage();
        log.error("[CompositeEmailProvider] ✗ {}", msg);
        throw new MailSendException(msg, lastException);
    }




    private EmailProvider getTargetProvider() {
        String key = configuredProviderName != null ? configuredProviderName.trim().toLowerCase() : "gmail";
        EmailProvider provider = providerMap.get(key);
        if (provider == null) {
            log.warn("[CompositeEmailProvider] Unknown provider '{}' requested — falling back to 'gmail'", configuredProviderName);
            provider = providerMap.get("gmail");
        }
        return provider;
    }

    private List<EmailProvider> getOrderedFallbackChain(EmailProvider primary) {
        List<EmailProvider> chain = new ArrayList<>();
        if (primary != null) {
            chain.add(primary);
        }
        // Add all remaining providers in fallback order (HTTP REST API providers first, as they bypass port blocks)
        String[] fallbackOrder = {"brevo", "resend", "sendgrid", "gmail"};
        for (String name : fallbackOrder) {
            EmailProvider p = providerMap.get(name);
            if (p != null && !chain.contains(p)) {
                chain.add(p);
            }
        }
        return chain;
    }
}
