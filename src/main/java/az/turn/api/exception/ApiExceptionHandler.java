package az.turn.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Comparator;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(SessionAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleSessionAuthentication(
            SessionAuthenticationException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNAUTHORIZED, exception.getState().name(), exception.getMessage(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null || exception.getReason().isBlank() ? "Xəta baş verdi." : exception.getReason();
        return error(status, "REQUEST_REJECTED", message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(error -> error.getField()))
                .map(error -> error.getDefaultMessage() == null ? "Daxil edilən məlumat düzgün deyil." : error.getDefaultMessage())
                .findFirst().orElse("Daxil edilən məlumat düzgün deyil.");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Sorğu məlumatları natamam və ya düzgün formatda deyil.", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .sorted()
                .findFirst().orElse("Sorğu parametrləri düzgün deyil.");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "Məlumat mövcud qeyd və ya biznes qaydası ilə ziddiyyət təşkil edir.", request);
    }

    @ExceptionHandler(SecureUploadException.class)
    public ResponseEntity<ApiErrorResponse> handleSecureUpload(
            SecureUploadException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = switch (exception.getFailure()) {
            case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case MALWARE_DETECTED, INVALID_FILE, INVALID_IMAGE, IMAGE_DIMENSIONS_EXCEEDED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case SCANNER_UNAVAILABLE, STORAGE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case OWNER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case EMPTY_FILE, UNSUPPORTED_FILE_TYPE -> HttpStatus.BAD_REQUEST;
        };
        return error(
                status,
                "UPLOAD_" + exception.getFailure().name(),
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "UPLOAD_FILE_TOO_LARGE",
                "Faylın ölçüsü 5 MB-dan böyük ola bilməz.",
                request
        );
    }

    @ExceptionHandler(WalletTopUpException.class)
    public ResponseEntity<ApiErrorResponse> handleWalletTopUp(
            WalletTopUpException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = switch (exception.getFailure()) {
            case PACKAGE_NOT_FOUND -> HttpStatus.BAD_REQUEST;
            case ACTIVE_REQUEST_EXISTS, RECEIPT_ALREADY_SUBMITTED -> HttpStatus.CONFLICT;
            case REQUEST_NOT_FOUND, ATTACHMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RECEIPT_WINDOW_EXPIRED -> HttpStatus.GONE;
        };
        return error(status, "TOP_UP_" + exception.getFailure().name(), exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        logger.error("Unhandled API error: method={}, path={}", request.getMethod(), request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Daxili server xətası baş verdi.", request);
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                OffsetDateTime.now(), status.value(), status.getReasonPhrase(), code, message, request.getRequestURI()
        ));
    }
}
