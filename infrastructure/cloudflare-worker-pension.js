/**
 * CloudFlare Worker for pension.tuleva.ee (PRODUCTION)
 * Routes /api/* and /oauth/* requests to backend (stripping /api prefix), everything else to S3
 * Route: pension.tuleva.ee/*
 * Architecture:
 * - Visitors access: https://pension.tuleva.ee (HTTPS via CloudFlare)
 * - Worker routes /api/authenticate → https://ecs-onboarding-service.tuleva.ee/authenticate (HTTPS)
 * - Worker routes /api/v1/* → https://ecs-onboarding-service.tuleva.ee/v1/* (HTTPS)
 * - Worker routes /oauth/* → https://ecs-onboarding-service.tuleva.ee/oauth/* (HTTPS)
 * - Worker routes other requests to: http://pension.tuleva.ee.s3-website.eu-central-1.amazonaws.com (HTTP)
 * - S3 website endpoints only support HTTP, worker handles the protocol translation
 *
 * Testing:
 * curl -X POST https://pension.tuleva.ee/api/authenticate \
 *   -H "Content-Type: application/json" \
 *   -d '{"phoneNumber":"test","personalCode":"test","type":"MOBILE_ID"}'
 *
 * curl -X POST https://pension.tuleva.ee/oauth/refresh-token \
 *   -H "Content-Type: application/json" \
 *   -d '{"refresh_token":"test"}'
 */

export default {
  async fetch(request) {
    const url = new URL(request.url);

    // Route API requests to ECS backend (HTTPS)
    // IMPORTANT: Strip /api prefix since backend endpoints don't use it
    if (url.pathname.startsWith('/api/')) {
      const backendUrl = new URL(request.url);
      backendUrl.hostname = 'ecs-onboarding-service.tuleva.ee';

      // Strip /api prefix: /api/authenticate → /authenticate, /api/v1/funds → /v1/funds
      backendUrl.pathname = backendUrl.pathname.replace(/^\/api/, '');

      return fetch(backendUrl.toString(), {
        method: request.method,
        headers: request.headers,
        body: request.body,
        redirect: 'follow',
      });
    }

    // Route OAuth requests to ECS backend (HTTPS)
    // These paths don't need /api prefix transformation
    if (url.pathname.startsWith('/oauth/')) {
      const backendUrl = new URL(request.url);
      backendUrl.hostname = 'ecs-onboarding-service.tuleva.ee';

      return fetch(backendUrl.toString(), {
        method: request.method,
        headers: request.headers,
        body: request.body,
        redirect: 'follow',
      });
    }

    // Route everything else to S3 website endpoint (HTTP)
    // S3 website endpoints only support HTTP, not HTTPS
    const s3Url = new URL(request.url);
    s3Url.protocol = 'http:';
    s3Url.hostname = 'pension.tuleva.ee.s3-website.eu-central-1.amazonaws.com';

    return fetch(s3Url.toString(), {
      method: request.method,
      headers: request.headers,
    });
  },
};
