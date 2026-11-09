package com.luyou.live.server.interaction.api;

import com.luyou.live.server.common.ApiResponse;
import com.luyou.live.server.interaction.application.InteractionService;
import com.luyou.live.server.interaction.application.InteractionService.RoomSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}")
public class InteractionQueryController {

    private final InteractionService interactionService;

    public InteractionQueryController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    /** 进场快照：在线数、累计赞、本场最近50条评论 */
    @GetMapping("/snapshot")
    public ApiResponse<RoomSnapshot> snapshot(@PathVariable String roomId) {
        return ApiResponse.ok(interactionService.snapshot(roomId));
    }
}
