package wiki.xmum.domain.dto;

import lombok.Data;

@Data
public class UserUpsertDTO {
    private String email;
    private String password;
    private String nickname;
    private String role;
    private String status;
}
