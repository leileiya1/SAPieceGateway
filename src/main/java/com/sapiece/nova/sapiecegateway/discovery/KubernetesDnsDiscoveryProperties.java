package com.sapiece.nova.sapiecegateway.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Configuration for Kubernetes Service DNS backed {@code lb://} routes. */
@ConfigurationProperties("gateway.kubernetes-dns")
public class KubernetesDnsDiscoveryProperties {

    private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9](?:[-a-z0-9]{0,61}[a-z0-9])?");
    private static final Pattern DNS_DOMAIN = Pattern.compile(
            "[a-z0-9](?:[-a-z0-9.]{0,251}[a-z0-9])?");

    private boolean enabled;
    private String namespace = "default";
    private String clusterDomain = "cluster.local";
    private int defaultPort = 80;
    private Map<String, Integer> ports = new HashMap<>();

    public void validate() {
        requireDnsLabel(namespace, "Kubernetes命名空间");
        if (clusterDomain == null || !DNS_DOMAIN.matcher(clusterDomain).matches()) {
            throw new IllegalArgumentException("Kubernetes集群域名格式不正确");
        }
        requirePort(defaultPort, "Kubernetes默认服务端口");
        ports.forEach((service, port) -> {
            requireDnsLabel(service, "Kubernetes服务名");
            requirePort(port, "Kubernetes服务端口");
        });
    }

    public static void requireDnsLabel(String value, String field) {
        if (value == null || !DNS_LABEL.matcher(value).matches()) {
            throw new IllegalArgumentException(field + "必须是DNS-1123标签");
        }
    }

    public static void requirePort(Integer port, String field) {
        if (port == null || port < 1 || port > 65535) {
            throw new IllegalArgumentException(field + "必须在1到65535之间");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getClusterDomain() { return clusterDomain; }
    public void setClusterDomain(String clusterDomain) { this.clusterDomain = clusterDomain; }
    public int getDefaultPort() { return defaultPort; }
    public void setDefaultPort(int defaultPort) { this.defaultPort = defaultPort; }
    public Map<String, Integer> getPorts() { return ports; }
    public void setPorts(Map<String, Integer> ports) {
        this.ports = ports == null ? new HashMap<>() : new HashMap<>(ports);
    }
}
