package ai.sachet.web;

import ai.sachet.security.AuthException;
import ai.sachet.service.ModelPolicy.ExternalTransferBlockedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Safe error envelopes. Clients get {@code {error:{code,message[,requestId]}}};
 * internals (stack traces, SQL, paths, credentials) stay in the secure log.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private String requestId(HttpServletRequest req) {
    Object id = req.getAttribute(RequestFilter.REQUEST_ID);
    return id == null ? "unknown" : String.valueOf(id);
  }

  private ResponseEntity<Map<String, Object>> envelope(HttpStatus status, String message,
      HttpServletRequest req, boolean withId) {
    var err = new java.util.HashMap<String, Object>();
    err.put("code", status.value());
    err.put("message", message);
    if (withId) {
      err.put("requestId", requestId(req));
    }
    return ResponseEntity.status(status).body(Map.of("error", err));
  }

  @ExceptionHandler(AuthException.class)
  public ResponseEntity<Map<String, Object>> auth(AuthException e, HttpServletRequest req) {
    return envelope(HttpStatus.UNAUTHORIZED, e.getMessage(), req, false);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> denied(AccessDeniedException e, HttpServletRequest req) {
    return envelope(HttpStatus.FORBIDDEN, "Forbidden.", req, false);
  }

  @ExceptionHandler(ExternalTransferBlockedException.class)
  public ResponseEntity<Map<String, Object>> blocked(ExternalTransferBlockedException e,
      HttpServletRequest req) {
    return envelope(HttpStatus.FORBIDDEN, e.getMessage(), req, false);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e,
      HttpServletRequest req) {
    // Request-shape feedback only; nothing about internals.
    String first = e.getBindingResult().getFieldErrors().stream()
        .map(f -> f.getField() + ": invalid").findFirst().orElse("Invalid request.");
    return envelope(HttpStatus.BAD_REQUEST, first, req, false);
  }

  @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
  public ResponseEntity<Map<String, Object>> notFound(Exception e, HttpServletRequest req) {
    return envelope(HttpStatus.NOT_FOUND, "Not found.", req, false);
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Map<String, Object>> missing(NotFoundException e, HttpServletRequest req) {
    return envelope(HttpStatus.NOT_FOUND, "Not found.", req, false);
  }

  @ExceptionHandler(ai.sachet.ingest.ValidationException.class)
  public ResponseEntity<Map<String, Object>> invalidUpload(ai.sachet.ingest.ValidationException e,
      HttpServletRequest req) {
    HttpStatus status = ai.sachet.domain.IngestionJob.Errors.LIMIT_EXCEEDED.equals(e.getCode())
        ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST;
    var err = new java.util.HashMap<String, Object>();
    err.put("code", status.value());
    err.put("message", e.getMessage());
    err.put("errorCode", e.getCode());
    return ResponseEntity.status(status).body(Map.of("error", err));
  }

  @ExceptionHandler(JobStateException.class)
  public ResponseEntity<Map<String, Object>> jobState(JobStateException e, HttpServletRequest req) {
    return envelope(HttpStatus.CONFLICT, e.getMessage(), req, false);
  }

  @ExceptionHandler(ai.sachet.rag.EmbeddingUnavailableException.class)
  public ResponseEntity<Map<String, Object>> embeddingDown(
      ai.sachet.rag.EmbeddingUnavailableException e, HttpServletRequest req) {
    // Engine missing/model corrupt: explicit 503, never a silent degraded answer.
    return envelope(HttpStatus.SERVICE_UNAVAILABLE, "Search unavailable.", req, false);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> badArgument(IllegalArgumentException e,
      HttpServletRequest req) {
    return envelope(HttpStatus.BAD_REQUEST, e.getMessage(), req, false);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> unhandled(Exception e, HttpServletRequest req) {
    log.error("unhandled error requestId={} type={}", requestId(req), e.getClass().getSimpleName());
    return envelope(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error.", req, true);
  }
}
