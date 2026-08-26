package com.vetos.platform.web;

import com.vetos.platform.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Tek global hata yakalayici. Butun DomainException alt siniflari buradan
 * gecer; HTTP durum kodu, sinif adinin sonekine gore belirlenir
 * (coding-conventions.md'deki "<Durum>Exception" isimlendirme kurali):
 * *NotFoundException -> 404, *ConflictException -> 409, digerleri -> 422.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex, HttpServletRequest request) {
        HttpStatus status = resolveStatus(ex);
        return ResponseEntity.status(status)
            .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldErrorItem> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldErrorItem(fe.getField(), fe.getDefaultMessage()))
            .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.ofValidation("Girdi dogrulama hatasi", fieldErrors, request.getRequestURI()));
    }

    /**
     * Spring Security 6.3+ ile @PreAuthorize reddi artik AccessDeniedException'in
     * alt sinifi AuthorizationDeniedException firlatiyor -- bu handler olmadan
     * genel Exception handler'a dusup 500 donuyordu (api-conventions.md'deki
     * "403 = kimlik dogrulandi ama yetkisi yok" sozlesmesini bozan bir hataydi).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of("ACCESS_DENIED", "Bu islem icin yetkiniz yok", request.getRequestURI()));
    }

    /**
     * Govde JSON olarak parse edilemedigi zaman (ör. gecersiz tarih formati,
     * bozuk enum degeri) Jackson bir HttpMessageNotReadableException firlatir --
     * bu handler olmadan genel Exception handler'a dusup 500 donuyordu; oysa
     * bu istemci hatasi, api-conventions.md'deki "400 = validation hatasi"
     * sozlesmesine gore 400 olmali.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("MALFORMED_REQUEST_BODY", "Istek govdesi okunamadi veya gecersiz bicimde", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Beklenmeyen hata: {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of("INTERNAL_ERROR", "Beklenmeyen bir hata olustu", request.getRequestURI()));
    }

    private HttpStatus resolveStatus(DomainException ex) {
        String name = ex.getClass().getSimpleName();
        if (name.endsWith("NotFoundException")) {
            return HttpStatus.NOT_FOUND;
        }
        if (name.endsWith("ConflictException")) {
            return HttpStatus.CONFLICT;
        }
        if (name.endsWith("ForbiddenException")) {
            return HttpStatus.FORBIDDEN;
        }
        if (name.endsWith("InvalidCredentialsException") || name.endsWith("UnauthorizedException")) {
            return HttpStatus.UNAUTHORIZED;
        }
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
