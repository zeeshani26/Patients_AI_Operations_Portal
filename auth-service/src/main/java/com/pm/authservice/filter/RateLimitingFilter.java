package com.pm.authservice.filter;

import io.github.bucket4j.Bucket;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class RateLimitingFilter implements Filter {

  /** HTTP 429 — not all Jakarta Servlet API versions expose {@code SC_TOO_MANY_REQUESTS}. */
  private static final int STATUS_TOO_MANY_REQUESTS = 429;

  // 10 requests per minute (single shared bucket; same behavior as prior LocalBucketBuilder usage)
  private final Bucket bucket = Bucket.builder()
      .addLimit(limit -> limit.capacity(10).refillGreedy(10, Duration.ofMinutes(1)))
      .build();

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    // Apply rate limiting to login endpoint
    if (httpRequest.getRequestURI().contains("/login")) {
      if (!bucket.tryConsume(1)) {
        httpResponse.setStatus(STATUS_TOO_MANY_REQUESTS);
        httpResponse.getWriter().write("{\"error\": \"Rate limit exceeded. Please try again later.\"}");
        httpResponse.setContentType("application/json");
        return;
      }
    }

    chain.doFilter(request, response);
  }
}


