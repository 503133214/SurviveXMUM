package wiki.xmum.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {
    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式不正确")
    private String userEmail;

    @NotBlank(message = "请输入密码")
    private String password;
}
