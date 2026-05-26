# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — Production dependencies (prod-deps)
# ─────────────────────────────────────────────────────────────────────────────
# Install ONLY production dependencies (--omit=dev) so this layer is cached
# and re-used directly by the runtime stage. By separating prod and dev deps
# into distinct stages, the final image never touches the npm registry at all
# — it only copies artefacts from earlier stages.
FROM node:20-alpine AS prod-deps
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev --silent && npm cache clean --force

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — All dependencies (build-deps)
# ─────────────────────────────────────────────────────────────────────────────
# Install full dependency tree including devDependencies needed to compile.
# TypeScript, ts-jest, ESLint, @types/* — none of these belong in the runtime.
FROM node:20-alpine AS build-deps
WORKDIR /app
COPY package*.json ./
RUN npm ci --silent

# ─────────────────────────────────────────────────────────────────────────────
# Stage 3 — Build
# ─────────────────────────────────────────────────────────────────────────────
FROM node:20-alpine AS builder
WORKDIR /app
# Pull full node_modules from build-deps (avoids reinstalling).
COPY --from=build-deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# ─────────────────────────────────────────────────────────────────────────────
# Stage 4 — Runtime
# ─────────────────────────────────────────────────────────────────────────────
# Alpine keeps the image small. Both artefacts are copied from earlier stages:
#   - node_modules from prod-deps (no devDependencies, no TypeScript compiler).
#   - dist/         from builder  (compiled JS).
# The network is never touched here — deterministic and cache-friendly.
FROM node:20-alpine AS runtime

LABEL org.opencontainers.image.title="nyxn-notification-service" \
      org.opencontainers.image.description="NYXN notification microservice — NestJS / Claude AI"

# Non-root user: same reasoning as the backend Dockerfile.
RUN addgroup -S nyxn && adduser -S nyxn -G nyxn
WORKDIR /app
RUN chown nyxn:nyxn /app

# Copy only what the runtime needs.
COPY --from=prod-deps /app/node_modules ./node_modules
COPY --from=builder   /app/dist         ./dist
COPY package*.json ./

RUN chown -R nyxn:nyxn /app

USER nyxn

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:3000/health | grep -q '"status":"ok"' || exit 1

CMD ["node", "dist/main"]
