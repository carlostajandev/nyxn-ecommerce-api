# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — Dependencies
# ─────────────────────────────────────────────────────────────────────────────
# Separate the npm install from the build step. node_modules changes only when
# package.json changes — this layer is cached across every code-only change.
FROM node:20-alpine AS deps
WORKDIR /app
COPY package*.json ./
# --omit=dev installs only production dependencies in this stage.
# We install ALL deps here (dev included) because we still need to run `nest build`.
RUN npm ci --silent

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — Build
# ─────────────────────────────────────────────────────────────────────────────
FROM node:20-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# ─────────────────────────────────────────────────────────────────────────────
# Stage 3 — Runtime
# ─────────────────────────────────────────────────────────────────────────────
# Alpine keeps the image small. Only the compiled dist/ and production
# node_modules are copied — devDependencies stay in the build stage.
FROM node:20-alpine AS runtime

LABEL org.opencontainers.image.title="nyxn-notification-service" \
      org.opencontainers.image.description="NYXN notification microservice — NestJS / Claude AI"

# Non-root user: same reasoning as the backend Dockerfile.
RUN addgroup -S nyxn && adduser -S nyxn -G nyxn
WORKDIR /app
RUN chown nyxn:nyxn /app

# Install only production dependencies fresh in the runtime stage — avoids
# carrying build tools (TypeScript compiler, ts-jest, etc.) into the final image.
COPY package*.json ./
RUN npm ci --omit=dev --silent && npm cache clean --force

COPY --from=builder /app/dist ./dist
RUN chown -R nyxn:nyxn /app

USER nyxn

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:3000/health | grep -q '"status":"ok"' || exit 1

CMD ["node", "dist/main"]
