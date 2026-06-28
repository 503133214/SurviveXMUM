package wiki.xmum.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDTO {
    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式不正确")
    private String userEmail;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 20, message = "密码长度应为 6-20 位")
    private String password;

    @NotBlank(message = "请输入验证码")
    private String code;
}
