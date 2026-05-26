# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — Production dependencies (prod-deps)
# ─────────────────────────────────────────────────────────────────────────────
# Install ONLY production dependencies (--prod) so this layer is cached and
# re-used directly by the runtime stage. The final image never touches the
# registry — it only copies artifacts from earlier stages.
FROM node:20-alpine AS prod-deps
# pnpm 9 is stable on Node 20-alpine and matches the lockfileVersion: 9.0.
# pnpm 11 has a known ERR_UNKNOWN_BUILTIN_MODULE bug on Node 20.20.x alpine.
RUN npm install -g pnpm@9 --silent
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile --prod && pnpm store prune

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — All dependencies (build-deps)
# ─────────────────────────────────────────────────────────────────────────────
# Full dependency tree including devDependencies (TypeScript compiler, @types/*).
# Separated from prod-deps so devDependencies never end up in the runtime image.
FROM node:20-alpine AS build-deps
RUN npm install -g pnpm@9 --silent
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# ─────────────────────────────────────────────────────────────────────────────
# Stage 3 — Build
# ─────────────────────────────────────────────────────────────────────────────
FROM node:20-alpine AS builder
WORKDIR /app
# Pull full node_modules from build-deps (avoids reinstalling).
COPY --from=build-deps /app/node_modules ./node_modules
COPY . .
RUN node_modules/.bin/nest build

# ─────────────────────────────────────────────────────────────────────────────
# Stage 4 — Runtime
# ─────────────────────────────────────────────────────────────────────────────
# Alpine keeps the image small. Both artifacts are copied from earlier stages:
#   - node_modules from prod-deps (no devDependencies, no TypeScript compiler).
#   - dist/         from builder  (compiled JS).
# The network is never touched here — deterministic and cache-friendly.
FROM node:20-alpine AS runtime

LABEL org.opencontainers.image.title="nyxn-notification-service" \
      org.opencontainers.image.description="NYXN notification microservice — NestJS / Claude AI"

# Non-root user for least-privilege execution.
RUN addgroup -S nyxn && adduser -S nyxn -G nyxn
WORKDIR /app
RUN chown nyxn:nyxn /app

# Copy only what the runtime needs.
COPY --from=prod-deps /app/node_modules ./node_modules
COPY --from=builder   /app/dist         ./dist
COPY package.json ./

RUN chown -R nyxn:nyxn /app

USER nyxn

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:3000/health | grep -q '"status":"ok"' || exit 1

CMD ["node", "dist/main"]
