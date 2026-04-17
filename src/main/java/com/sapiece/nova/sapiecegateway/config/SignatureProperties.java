package com.sapiece.nova.sapiecegateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "signature")
public class SignatureProperties {

    private boolean enabled = false;

    private String secret = "";

    /** 签名算法：MD5 或 SHA256 */
    private String algorithm = "MD5";

    /** 时间戳有效期（秒） */
    private long timestampValidity = 300;

    /** 不需要签名验证的路径前缀 */
    private List<String> excludedPaths = Collections.emptyList();
}
