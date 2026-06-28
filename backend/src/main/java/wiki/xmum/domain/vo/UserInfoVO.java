package wiki.xmum.domain.vo;

import lombok.Data;
import wiki.xmum.domain.po.User;

@Data
public class UserInfoVO {
    private Long id;
    private String userEmail;
    private String nickname;
    private String avatar;
    private String role;

    public static UserInfoVO from(User u) {
        UserInfoVO vo = new UserInfoVO();
        vo.id = u.getId();
        vo.userEmail = u.getEmail();
        vo.nickname = u.getNickname();
        vo.avatar = u.getAvatar();
        vo.role = u.getRole();
        return vo;
    }
}
