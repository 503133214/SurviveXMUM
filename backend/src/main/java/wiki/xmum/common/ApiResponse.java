package wiki.xmum.common;

import lombok.Data;

/**
 * 统一响应封装，与前端 net/index.js 约定一致：code=0 表示成功。
 */
@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private String traceId;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
