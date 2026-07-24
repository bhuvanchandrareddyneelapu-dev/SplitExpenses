package com.splitwisemoney.service.email;

import org.springframework.stereotype.Component;

@Component
public class InvitationEmailTemplate {

    /**
     * Generates responsive dark + purple HTML template for registered user invitation.
     */
    public String buildRegisteredUserEmail(String inviteeName, String inviterName, String groupName, String acceptUrl, String declineUrl, String expiryTime) {
        String greeting = (inviteeName != null && !inviteeName.isBlank()) ? "Hello " + esc(inviteeName) + "," : "Hello,";
        String expiryText = (expiryTime != null) ? expiryTime : "48 hours from now";

        return "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset='UTF-8'></head>"
                + "<body style='font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; background-color: #0f0f1a; color: #e2e8f0; margin: 0; padding: 40px 20px;'>"
                + "<div style='max-width: 560px; margin: 0 auto; background-color: #1a1a2e; border: 1px solid #6366f1; border-radius: 16px; padding: 40px; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);'>"
                // Header / Logo
                + "<div style='text-align: center; margin-bottom: 30px;'>"
                + "<h1 style='color: #818cf8; font-size: 28px; margin: 0; font-weight: 800; tracking: -0.5px;'>💸 SplitWiseMoney</h1>"
                + "<p style='color: #94a3b8; font-size: 14px; margin-top: 5px;'>Smart Expense Sharing</p>"
                + "</div>"
                // Body content
                + "<h2 style='color: #ffffff; font-size: 20px; font-weight: 600; margin-bottom: 16px;'>"
                + "You've been invited to join \"" + esc(groupName) + "\" on SplitWiseMoney</h2>"
                + "<p style='color: #cbd5e1; font-size: 15px; line-height: 1.6; margin-bottom: 24px;'>" + greeting + "</p>"
                + "<p style='color: #cbd5e1; font-size: 15px; line-height: 1.6; margin-bottom: 24px;'>"
                + "<strong style='color: #ffffff;'>" + esc(inviterName) + "</strong> has invited you to join the expense sharing group: "
                + "<strong style='color: #a5b4fc;'>" + esc(groupName) + "</strong>.</p>"
                + "<p style='color: #94a3b8; font-size: 14px; line-height: 1.6; margin-bottom: 32px;'>⏳ This invitation link is valid for <strong>48 hours</strong> (Expires: " + esc(expiryText) + ").</p>"
                // CTA Buttons (Accept + Decline)
                + "<div style='text-align: center; margin-bottom: 32px; display: flex; justify-content: center; gap: 12px;'>"
                + "<a href='" + acceptUrl + "' style='display: inline-block; background-color: #10b981; color: #ffffff; text-decoration: none; padding: 14px 28px; border-radius: 10px; font-weight: 700; font-size: 15px; box-shadow: 0 4px 14px 0 rgba(16, 185, 129, 0.4); margin-right: 10px;'>✓ Accept & Join</a>"
                + "<a href='" + declineUrl + "' style='display: inline-block; background-color: rgba(239, 68, 68, 0.15); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.3); text-decoration: none; padding: 14px 24px; border-radius: 10px; font-weight: 600; font-size: 15px;'>Decline</a>"
                + "</div>"
                // Footer
                + "<hr style='border: 0; border-top: 1px solid #334155; margin-bottom: 24px;' />"
                + "<div style='text-align: center; color: #64748b; font-size: 13px; line-height: 1.5;'>"
                + "<p style='margin-bottom: 8px;'>SplitWiseMoney — Simplify group expenses with ease.</p>"
                + "<p style='margin: 0;'>Need help? Contact <a href='mailto:support@splitwisemoney.com' style='color: #818cf8; text-decoration: none;'>support@splitwisemoney.com</a></p>"
                + "</div>"
                + "</div>"
                + "</body></html>";
    }

    /**
     * Generates responsive dark + purple HTML template for unregistered user invitation.
     */
    public String buildUnregisteredUserEmail(String inviterName, String groupName, String signupUrl, String declineUrl, String expiryTime) {
        String expiryText = (expiryTime != null) ? expiryTime : "48 hours from now";

        return "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset='UTF-8'></head>"
                + "<body style='font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; background-color: #0f0f1a; color: #e2e8f0; margin: 0; padding: 40px 20px;'>"
                + "<div style='max-width: 560px; margin: 0 auto; background-color: #1a1a2e; border: 1px solid #6366f1; border-radius: 16px; padding: 40px; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);'>"
                // Header / Logo
                + "<div style='text-align: center; margin-bottom: 30px;'>"
                + "<h1 style='color: #818cf8; font-size: 28px; margin: 0; font-weight: 800; tracking: -0.5px;'>💸 SplitWiseMoney</h1>"
                + "<p style='color: #94a3b8; font-size: 14px; margin-top: 5px;'>Smart Expense Sharing</p>"
                + "</div>"
                // Body content
                + "<h2 style='color: #ffffff; font-size: 20px; font-weight: 600; margin-bottom: 16px;'>"
                + "Join SplitWiseMoney — You've been invited!</h2>"
                + "<p style='color: #cbd5e1; font-size: 15px; line-height: 1.6; margin-bottom: 24px;'>Hello,</p>"
                + "<p style='color: #cbd5e1; font-size: 15px; line-height: 1.6; margin-bottom: 24px;'>"
                + "<strong style='color: #ffffff;'>" + esc(inviterName) + "</strong> invited you to join the expense sharing group: "
                + "<strong style='color: #a5b4fc;'>" + esc(groupName) + "</strong>.</p>"
                + "<p style='color: #94a3b8; font-size: 14px; line-height: 1.6; margin-bottom: 24px;'>⏳ This invitation link is valid for <strong>48 hours</strong> (Expires: " + esc(expiryText) + ").</p>"
                + "<p style='color: #cbd5e1; font-size: 15px; line-height: 1.6; margin-bottom: 32px;'>Create your account below to automatically accept and join the group.</p>"
                // CTA Buttons
                + "<div style='text-align: center; margin-bottom: 32px; display: flex; justify-content: center; gap: 12px;'>"
                + "<a href='" + signupUrl + "' style='display: inline-block; background-color: #6366f1; color: #ffffff; text-decoration: none; padding: 14px 28px; border-radius: 10px; font-weight: 700; font-size: 15px; box-shadow: 0 4px 14px 0 rgba(99, 102, 241, 0.4); margin-right: 10px;'>Create Account & Join</a>"
                + "<a href='" + declineUrl + "' style='display: inline-block; background-color: rgba(239, 68, 68, 0.15); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.3); text-decoration: none; padding: 14px 24px; border-radius: 10px; font-weight: 600; font-size: 15px;'>Decline</a>"
                + "</div>"
                // Footer
                + "<hr style='border: 0; border-top: 1px solid #334155; margin-bottom: 24px;' />"
                + "<div style='text-align: center; color: #64748b; font-size: 13px; line-height: 1.5;'>"
                + "<p style='margin-bottom: 8px;'>SplitWiseMoney — Simplify group expenses with ease.</p>"
                + "<p style='margin: 0;'>Need help? Contact <a href='mailto:support@splitwisemoney.com' style='color: #818cf8; text-decoration: none;'>support@splitwisemoney.com</a></p>"
                + "</div>"
                + "</div>"
                + "</body></html>";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
