package ee.tuleva.onboarding.auth.jwt;

import static ee.tuleva.onboarding.auth.jwt.TokenType.*;
import static ee.tuleva.onboarding.auth.jwt.TokenType.HANDOVER;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

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
      final var accessToken = requestTokenHeader.substring(7);
      // Frontend sends null when token is missing, remove if fixed
      if (accessToken.equals("null")) {
        filterChain.doFilter(request, response);
        return;
      }
      try {
        TokenType tokenType = jwtTokenUtil.getTypeFromToken(accessToken);
        if (!List.of(ACCESS, HANDOVER).contains(tokenType)) {
          return;
        }
        AuthenticatedPerson principal =
            principalService.getFrom(
                jwtTokenUtil.getPersonFromToken(accessToken),
                jwtTokenUtil.getAttributesFromToken(accessToken));

        final var authorities =
            jwtTokenUtil.getAuthorities(accessToken).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        final var authenticationToken =
            new UsernamePasswordAuthenticationToken(principal, accessToken, authorities);

        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
      } catch (ExpiredJwtException e) {
        logger.info("JWT Token is expired");
        SecurityContextHolder.clearContext();
        respondWithTokenExpired(response);
        return;
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
    }
    filterChain.doFilter(request, response);
  }

  private static void respondWithTokenExpired(HttpServletResponse response) throws IOException {
    Map<String, String> errorResponse = JwtTokenUtil.getExpiredTokenErrorResponse();
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    response.getWriter().write(JsonMapper.builder().build().writeValueAsString(errorResponse));
  }
}
