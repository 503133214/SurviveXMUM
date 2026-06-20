package wiki.xmum.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import wiki.xmum.common.BizException;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码：生成 6 位码存 Redis(TTL 5min)，60s 发送频控。
 * 若未配置 SMTP（mailSender 为空或 mail.enabled=false），开发期把验证码写入日志，便于本地联调。
 */
@Slf4j
@Service
public class VerificationCodeService {

    private static final long TTL_SECONDS = 5 * 60;
    private static final long COOLDOWN_SECONDS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${wiki.mail.enabled:false}")
    private boolean mailEnabled;

    public VerificationCodeService(StringRedisTemplate redis,
                                   org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.redis = redis;
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    private String codeKey(String email, String type) {
        return "wiki:code:" + type + ":" + email.toLowerCase();
    }

    private String cooldownKey(String email, String type) {
        return "wiki:code:cd:" + type + ":" + email.toLowerCase();
    }

    public void send(String email, String type) {
        String cdKey = cooldownKey(email, type);
        if (Boolean.TRUE.equals(redis.hasKey(cdKey))) {
            throw new BizException("发送过于频繁，请稍后再试");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redis.opsForValue().set(codeKey(email, type), code, TTL_SECONDS, TimeUnit.SECONDS);
        redis.opsForValue().set(cdKey, "1", COOLDOWN_SECONDS, TimeUnit.SECONDS);

        if (mailEnabled && mailSender != null) {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromAddress);
                msg.setTo(email);
                msg.setSubject("【SurviveXMUM Wiki】验证码");
                msg.setText("你的验证码是：" + code + "\n\n5 分钟内有效。如果不是你本人操作，请忽略本邮件。");
                mailSender.send(msg);
            } catch (Exception e) {
                log.error("发送验证码邮件失败 to={}", email, e);
                throw new BizException("验证码邮件发送失败，请稍后重试");
            }
        } else {
            // 开发模式：未启用邮件，打印到日志方便联调
            log.warn("[DEV] 邮件未启用，{} 的验证码({})为：{}", email, type, code);
        }
    }

    /** 校验并消费验证码（成功后删除）。 */
    public void verify(String email, String type, String code) {
        String key = codeKey(email, type);
        String saved = redis.opsForValue().get(key);
        if (saved == null) {
            throw new BizException("验证码已过期，请重新获取");
        }
        if (!saved.equals(code)) {
            throw new BizException("验证码不正确");
        }
        redis.delete(key);
    }
}
