package ee.tuleva.onboarding.auth.jwt;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
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
  private final PrincipalService principalService;

  @Override
  @SneakyThrows
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
    final var requestTokenHeader = request.getHeader("Authorization");
    if (StringUtils.startsWith(requestTokenHeader, "Bearer ")) {
      final var jwtToken = requestTokenHeader.substring(7);
      // Frontend sends null when token is missing, remove if fixed
      if (jwtToken.equals("null")) {
        filterChain.doFilter(request, response);
        return;
      }
      try {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
          AuthenticatedPerson principal =
              principalService.getFrom(
                  jwtTokenUtil.getPersonFromToken(jwtToken),
                  jwtTokenUtil.getAttributesFromToken(jwtToken));

          final var authorities =
              jwtTokenUtil.getAuthoritiesFromToken(jwtToken).stream()
                  .map(SimpleGrantedAuthority::new)
                  .toList();

          final var authenticationToken =
              new UsernamePasswordAuthenticationToken(principal, jwtToken, authorities);

          authenticationToken.setDetails(
              new WebAuthenticationDetailsSource().buildDetails(request));

          SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }
      } catch (ExpiredJwtException e) {
        logger.info("JWT Token is expired");
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
    }
    filterChain.doFilter(request, response);
  }
}
