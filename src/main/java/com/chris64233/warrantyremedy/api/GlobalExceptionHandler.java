package com.chris64233.warrantyremedy.api;

import com.chris64233.warrantyremedy.api.Views.ErrorResponse;
import com.chris64233.warrantyremedy.service.BusinessRuleException;
import com.chris64233.warrantyremedy.service.ConcurrencyConflictException;
import com.chris64233.warrantyremedy.service.ResourceNotFoundException;
import com.chris64233.warrantyremedy.service.ValidationException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 业务异常到 HTTP 状态码的统一映射：
 * 404 资源不存在；422 输入/资格校验失败；409 业务规则与并发冲突；400 请求格式错误。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", e.getMessage());
    }

    @ExceptionHandler(ConcurrencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleConcurrency(ConcurrencyConflictException e) {
        return build(HttpStatus.CONFLICT, "CONCURRENCY_CONFLICT", e.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessRuleException e) {
        return build(HttpStatus.CONFLICT, "BUSINESS_RULE_VIOLATION", e.getMessage());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleOptimistic(ObjectOptimisticLockingFailureException e) {
        return build(HttpStatus.CONFLICT, "CONCURRENCY_CONFLICT",
                "并发修改冲突，请重试：" + e.getMessage());
    }

    /**
     * 唯一约束冲突在事务边界外翻译（此时事务已回滚，不会出现"占用后继续写入"）。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        String message = rootMessage(e);
        String code;
        String text;
        if (message.contains("UK_CLAIM_EXTERNAL_REF")) {
            code = "IDEMPOTENCY_CONFLICT";
            text = "外部申请号已存在，请使用原外部申请号查询（幂等）";
        } else if (message.contains("UK_CLAIM_ACTIVE_DEDUPE")) {
            code = "DUPLICATE_ACTIVE_CLAIM";
            text = "同一产品同一故障已有一笔活动申请";
        } else if (message.contains("UK_DISPOSITION_CLAIM")) {
            code = "CONCURRENCY_CONFLICT";
            text = "该申请已存在处置，并发批准只有一笔成功";
        } else if (message.contains("UK_PRODUCT_SERIAL")) {
            code = "BUSINESS_RULE_VIOLATION";
            text = "序列号已存在";
        } else {
            code = "DATA_INTEGRITY_VIOLATION";
            text = "数据约束冲突：" + message;
        }
        return build(HttpStatus.CONFLICT, code, text);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .findFirst()
                .orElse("请求参数不合法");
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(error, message, LocalDateTime.now(clock)));
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return String.valueOf(cur.getMessage()).toUpperCase();
    }
}
