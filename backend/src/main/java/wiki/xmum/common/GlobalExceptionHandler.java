package wiki.xmum.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException e) {
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getDefaultMessage() : "参数校验失败";
        return ApiResponse.error(400, msg);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ApiResponse<Void> handleDenied(AccessDeniedException e) {
        return ApiResponse.error(403, "没有权限执行该操作");
    }

    // ---- 以下都是“客户端把请求发错了”，属于 4xx。
    // 之前全部落到兜底分支，既回 500 误导调用方（前端提示“服务器内部错误”），
    // 又在日志里打完整堆栈，把真正的服务端故障淹没。这里按语义分流，只记一行 WARN。

    /** 请求体不是合法 JSON，或字段类型对不上（如 parentId 传了 "abc"）。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("请求体无法解析: {}", e.getMessage());
        return ApiResponse.error(400, "请求内容格式不正确");
    }

    /** 路径变量 / 查询参数类型不匹配，如 /comments/abc、?page=abc。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}={}", e.getName(), e.getValue());
        return ApiResponse.error(400, "参数 " + e.getName() + " 格式不正确");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return ApiResponse.error(400, "缺少参数 " + e.getParameterName());
    }

    /** 上传超过 spring.servlet.multipart 限制（10MB）。不处理的话用户只会看到“服务器内部错误”。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleTooLarge(MaxUploadSizeExceededException e) {
        return ApiResponse.error(400, "文件过大，单张图片不能超过 10MB");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<Void> handleMethod(HttpRequestMethodNotSupportedException e) {
        return ApiResponse.error(405, "该接口不支持 " + e.getMethod() + " 方法");
    }

    /** 访问了不存在的接口。Spring 6 会抛这个而不是直接 404，兜底会把它记成 ERROR。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResponse<Void> handleNoResource(NoResourceFoundException e) {
        return ApiResponse.error(404, "接口不存在");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        // 不把内部异常信息（SQL/路径等线索）回传给前端，只落服务端日志
        log.error("未处理异常: {}", e.getMessage(), e);
        return ApiResponse.error(500, "服务器内部错误，请稍后重试");
    }
}
