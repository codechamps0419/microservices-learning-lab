package com.example.product.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/*
 * Centralised exception handler for all controllers in this service.
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 * Every @ExceptionHandler method here automatically serialises its
 * return value to JSON — no need for @ResponseBody on each method.
 *
 * WHY CENTRALISE EXCEPTION HANDLING?
 * Without this, every controller method needs its own try-catch block.
 * That scatters error-handling logic across the codebase and makes it
 * inconsistent. One @RestControllerAdvice handles everything in one place.
 *
 * PRODUCTION MISTAKE TO AVOID:
 * Do not return exception.getMessage() directly for unexpected errors (500).
 * Stack traces and internal messages can leak implementation details,
 * file paths, or database schema information to the client.
 * Return a generic message for 500s and log the real exception server-side.
 *
 * ERROR RESPONSE STRUCTURE:
 * Every error response follows the same shape so API consumers can
 * handle errors consistently without parsing different formats.
 *
 * {
 *   "timestamp": "2024-01-15T10:30:00",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Product not found with id: ...",
 *   "path": null          <- populated in Phase 5 via HttpServletRequest
 * }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // -------------------------------------------------------------------------
    // 404 Not Found
    // -------------------------------------------------------------------------

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProductNotFound(
            ProductNotFoundException ex) {

        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // 409 Conflict
    // -------------------------------------------------------------------------

    @ExceptionHandler(DuplicateProductNameException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateProductName(
            DuplicateProductNameException ex) {

        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request — @Valid failures on request body fields
    //
    // Thrown by Spring when @Valid fails on a @RequestBody parameter.
    // Contains one FieldError per invalid field.
    //
    // We collect all field errors into a map so the client sees every
    // problem in one response instead of fixing errors one at a time.
    //
    // Example response:
    // {
    //   "timestamp": "...",
    //   "status": 400,
    //   "error": "Bad Request",
    //   "message": "Validation failed",
    //   "errors": {
    //     "price": "Price must be greater than zero",
    //     "name": "Product name is required"
    //   }
    // }
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        Map<String, Object> body = buildResponseBody(HttpStatus.BAD_REQUEST, "Validation failed");
        body.put("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request — unreadable or malformed JSON body
    //
    // Thrown when:
    // - The request body is not valid JSON
    // - An enum field receives an unknown value (e.g. status: "DELETED")
    // - A UUID field receives a non-UUID string
    //
    // Without this handler, Spring returns a generic 400 with a long
    // internal error message that leaks implementation details.
    // -------------------------------------------------------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableMessage(
            HttpMessageNotReadableException ex) {

        return buildResponse(HttpStatus.BAD_REQUEST,
                "Malformed request body. Check field types and accepted values.");
    }

    // -------------------------------------------------------------------------
    // 500 Internal Server Error — catch-all for unexpected exceptions
    //
    // IMPORTANT: We log the real exception here (in production use a logger)
    // but return only a generic message to the client.
    // -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        // In production: log.error("Unexpected error", ex);
        // We print to stderr here since we have no logger configured yet.
        ex.printStackTrace();
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    // -------------------------------------------------------------------------
    // Shared builder methods
    // -------------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(buildResponseBody(status, message));
    }

    private Map<String, Object> buildResponseBody(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
