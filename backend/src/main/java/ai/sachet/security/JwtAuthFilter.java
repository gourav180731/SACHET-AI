package ai.sachet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Stateless JWT filter: verifies Bearer tokens into {@link SachetPrincipal}. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwt;

  public JwtAuthFilter(JwtService jwt) {
    this.jwt = jwt;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
      FilterChain chain) throws ServletException, IOException {
    String header = req.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7).trim();
      if (!token.isEmpty()) {
        try {
          SachetPrincipal principal = jwt.verify(token);
          var auth = new UsernamePasswordAuthenticationToken(
              principal, null, principal.getAuthorities());
          SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (AuthException e) {
          SecurityContextHolder.clearContext(); // entry point below renders the 401
        }
      }
    }
    chain.doFilter(req, res);
  }
}
