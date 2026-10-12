package ai.sachet.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Carries/propagates X-Request-ID (for log correlation + error envelopes) and sets baseline security headers. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID = "X-Request-ID";

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
      FilterChain chain) throws ServletException, IOException {
    String id = req.getHeader(REQUEST_ID);
    if (id == null || id.isBlank() || id.length() > 128) {
      id = UUID.randomUUID().toString();
    }
    MDC.put("requestId", id);
    try {
      // Echo the id unless the caller supplied an overlong value (reflected header hygiene).
      res.setHeader(REQUEST_ID, id.length() > 128 ? UUID.randomUUID().toString() : id);
      res.setHeader("X-Content-Type-Options", "nosniff");
      res.setHeader("X-Frame-Options", "DENY");
      res.setHeader("Referrer-Policy", "no-referrer");
      req.setAttribute(REQUEST_ID, res.getHeader(REQUEST_ID));
      chain.doFilter(req, res);
    } finally {
      MDC.remove("requestId");
    }
  }
}
