package wiki.xmum.common;

import lombok.Getter;

/**
 * 业务异常。code 非 0，会被 GlobalExceptionHandler 转成 ApiResponse.error。
 */
@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(String message) {
        super(message);
        this.code = 400;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
