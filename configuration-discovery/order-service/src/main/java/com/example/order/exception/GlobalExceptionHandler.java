package com.example.order.exception;

import com.example.order.dto.ErrorResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
 @ExceptionHandler(ProductNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND) ErrorResponse notFound(ProductNotFoundException e){return error(HttpStatus.NOT_FOUND,e.getMessage());}
 @ExceptionHandler(InactiveProductException.class) @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY) ErrorResponse inactive(InactiveProductException e){return error(HttpStatus.UNPROCESSABLE_ENTITY,e.getMessage());}
 @ExceptionHandler(ProductServiceUnavailableException.class) @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE) ErrorResponse unavailable(ProductServiceUnavailableException e){return error(HttpStatus.SERVICE_UNAVAILABLE,e.getMessage());}
 @ExceptionHandler(ProductServiceException.class) @ResponseStatus(HttpStatus.BAD_GATEWAY) ErrorResponse product(ProductServiceException e){return error(HttpStatus.BAD_GATEWAY,e.getMessage());}
 @ExceptionHandler(MethodArgumentNotValidException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) ErrorResponse validation(MethodArgumentNotValidException e){return error(HttpStatus.BAD_REQUEST,"Validation failed");}
 private ErrorResponse error(HttpStatus status,String message){ return new ErrorResponse(status.value(),status.getReasonPhrase(),message,MDC.get("correlationId")); }
}
