# Frontend–Backend Integration

Use this guide when connecting the GAM frontend to the backend in a browser. For explanations of CSRF, tokens, refresh, and CORS, see [Frontend–Backend Concepts](frontend-backend-concepts.md).

## Browser-facing model

GAM uses one browser origin in production:

```text
Frontend: https://gam.org.br/
API:      https://gam.org.br/api/
```

The reverse proxy serves frontend files and forwards `/api/` to the backend. Use relative API URLs:

```javascript
fetch('/api/accounts/me')
```

Do not hard-code `http://localhost:8080` or a production API host in frontend code.

## Development proxy

Configure the frontend development server to preserve the production URL shape:

```text
Browser:           http://localhost:5173/api
Development proxy: http://localhost:8080
```

The browser should call `/api/...`; the development proxy forwards the request to the backend.

Do not add production CORS assumptions to frontend code just because the local servers use different ports. If separate origins are intentional, configure CORS, cookies, and CSRF explicitly.

## Authentication flow

1. Login returns an access token and sets the refresh-token cookie.
2. Keep the access token in application memory.
3. Let the browser manage the `HttpOnly` refresh-token cookie.
4. After a page reload, call `POST /api/auth/refresh`.
5. Store the new access token.
6. Call `GET /api/accounts/me` before showing authenticated UI.

When an access token expires, refresh once and retry the original request once. Share one refresh operation across concurrent requests. If refresh or `/accounts/me` returns `401`, clear authentication state and show the login flow.

Do not store refresh tokens in JavaScript, put tokens in URLs or logs, or treat an old access token as proof of authentication.

## CSRF request rules

For cookie-authenticated operations, send the CSRF header and validate the request origin as required by the browser-session contract. GAM login, refresh, and logout use cookie-to-header CSRF proof. Normal bearer-token requests do not need CSRF proof merely because they are authenticated.

Do not use a substring check for origins, such as `contains("gam.org.br")`. Do not treat CORS configuration as CSRF protection.

## Contract and source of truth

Use the [OpenAPI workflow](../openapi-workflow.md) to inspect endpoint paths, request shapes, responses, and security requirements. Use the [Browser Session and Frontend Integration](../../requirements/authentication/browser-session-and-frontend-integration.md) requirement for authentication behavior.

When this guide conflicts with an accepted Requirement Specification, report the conflict and follow the requirement until the guide is corrected.
