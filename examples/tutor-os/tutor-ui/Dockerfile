# syntax=docker/dockerfile:1.7
#
# tutor-ui Dockerfile — multi-stage build for the React/Vite app.
#
# Stage 1 (build) installs deps and runs `npm run build` (which is
# `tsc -b && vite build`). Vite emits static assets into /app/dist.
#
# Stage 2 (runtime) is a slim nginx that serves /app/dist on :80 and
# proxies /api/* + /session/* + /quiz/* + /progress/* + /plan/* + the
# WebSocket endpoint to the tutor-api service. Routing rules live in
# nginx.conf next to this file.
#
# Build context: tutor-ui directory.
#   docker build -f tutor-ui/Dockerfile -t tutoros-ui ./tutor-ui
# OR via docker-compose.yml in the parent directory.

# ── Stage 1: build ───────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /app

# Lock-file first → npm ci layer caches across source changes.
COPY package.json package-lock.json* ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

# The rest of the source.
COPY . ./

# VITE_LIVE_BACKEND=true forces the live API path; the proxy in nginx
# rewrites /api → tutor-api, so the browser sees same-origin URLs.
# VITE_API_BASE_URL stays empty (default) so requests are same-origin.
ENV VITE_LIVE_BACKEND=true

RUN npm run build

# ── Stage 2: runtime ─────────────────────────────────────────────────
FROM nginx:1.27-alpine AS runtime
# Default nginx config replaced — we route SPA fallbacks + /api proxy.
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O - http://localhost/healthz || exit 1
