package ee.tuleva.onboarding.auth.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthorizationFilter extends OncePerRequestFilter {
  private final JwtTokenUtil jwtTokenUtil;

  @Override
  @SneakyThrows
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
    final var requestTokenHeader = request.getHeader("Authorization");
    if (StringUtils.startsWith(requestTokenHeader, "Bearer ")) {
      final var jwtToken = requestTokenHeader.substring(7);
      try {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
          final var authenticationToken =
              new UsernamePasswordAuthenticationToken(
                  jwtTokenUtil.getUsernameFromToken(jwtToken),
                  jwtToken,
                  jwtTokenUtil.getAuthoritiesFromToken(jwtToken).stream()
                      .map(SimpleGrantedAuthority::new)
                      .toList());
          authenticationToken.setDetails(
              new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }
      } catch (IllegalArgumentException e) {
        logger.error("Unable to fetch JWT Token");
      } catch (ExpiredJwtException e) {
        logger.error("JWT Token is expired");
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
    } else {
      logger.warn("JWT Token does not begin with Bearer String");
    }
    filterChain.doFilter(request, response);
  }
}
