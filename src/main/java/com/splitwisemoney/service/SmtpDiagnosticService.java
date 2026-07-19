package com.splitwisemoney.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;

/**
 * Diagnostic service for testing TCP, DNS, and SMTP connectivity to mail servers.
 *
 * <p>Provides deep diagnostic capabilities to isolate network failures (firewalls,
 * port blocking, DNS issues, IPv6 socket timeouts) from application logic.
 */
@Service
public class SmtpDiagnosticService {

    private static final Logger log = LoggerFactory.getLogger(SmtpDiagnosticService.class);

    private final Environment environment;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String smtpHost;

    @Value("${spring.mail.port:587}")
    private int smtpPort;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${spring.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private String smtpAuth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
    private String starttlsEnable;

    @Value("${spring.mail.properties.mail.smtp.starttls.required:true}")
    private String starttlsRequired;

    @Value("${spring.mail.properties.mail.smtp.ssl.enable:false}")
    private String sslEnable;

    public SmtpDiagnosticService(Environment environment) {
        this.environment = environment;
    }

    /**
     * Executes a comprehensive diagnostic check of the mail subsystem.
     *
     * @return structured diagnostic map containing environment, configuration, DNS, TCP, and verdict.
     */
    public Map<String, Object> performDiagnostic() {
        Map<String, Object> report = new LinkedHashMap<>();

        // 1. Runtime Environment Details
        boolean isRailway = System.getenv("PORT") != null || System.getenv("RAILWAY_STATIC_URL") != null;
        String platform = isRailway ? "Railway Cloud Container" : "Local Machine / On-Premise";

        Map<String, Object> envDetails = new LinkedHashMap<>();
        envDetails.put("platform", platform);
        envDetails.put("isRailway", isRailway);
        envDetails.put("activeProfiles", Arrays.toString(environment.getActiveProfiles()));
        envDetails.put("javaVersion", System.getProperty("java.version"));
        envDetails.put("javaVendor", System.getProperty("java.vendor"));
        envDetails.put("osName", System.getProperty("os.name"));
        envDetails.put("osArch", System.getProperty("os.arch"));
        envDetails.put("osVersion", System.getProperty("os.version"));
        report.put("environment", envDetails);

        // 2. Resolved Application Configuration
        boolean hasUsername = smtpUsername != null && !smtpUsername.isBlank();
        boolean hasPassword = smtpPassword != null && !smtpPassword.isBlank();
        String maskedUsername = hasUsername ? maskEmail(smtpUsername) : "<EMPTY>";

        Map<String, Object> configDetails = new LinkedHashMap<>();
        configDetails.put("host", smtpHost);
        configDetails.put("port", smtpPort);
        configDetails.put("username", maskedUsername);
        configDetails.put("mailEnabled", mailEnabled);
        configDetails.put("passwordPresent", hasPassword);
        configDetails.put("smtpAuth", smtpAuth);
        configDetails.put("starttlsEnable", starttlsEnable);
        configDetails.put("starttlsRequired", starttlsRequired);
        configDetails.put("sslEnable", sslEnable);
        report.put("configuration", configDetails);

        // 3. DNS Resolution Audit
        Map<String, Object> dnsDetails = new LinkedHashMap<>();
        List<String> ipv4Addresses = new ArrayList<>();
        List<String> ipv6Addresses = new ArrayList<>();
        List<String> allResolvedIps = new ArrayList<>();

        long dnsStart = System.currentTimeMillis();
        try {
            InetAddress[] addresses = InetAddress.getAllByName(smtpHost);
            long dnsDuration = System.currentTimeMillis() - dnsStart;
            dnsDetails.put("status", "SUCCESS");
            dnsDetails.put("durationMs", dnsDuration);

            for (InetAddress addr : addresses) {
                String ip = addr.getHostAddress();
                allResolvedIps.add(ip);
                if (ip.contains(":")) {
                    ipv6Addresses.add(ip);
                } else {
                    ipv4Addresses.add(ip);
                }
            }
            dnsDetails.put("totalIpsFound", addresses.length);
            dnsDetails.put("allIps", allResolvedIps);
            dnsDetails.put("ipv4Ips", ipv4Addresses);
            dnsDetails.put("ipv6Ips", ipv6Addresses);

            log.info("[SmtpDiagnostic] DNS resolution for {}: {} IPs found in {}ms (IPv4: {}, IPv6: {})",
                    smtpHost, addresses.length, dnsDuration, ipv4Addresses.size(), ipv6Addresses.size());
        } catch (Exception e) {
            dnsDetails.put("status", "FAILED");
            dnsDetails.put("error", e.getClass().getName() + ": " + e.getMessage());
            log.error("[SmtpDiagnostic] DNS resolution failed for {}: {}", smtpHost, e.getMessage());
        }
        report.put("dnsResolution", dnsDetails);

        // 4. Primary TCP Socket Connectivity Probe (Target Port)
        Map<String, Object> primaryTcp = testTcpPort(smtpHost, smtpPort, 5000);
        report.put("tcpProbePrimaryPort", primaryTcp);

        // 5. Alternate TCP Socket Probe (Port 465 SMTPS if primary is 587, or Port 587 if primary is 465)
        int altPort = (smtpPort == 587) ? 465 : 587;
        Map<String, Object> altTcp = testTcpPort(smtpHost, altPort, 5000);
        report.put("tcpProbeAlternatePort", altTcp);

        // 6. Direct SMTP Handshake Banner Check (if primary TCP succeeded)
        Map<String, Object> smtpHandshake = new LinkedHashMap<>();
        boolean primaryTcpSuccess = Boolean.TRUE.equals(primaryTcp.get("success"));

        if (primaryTcpSuccess) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(smtpHost, smtpPort), 5000);
                socket.setSoTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

                String banner = reader.readLine();
                smtpHandshake.put("status", "SUCCESS");
                smtpHandshake.put("smtpBanner", banner);

                // Send EHLO
                writer.println("EHLO SplitWiseDiagnostic");
                String ehloResponse = reader.readLine();
                smtpHandshake.put("ehloResponse", ehloResponse);

                log.info("[SmtpDiagnostic] SMTP Handshake banner: {}", banner);
            } catch (Exception e) {
                smtpHandshake.put("status", "FAILED");
                smtpHandshake.put("error", e.getClass().getName() + ": " + e.getMessage());
                log.warn("[SmtpDiagnostic] SMTP Handshake failed: {}", e.getMessage());
            }
        } else {
            smtpHandshake.put("status", "SKIPPED");
            smtpHandshake.put("reason", "TCP connection to port " + smtpPort + " failed");
        }
        report.put("smtpHandshake", smtpHandshake);

        // 7. Overall Verdict Generation
        String verdict;
        boolean overallHealthy = false;

        if (!mailEnabled) {
            verdict = "⚠️ Mail is DISABLED (spring.mail.enabled=false). Invitation emails will be skipped.";
        } else if (!hasUsername || !hasPassword) {
            verdict = "⚠️ Mail credentials INCOMPLETE. MAIL_USERNAME or MAIL_PASSWORD environment variable missing.";
        } else if (!primaryTcpSuccess) {
            String errorMsg = (String) primaryTcp.get("error");
            boolean altSuccess = Boolean.TRUE.equals(altTcp.get("success"));

            if (altSuccess) {
                verdict = "✗ TCP connection to " + smtpHost + ":" + smtpPort + " TIMED OUT / BLOCKED (" + errorMsg + "). " +
                          "However, Port " + altPort + " IS REACHABLE! Switch MAIL_PORT=" + altPort + " and MAIL_SSL=true.";
            } else {
                verdict = "✗ OUTBOUND PORT BLOCKED! Network/Firewall blocks connection to " + smtpHost + ":" + smtpPort + " (" + errorMsg + "). " +
                          "If deploying on Railway or restrictive cloud network, consider using an alternative SMTP relay (e.g. Brevo/SendGrid) or Port 465 SSL.";
            }
        } else {
            overallHealthy = true;
            verdict = "✓ TCP connectivity to " + smtpHost + ":" + smtpPort + " is SUCCESSFUL. Mail transport network layer is fully functional.";
        }

        report.put("healthy", overallHealthy);
        report.put("verdict", verdict);

        return report;
    }

    /**
     * Executes a raw TCP socket test to a specific host and port.
     */
    public Map<String, Object> testTcpPort(String host, int port, int timeoutMs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetHost", host);
        result.put("targetPort", port);
        result.put("timeoutMs", timeoutMs);

        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            long duration = System.currentTimeMillis() - start;

            result.put("success", true);
            result.put("durationMs", duration);
            result.put("connectedIp", socket.getInetAddress().getHostAddress());
            result.put("localPort", socket.getLocalPort());

            log.info("[SmtpDiagnostic] TCP Socket test PASS -> {}:{} ({}) in {}ms",
                    host, port, socket.getInetAddress().getHostAddress(), duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            result.put("success", false);
            result.put("durationMs", duration);
            result.put("errorType", e.getClass().getName());
            result.put("error", e.getMessage());

            log.error("[SmtpDiagnostic] TCP Socket test FAIL -> {}:{} after {}ms: {} ({})",
                    host, port, duration, e.getClass().getSimpleName(), e.getMessage());
        }
        return result;
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String maskedName = name.length() > 2 ? name.substring(0, 2) + "***" : "***";
        return maskedName + "@" + parts[1];
    }
}
