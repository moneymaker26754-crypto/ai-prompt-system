package com.jojo.prompt.common.exception;

import com.jojo.prompt.common.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        BindingResult result = e.getBindingResult();
        String message = result.getFieldError() != null
                ? result.getFieldError().getDefaultMessage()
                : "ParameterValidationFailed";

        log.warn("ParameterValidationFailed: {}", message);
        return Result.error(400, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadable: {}", e.getMostSpecificCause().getMessage());
        return Result.error(400, "request body is not valid JSON");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKeyException(DuplicateKeyException e) {
        String errorMessage = e.getMostSpecificCause() != null
                ? e.getMostSpecificCause().getMessage()
                : e.getMessage();
        String message = "username and email already exists";

        if (errorMessage != null) {
            String lower = errorMessage.toLowerCase();
            if (lower.contains("uk_username")) {
                message = "username already exists";
            }
            if (lower.contains("uk_email")) {
                message = "email already exists";
            }
        }

        log.warn("DuplicateKeyException: {}", errorMessage);
        return Result.error(400, message);
    }
    //@RequestParam/@PathVariable校验，在controller层加上@Validated注解
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("parameter validation failed");
        log.warn("ConstraintViolationException: {}", message);
        return Result.error(400, message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        log.debug("NoResourceFound: {}", e.getResourcePath());
        return Result.error(404, "resource not found");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("UnhandledException", e);
        return Result.error("Busy");
    }
}
