package wiki.xmum.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

public final class JsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtil() {}

    /** 把存库的 JSON 数组字符串解析为 List<String>，失败/空返回空列表。 */
    public static List<String> toStringList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
