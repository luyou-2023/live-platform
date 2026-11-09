package com.luyou.live.server.live.api;

import com.luyou.live.server.common.ApiResponse;
import com.luyou.live.server.common.AuthContext;
import com.luyou.live.server.live.application.RoomService;
import com.luyou.live.server.live.application.RoomService.EnterResult;
import com.luyou.live.server.live.application.RoomService.RoomView;
import com.luyou.live.server.live.application.RoomService.StartResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
@Validated
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) { this.roomService = roomService; }

    public record CreateRequest(@NotBlank @Size(max = 128) String title,
                                @Size(max = 512) String coverUrl) {}

    @PostMapping
    public ApiResponse<RoomView> create(@RequestBody @Validated CreateRequest req) {
        return ApiResponse.ok(roomService.create(AuthContext.requireUid(), req.title(), req.coverUrl()));
    }

    @PostMapping("/{roomId}/start")
    public ApiResponse<StartResult> start(@PathVariable String roomId) {
        return ApiResponse.ok(roomService.start(roomId, AuthContext.requireUid()));
    }

    @PostMapping("/{roomId}/republish")
    public ApiResponse<StartResult> republish(@PathVariable String roomId) {
        return ApiResponse.ok(roomService.republish(roomId, AuthContext.requireUid()));
    }

    @PostMapping("/{roomId}/stop")
    public ApiResponse<RoomView> stop(@PathVariable String roomId) {
        return ApiResponse.ok(roomService.stop(roomId, AuthContext.requireUid()));
    }

    @PostMapping("/{roomId}/enter")
    public ApiResponse<EnterResult> enter(@PathVariable String roomId) {
        return ApiResponse.ok(roomService.enter(roomId, AuthContext.requireUid()));
    }

    @GetMapping("/live")
    public ApiResponse<List<RoomView>> liveList() {
        return ApiResponse.ok(roomService.liveList());
    }

    @GetMapping("/mine")
    public ApiResponse<List<RoomView>> myRooms() {
        return ApiResponse.ok(roomService.myRooms(AuthContext.requireUid()));
    }

    @GetMapping("/{roomId}")
    public ApiResponse<RoomView> detail(@PathVariable String roomId) {
        return ApiResponse.ok(roomService.detail(roomId));
    }
}
