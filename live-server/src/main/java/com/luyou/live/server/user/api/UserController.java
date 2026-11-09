package com.luyou.live.server.user.api;

import com.luyou.live.server.common.ApiResponse;
import com.luyou.live.server.common.AuthContext;
import com.luyou.live.server.user.application.UserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) { this.userService = userService; }

    public record RegisterRequest(@NotBlank @Size(min = 3, max = 32) String username,
                                  @NotBlank @Size(min = 6, max = 64) String password,
                                  @Size(max = 32) String nickname) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    @PostMapping("/users/register")
    public ApiResponse<UserService.LoginResult> register(@RequestBody @Validated RegisterRequest req) {
        return ApiResponse.ok(userService.register(req.username(), req.password(), req.nickname()));
    }

    @PostMapping("/users/login")
    public ApiResponse<UserService.LoginResult> login(@RequestBody @Validated LoginRequest req) {
        return ApiResponse.ok(userService.login(req.username(), req.password()));
    }

    @GetMapping("/users/me")
    public ApiResponse<UserService.UserProfile> me() {
        return ApiResponse.ok(userService.me(AuthContext.requireUid()));
    }
}
