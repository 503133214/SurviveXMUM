package wiki.xmum.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.domain.dto.LoginDTO;
import wiki.xmum.domain.dto.RegisterDTO;
import wiki.xmum.domain.dto.ResetPasswordDTO;
import wiki.xmum.domain.vo.AuthVO;
import wiki.xmum.domain.vo.UserInfoVO;
import wiki.xmum.security.AuthUser;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.AuthService;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/send/code")
    public ApiResponse<Void> sendCode(@RequestParam String email, @RequestParam String type) {
        authService.sendCode(email, type);
        return ApiResponse.ok(null);
    }

    @PostMapping("/register")
    public ApiResponse<AuthVO> register(@RequestBody @Valid RegisterDTO dto) {
        return ApiResponse.ok(authService.register(dto));
    }

    @PostMapping("/login")
    public ApiResponse<AuthVO> login(@RequestBody @Valid LoginDTO dto) {
        return ApiResponse.ok(authService.login(dto));
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@RequestBody @Valid ResetPasswordDTO dto) {
        authService.resetPassword(dto);
        return ApiResponse.ok(null);
    }

    @PostMapping("/login/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        authService.logout(token);
        return ApiResponse.ok(null);
    }

    @GetMapping("/user/info")
    public ApiResponse<UserInfoVO> userInfo() {
        AuthUser u = CurrentUser.get();
        return ApiResponse.ok(authService.userInfo(u.getId()));
    }
}
