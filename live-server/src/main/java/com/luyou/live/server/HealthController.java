package com.luyou.live.server;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查。第一期脚手架；业务模块按 docs/architecture.md 的包划分逐步落地。
 */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> healthz() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "app", "live-server",
                "time", Instant.now().toString()));
    }
}
