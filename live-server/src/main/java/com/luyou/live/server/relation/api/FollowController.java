package com.luyou.live.server.relation.api;

import com.luyou.live.server.common.ApiResponse;
import com.luyou.live.server.common.AuthContext;
import com.luyou.live.server.relation.application.FollowService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/{userId}")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) { this.followService = followService; }

    @PostMapping("/follow")
    public ApiResponse<Void> follow(@PathVariable String userId) {
        followService.follow(AuthContext.requireUid(), userId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/follow")
    public ApiResponse<Void> unfollow(@PathVariable String userId) {
        followService.unfollow(AuthContext.requireUid(), userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/follow/counts")
    public ApiResponse<FollowService.Counts> counts(@PathVariable String userId) {
        // 可选鉴权：带 token 时返回 viewer 是否已关注
        return ApiResponse.ok(followService.counts(userId, AuthContext.uid()));
    }
}
