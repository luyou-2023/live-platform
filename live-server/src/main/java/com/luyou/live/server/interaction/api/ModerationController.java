package com.luyou.live.server.interaction.api;

import com.luyou.live.server.common.ApiResponse;
import com.luyou.live.server.common.AuthContext;
import com.luyou.live.server.interaction.application.InteractionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}")
@Validated
public class ModerationController {

    private final InteractionService interactionService;

    public ModerationController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    public record MuteRequest(@NotBlank String userId, @Min(1) @Max(10080) long minutes) {}

    @PostMapping("/mute")
    public ApiResponse<Void> mute(@PathVariable String roomId,
                                  @RequestBody @Validated MuteRequest req) {
        interactionService.mute(roomId, AuthContext.requireUid(), req.userId(), req.minutes());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/mute/{userId}")
    public ApiResponse<Void> unmute(@PathVariable String roomId, @PathVariable String userId) {
        interactionService.unmute(roomId, AuthContext.requireUid(), userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/online")
    public ApiResponse<Long> online(@PathVariable String roomId) {
        return ApiResponse.ok(interactionService.onlineCount(roomId));
    }
}
