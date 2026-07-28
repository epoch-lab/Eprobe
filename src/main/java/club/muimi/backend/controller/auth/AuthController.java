package club.muimi.backend.controller.auth;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.dto.auth.*;
import club.muimi.backend.service.auth.AuthService;
import club.muimi.backend.vo.auth.CurrentUserVo;
import club.muimi.backend.vo.auth.LoginResultVo;
import club.muimi.backend.vo.auth.RegisterResultVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/send-email-code")
    public ApiResponse<Void> sendEmailCode(
            @Valid @RequestBody SendEmailCodeRequest request,
            HttpServletRequest httpServletRequest
    ) {
        authService.sendEmailCode(request, httpServletRequest);
        return ApiResponse.success(null, "验证码已发送");
    }

    @PostMapping("/register")
    public ApiResponse<RegisterResultVo> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResultVo result = authService.register(request);
        return ApiResponse.success(result, "注册成功");
    }

    @PostMapping("/login")
    public ApiResponse<LoginResultVo> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        LoginResultVo result = authService.login(request, httpServletRequest, response);
        return ApiResponse.success(result, "登录成功");
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ApiResponse.success(null, "退出成功");
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserVo> getCurrentUser() {
        return ApiResponse.success(authService.getCurrentUser(), "ok");
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpServletRequest
    ) {
        authService.forgotPassword(request, httpServletRequest);
        return ApiResponse.success(null, "找回密码验证码已发送");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success(null, "重置密码成功");
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        authService.changePassword(request, httpServletRequest, httpServletResponse);
        return ApiResponse.success(null, "修改密码成功，请重新登录");
    }
}
