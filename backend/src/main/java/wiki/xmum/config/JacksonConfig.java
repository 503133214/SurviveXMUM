package wiki.xmum.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;

/**
 * 雪花 ID（MyBatis-Plus assign_id）是 19 位 Long，超出 JS Number.MAX_SAFE_INTEGER（2^53-1，约 9e15），
 * 浏览器 JSON.parse 会丢精度（例如 ...418 被舍入成 ...400）。一旦前端用这个被改写的 id 回传后端
 * （如 GET /admin/revision/{id}、approve/reject），就会查不到记录而 404 —— 表现为“管理后台点开投稿无详情/审核失败”。
 *
 * 解决：全局把 Long / BigInteger 序列化为字符串。前端把 id 当作不透明字符串透传即可，后端 @PathVariable Long 反解无碍。
 * 本项目对外暴露的 Long 仅有各 VO 的 id 字段（viewCount 是 Integer，时间已是 String），改为字符串无副作用。
 *
 * 以 Module @Bean 形式注册：Spring Boot 会把它“追加”到自动配置的 ObjectMapper 上，
 * 不会覆盖 JavaTimeModule 等已注册模块。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Module longToStringModule() {
        SimpleModule module = new SimpleModule("LongToString");
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        module.addSerializer(BigInteger.class, ToStringSerializer.instance);
        return module;
    }
}
