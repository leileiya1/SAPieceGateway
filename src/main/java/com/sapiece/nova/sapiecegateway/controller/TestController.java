package com.sapiece.nova.sapiecegateway.controller;

import com.sapiece.nova.sapiecegateway.annotation.Idempotent;
import com.sapiece.nova.sapiecegateway.common.Result;
import com.sapiece.nova.sapiecegateway.service.IdempotentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器
 * 用于演示幂等性、签名验证等功能
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final IdempotentService idempotentService;

    /**
     * 测试幂等性接口（使用Header中的Token）
     * 客户端需要先调用 /test/idempotent/token 获取Token
     * 然后在请求头中添加 Idempotent-Token: {token}
     *
     * @param request 请求参数
     * @return 处理结果
     */
    @PostMapping("/idempotent/submit")
    @Idempotent(
            prefix = "order:create",
            timeout = 30,
            message = "订单正在创建中，请勿重复提交",
            keySource = Idempotent.KeySource.HEADER,
            keyField = "Idempotent-Token"
    )
    public Mono<Result<Map<String, Object>>> createOrder(@RequestBody OrderRequest request) {
        log.info("创建订单请求, orderId: {}, amount: {}", request.getOrderId(), request.getAmount());

        // 用非阻塞定时器模拟耗时操作，避免占用 Netty 事件循环线程。
        return Mono.delay(Duration.ofMillis(100))
                .map(ignored -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("orderId", request.getOrderId());
                    result.put("status", "SUCCESS");
                    result.put("message", "订单创建成功");

                    return result;
                })
                .map(result -> {
                    log.info("订单创建成功, orderId: {}", request.getOrderId());
                    return Result.success("订单创建成功", result);
                })
                .doOnError(error -> log.error("订单创建失败, error: {}", error.getMessage()));
    }

    /**
     * 获取幂等性Token
     * 客户端在提交幂等性请求前，需要先获取Token
     *
     * @return 幂等性Token
     */
    @GetMapping("/idempotent/token")
    public Mono<Result<Map<String, String>>> getIdempotentToken() {
        log.info("获取幂等性Token请求");

        return idempotentService.generateToken("order:create")
                .map(token -> {
                    Map<String, String> result = new HashMap<>();
                    result.put("token", token);
                    log.info("成功生成幂等性Token: {}", token);
                    return Result.success("获取Token成功", result);
                })
                .switchIfEmpty(Mono.just(Result.error("获取Token失败")));
    }

    /**
     * 测试自动生成幂等性键的接口
     * 基于请求参数自动生成幂等性键
     *
     * @param request 请求参数
     * @return 处理结果
     */
    @PostMapping("/idempotent/auto")
    @Idempotent(
            prefix = "payment:submit",
            timeout = 60,
            message = "支付正在处理中，请勿重复提交",
            keySource = Idempotent.KeySource.AUTO
    )
    public Mono<Result<Map<String, Object>>> submitPayment(@RequestBody PaymentRequest request) {
        log.info("提交支付请求, paymentId: {}, amount: {}", request.getPaymentId(), request.getAmount());

        // 用非阻塞定时器模拟耗时操作，避免占用 Netty 事件循环线程。
        return Mono.delay(Duration.ofMillis(200))
                .map(ignored -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("paymentId", request.getPaymentId());
                    result.put("status", "SUCCESS");
                    result.put("message", "支付成功");

                    return result;
                })
                .map(result -> {
                    log.info("支付成功, paymentId: {}", request.getPaymentId());
                    return Result.success("支付成功", result);
                })
                .doOnError(error -> log.error("支付失败, error: {}", error.getMessage()));
    }

    /**
     * 测试签名验证接口
     * 客户端需要计算签名并放入请求头
     *
     * @param request 请求参数
     * @return 处理结果
     */
    @PostMapping("/signature/verify")
    public Mono<Result<String>> testSignature(@RequestBody TestRequest request) {
        log.info("签名验证测试, data: {}", request.getData());

        return Mono.just(Result.success("签名验证通过", request.getData()));
    }

    /**
     * 订单请求DTO
     */
    @Data
    public static class OrderRequest {
        /**
         * 订单ID
         */
        private String orderId;

        /**
         * 订单金额
         */
        private Double amount;

        /**
         * 商品名称
         */
        private String productName;
    }

    /**
     * 支付请求DTO
     */
    @Data
    public static class PaymentRequest {
        /**
         * 支付ID
         */
        private String paymentId;

        /**
         * 支付金额
         */
        private Double amount;

        /**
         * 支付方式
         */
        private String paymentMethod;
    }

    /**
     * 测试请求DTO
     */
    @Data
    public static class TestRequest {
        /**
         * 测试数据
         */
        private String data;
    }
}
