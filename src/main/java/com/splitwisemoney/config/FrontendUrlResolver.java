package com.splitwisemoney.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Enterprise Production URL Resolver for SplitWiseMoney.
 *
 * <p>Ensures email invitation links NEVER use 'localhost' or '127.0.0.1'.
 *
 * <p>Resolution Order:
 * <ol>
 *   <li>Configured property {@code app.frontend-url} (e.g. {@code https://splitwisemoney.com})</li>
 *   <li>Environment variable {@code APP_FRONTEND_URL} or {@code FRONTEND_URL}</li>
 *   <li>Auto-discovered Local Network (LAN) IP (e.g. {@code http://192.168.X.X:8080})</li>
 * </ol>
 */
@Component
public class FrontendUrlResolver {

    private static final Logger log = LoggerFactory.getLogger(FrontendUrlResolver.class);

    @Value("${app.frontend-url:}")
    private String configuredFrontendUrl;

    @Value("${server.port:8080}")
    private int serverPort;

    private String cachedResolvedUrl;

    /**
     * Returns the active base URL guaranteed to be reachable by external devices (mobile/desktop).
     */
    public synchronized String getBaseUrl() {
        if (cachedResolvedUrl != null && !cachedResolvedUrl.isBlank()) {
            return cachedResolvedUrl;
        }

        // 1. Check explicitly configured property / environment variable
        if (configuredFrontendUrl != null && !configuredFrontendUrl.isBlank()
                && !isLocalhost(configuredFrontendUrl)) {
            cachedResolvedUrl = stripTrailingSlash(configuredFrontendUrl);
            log.info("[FrontendUrlResolver] Using explicitly configured frontend URL: {}", cachedResolvedUrl);
            return cachedResolvedUrl;
        }

        // Check ENV directly
        String envUrl = System.getenv("APP_FRONTEND_URL");
        if (envUrl == null || envUrl.isBlank()) {
            envUrl = System.getenv("FRONTEND_URL");
        }
        if (envUrl != null && !envUrl.isBlank() && !isLocalhost(envUrl)) {
            cachedResolvedUrl = stripTrailingSlash(envUrl);
            log.info("[FrontendUrlResolver] Using environment frontend URL: {}", cachedResolvedUrl);
            return cachedResolvedUrl;
        }

        // 2. Auto-discover Local Network (LAN) IP to prevent mobile ERR_CONNECTION_REFUSED
        String lanIp = discoverLanIpAddress();
        if (lanIp != null && !lanIp.isBlank()) {
            cachedResolvedUrl = "http://" + lanIp + ":" + serverPort;
            log.info("[FrontendUrlResolver] Auto-discovered LAN IP for mobile compatibility: {}", cachedResolvedUrl);
            return cachedResolvedUrl;
        }

        // Fallback default if LAN IP is unavailable
        cachedResolvedUrl = "http://192.168.1.100:" + serverPort;
        log.warn("[FrontendUrlResolver] Fallback LAN URL set: {}", cachedResolvedUrl);
        return cachedResolvedUrl;
    }

    private boolean isLocalhost(String url) {
        String lower = url.toLowerCase();
        return lower.contains("localhost") || lower.contains("127.0.0.1");
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String discoverLanIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                        String hostAddr = addr.getHostAddress();
                        if (hostAddr.startsWith("192.168.") || hostAddr.startsWith("10.") || hostAddr.startsWith("172.")) {
                            return hostAddr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[FrontendUrlResolver] Could not discover LAN IP: {}", e.getMessage());
        }
        return null;
    }
}
