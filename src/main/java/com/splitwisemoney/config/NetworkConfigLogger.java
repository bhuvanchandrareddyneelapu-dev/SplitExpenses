package com.splitwisemoney.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

@Component
public class NetworkConfigLogger {

    private static final Logger log = LoggerFactory.getLogger(NetworkConfigLogger.class);

    @Value("${app.frontend-url:${app.base-url:http://localhost:8080}}")
    private String frontendUrl;

    @Value("${server.port:8080}")
    private String serverPort;

    @PostConstruct
    public void logNetworkConfiguration() {
        String lanIp = discoverLanIp();

        log.info("==========================================================================================");
        log.info("[SplitWiseMoney Network Configuration]");
        log.info("Current Configured app.frontend-url : {}", frontendUrl);

        if (lanIp != null) {
            String suggestedLanUrl = "http://" + lanIp + ":" + serverPort;
            log.info("Discovered Local Network (LAN) IP   : {}", suggestedLanUrl);
            log.info("------------------------------------------------------------------------------------------");
            log.info("💡 MOBILE TESTING INSTRUCTIONS:");
            log.info("To test email invitations on a real mobile device (Android / iPhone) connected to Wi-Fi:");
            log.info("Set environment variable: APP_FRONTEND_URL={}", suggestedLanUrl);
            log.info("Or pass JVM option      : -Dapp.frontend-url={}", suggestedLanUrl);
        } else {
            log.info("To test on mobile or production, set APP_FRONTEND_URL to your ngrok or domain URL.");
        }
        log.info("==========================================================================================");
    }

    private String discoverLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.isSiteLocalAddress() && addr.getHostAddress().indexOf(':') < 0) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }
}
