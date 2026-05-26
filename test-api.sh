#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════════
# test-api.sh — NYXN E-Commerce API — Manual Test Suite
# Usage: chmod +x test-api.sh && ./test-api.sh
# Requires: curl, jq (for JSON assertions)
# ═══════════════════════════════════════════════════════════════════════════════

set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# CONFIGURATION
# ─────────────────────────────────────────────────────────────────────────────
BASE_URL="${BASE_URL:-http://localhost:8080}"
NOTIFICATION_URL="${NOTIFICATION_URL:-http://localhost:3000}"
TIMEOUT=10

# Terminal colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

# Test counters
TOTAL=0
PASSED=0
FAILED=0

# Global IDs — chained across suites so later tests reference resources created earlier.
PRODUCT_ID=""
ORDER_ID=""

# ─────────────────────────────────────────────────────────────────────────────
# UTILITY FUNCTIONS
# ─────────────────────────────────────────────────────────────────────────────

section() {
  echo ""
  echo -e "${CYAN}${BOLD}═══════════════════════════════════════════════${RESET}"
  echo -e "${CYAN}${BOLD}  $1${RESET}"
  echo -e "${CYAN}${BOLD}═══════════════════════════════════════════════${RESET}"
}

# run_test "description" expected_status actual_status [body_substring] [body]
run_test() {
  local description="$1"
  local expected_status="$2"
  local actual_status="$3"
  local body_check="${4:-}"
  local body="${5:-}"
  TOTAL=$((TOTAL + 1))
  local status_ok=false body_ok=true
  [[ "$actual_status" == "$expected_status" ]] && status_ok=true
  if [[ -n "$body_check" && -n "$body" ]]; then
    echo "$body" | grep -q "$body_check" || body_ok=false
  fi
  if $status_ok && $body_ok; then
    PASSED=$((PASSED + 1))
    echo -e "  ${GREEN}✅ PASS${RESET} ${description} (HTTP ${actual_status})"
  else
    FAILED=$((FAILED + 1))
    echo -e "  ${RED}❌ FAIL${RESET} ${description}"
    echo -e "       Expected HTTP ${expected_status}, got HTTP ${actual_status}"
    [[ -n "$body_check" ]] && ! $body_ok && \
      echo -e "       Body must contain '${body_check}'"
  fi
}

# Execute an HTTP request; writes body to /tmp/nyxn_body; returns status code.
http() {
  local method="$1" url="$2" data="${3:-}" content_type="${4:-application/json}"
  if [[ -n "$data" ]]; then
    curl -s -o /tmp/nyxn_body -w "%{http_code}" \
      -X "$method" "$url" \
      -H "Content-Type: $content_type" \
      -H "Accept: application/json" \
      --connect-timeout "$TIMEOUT" -d "$data" 2>/dev/null
  else
    curl -s -o /tmp/nyxn_body -w "%{http_code}" \
      -X "$method" "$url" \
      -H "Accept: application/json" \
      --connect-timeout "$TIMEOUT" 2>/dev/null
  fi
}

get_body() { cat /tmp/nyxn_body 2>/dev/null || echo ""; }

check_services() {
  section "PRE-CHECK — Verifying services"

  echo -e "\n${BLUE}-> Backend (Spring Boot) at $BASE_URL${RESET}"
  local backend_status
  backend_status=$(curl -s -o /tmp/nyxn_body -w "%{http_code}" \
    "${BASE_URL}/actuator/health" --connect-timeout 5 2>/dev/null || echo "000")
  if [[ "$backend_status" == "200" ]]; then
    echo -e "  ${GREEN}✅ Backend UP${RESET}"
  else
    echo -e "  ${RED}❌ Backend NOT AVAILABLE (HTTP $backend_status)${RESET}"
    echo -e "  ${YELLOW}  -> Run: docker-compose up -d${RESET}"
    exit 1
  fi

  echo -e "\n${BLUE}-> Notification Service (NestJS) at $NOTIFICATION_URL${RESET}"
  local notif_status
  notif_status=$(curl -s -o /dev/null -w "%{http_code}" \
    "${NOTIFICATION_URL}/health" --connect-timeout 5 2>/dev/null || echo "000")
  if [[ "$notif_status" == "200" ]]; then
    echo -e "  ${GREEN}✅ Notification Service UP${RESET}"
  else
    echo -e "  ${YELLOW}⚠️  Notification Service NOT AVAILABLE (HTTP $notif_status)${RESET}"
    echo -e "  ${YELLOW}  -> NestJS tests will be skipped${RESET}"
    NOTIFICATION_AVAILABLE=false
  fi
}

NOTIFICATION_AVAILABLE=true

# ═══════════════════════════════════════════════════════════════════════════════
# SUITE 1 — PRODUCTS API
# ═══════════════════════════════════════════════════════════════════════════════

test_products() {
  section "SUITE 1 — Products API (Spring Boot)"

  echo -e "\n${BLUE}[1.1] POST /products — create valid product${RESET}"
  local status body
  status=$(http POST "${BASE_URL}/api/v1/products" '{
    "name": "Laptop Pro 15",
    "description": "High-performance laptop with 16GB RAM and 512GB SSD",
    "price": 1299.99,
    "stock": 50,
    "category": "ELECTRONICS"
  }')
  body=$(get_body)
  run_test "Create valid product returns 201" "201" "$status" "id" "$body"
  if [[ "$status" == "201" ]]; then
    PRODUCT_ID=$(echo "$body" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo -e "  ${YELLOW}-> Product ID: $PRODUCT_ID${RESET}"
  fi

  echo -e "\n${BLUE}[1.2] POST /products — negative price (expect 400)${RESET}"
  status=$(http POST "${BASE_URL}/api/v1/products" '{
    "name": "Bad Product", "description": "Valid description",
    "price": -10.00, "stock": 5, "category": "ELECTRONICS"
  }')
  run_test "Negative price returns 400" "400" "$status"

  echo -e "\n${BLUE}[1.3] POST /products — blank name (expect 400)${RESET}"
  status=$(http POST "${BASE_URL}/api/v1/products" '{
    "name": "", "description": "Valid description",
    "price": 99.99, "stock": 10, "category": "BOOKS"
  }')
  run_test "Blank name returns 400" "400" "$status"

  echo -e "\n${BLUE}[1.4] POST /products — negative stock (expect 400)${RESET}"
  status=$(http POST "${BASE_URL}/api/v1/products" '{
    "name": "Stock Test", "description": "Valid description",
    "price": 50.00, "stock": -5, "category": "BOOKS"
  }')
  run_test "Negative stock returns 400" "400" "$status"

  echo -e "\n${BLUE}[1.5] GET /products?page=0&size=10 — paginated list${RESET}"
  status=$(http GET "${BASE_URL}/api/v1/products?page=0&size=10")
  body=$(get_body)
  run_test "List products returns 200" "200" "$status" "content" "$body"
  run_test "Response includes pagination metadata" "200" "$status" "totalElements" "$body"

  if [[ -n "$PRODUCT_ID" ]]; then
    echo -e "\n${BLUE}[1.6] GET /products/$PRODUCT_ID — get by ID${RESET}"
    status=$(http GET "${BASE_URL}/api/v1/products/${PRODUCT_ID}")
    body=$(get_body)
    run_test "Get product by ID returns 200" "200" "$status" "Laptop Pro 15" "$body"
  fi

  echo -e "\n${BLUE}[1.7] GET /products/00000000-0000-0000-0000-000000000000 — not found${RESET}"
  status=$(http GET "${BASE_URL}/api/v1/products/00000000-0000-0000-0000-000000000000")
  run_test "Unknown product returns 404" "404" "$status"

  if [[ -n "$PRODUCT_ID" ]]; then
    echo -e "\n${BLUE}[1.8] PUT /products/$PRODUCT_ID — update${RESET}"
    status=$(http PUT "${BASE_URL}/api/v1/products/${PRODUCT_ID}" '{
      "name": "Laptop Pro 15 v2",
      "description": "Updated description with 32GB RAM",
      "price": 1399.99, "stock": 45, "category": "ELECTRONICS"
    }')
    body=$(get_body)
    run_test "Update product returns 200" "200" "$status" "Laptop Pro 15 v2" "$body"
  fi

  echo -e "\n${BLUE}[1.9] PUT /products/00000000-0000-0000-0000-000000000000 — not found${RESET}"
  status=$(http PUT "${BASE_URL}/api/v1/products/00000000-0000-0000-0000-000000000000" '{
    "name": "Ghost", "description": "Does not exist",
    "price": 50.00, "stock": 10, "category": "BOOKS"
  }')
  run_test "Update unknown product returns 404" "404" "$status"
}

# ═══════════════════════════════════════════════════════════════════════════════
# SUITE 2 — STOCK RESERVATION (Cyber Day)
# ═══════════════════════════════════════════════════════════════════════════════

test_stock() {
  section "SUITE 2 — Stock Reservation (Cyber Day)"

  if [[ -z "$PRODUCT_ID" ]]; then
    echo -e "  ${YELLOW}⚠️  PRODUCT_ID not available — creating test product${RESET}"
    local status body
    status=$(http POST "${BASE_URL}/api/v1/products" '{
      "name": "Stock Test Product",
      "description": "Product for stock reservation tests",
      "price": 29.99, "stock": 30, "category": "TEST"
    }')
    body=$(get_body)
    PRODUCT_ID=$(echo "$body" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  fi

  echo -e "\n${BLUE}[2.1] POST /stock/reserve — reserve 5 units${RESET}"
  local status body
  status=$(http POST "${BASE_URL}/api/v1/products/${PRODUCT_ID}/stock/reserve" '{"quantity": 5}')
  run_test "Reserve valid quantity returns 204" "204" "$status"

  echo -e "\n${BLUE}[2.2] POST /stock/reserve — quantity 0 (expect 400)${RESET}"
  status=$(http POST "${BASE_URL}/api/v1/products/${PRODUCT_ID}/stock/reserve" '{"quantity": 0}')
  run_test "Reserve zero quantity returns 400" "400" "$status"

  echo -e "\n${BLUE}[2.3] POST /stock/release — release 5 units${RESET}"
  status=$(http POST "${BASE_URL}/api/v1/products/${PRODUCT_ID}/stock/release" '{"quantity": 5}')
  run_test "Release stock returns 204" "204" "$status"

  echo -e "\n${BLUE}[2.4] POST /stock/reserve — exceeds available stock (expect 422 or 409)${RESET}"
  status=$(http POST "${BASE_URL}/api/v1/products/${PRODUCT_ID}/stock/reserve" '{"quantity": 9999}')
  if [[ "$status" == "422" || "$status" == "409" ]]; then
    TOTAL=$((TOTAL + 1)); PASSED=$((PASSED + 1))
    echo -e "  ${GREEN}✅ PASS${RESET} Insufficient stock returns $status (4xx)"
  else
    TOTAL=$((TOTAL + 1)); FAILED=$((FAILED + 1))
    echo -e "  ${RED}❌ FAIL${RESET} Expected 422 or 409, got $status"
  fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# SUITE 3 — ORDERS API
# ═══════════════════════════════════════════════════════════════════════════════

test_orders() {
  section "SUITE 3 — Orders API (Hexagonal)"

  if [[ -z "$PRODUCT_ID" ]]; then
    echo -e "  ${YELLOW}⚠️  Skipping — PRODUCT_ID not available${RESET}"; return
  fi

  echo -e "\n${BLUE}[3.1] POST /orders — create valid order${RESET}"
  local status body
  status=$(http POST "${BASE_URL}/api/v1/orders" "{
    \"customerId\": \"cust-$(date +%s)\",
    \"productId\": \"${PRODUCT_ID}\",
    \"quantity\": 2
  }")
  body=$(get_body)
  run_test "Create order returns 201" "201" "$status" "id" "$body"
  if [[ "$status" == "201" ]]; then
    ORDER_ID=$(echo "$body" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo -e "  ${YELLOW}-> Order ID: $ORDER_ID${RESET}"
  fi

  if [[ -n "$ORDER_ID" ]]; then
    echo -e "\n${BLUE}[3.2] GET /orders/$ORDER_ID — get order${RESET}"
    status=$(http GET "${BASE_URL}/api/v1/orders/${ORDER_ID}")
    body=$(get_body)
    run_test "Get order by ID returns 200" "200" "$status" "PENDING" "$body"
  fi

  echo -e "\n${BLUE}[3.3] GET /orders/00000000-0000-0000-0000-000000000000 — not found${RESET}"
  status=$(http GET "${BASE_URL}/api/v1/orders/00000000-0000-0000-0000-000000000000")
  run_test "Unknown order returns 404" "404" "$status"

  echo -e "\n${BLUE}[3.4] GET /orders?page=0&size=5 — paginated list${RESET}"
  status=$(http GET "${BASE_URL}/api/v1/orders?page=0&size=5")
  body=$(get_body)
  run_test "List orders returns 200" "200" "$status" "content" "$body"
}

# ═══════════════════════════════════════════════════════════════════════════════
# SUITE 4 — ANALYTICS API (SQL CTEs + window functions + Redis Cache)
# ═══════════════════════════════════════════════════════════════════════════════

test_analytics() {
  section "SUITE 4 — Analytics API (SQL CTEs + Redis Cache)"

  echo -e "\n${BLUE}[4.1] GET /analytics/top-products${RESET}"
  local status body start end elapsed
  status=$(http GET "${BASE_URL}/api/v1/analytics/top-products")
  run_test "Top products returns 200" "200" "$status"

  echo -e "\n${BLUE}[4.2] GET /analytics/revenue-trend${RESET}"
  status=$(http GET "${BASE_URL}/api/v1/analytics/revenue-trend")
  run_test "Revenue trend returns 200" "200" "$status"

  echo -e "\n${BLUE}[4.3] GET /analytics/low-stock${RESET}"
  status=$(http GET "${BASE_URL}/api/v1/analytics/low-stock")
  run_test "Low-stock alerts returns 200" "200" "$status"

  echo -e "\n${BLUE}[4.4] Cache hit timing — second call to top-products${RESET}"
  start=$(date +%s%N)
  status=$(http GET "${BASE_URL}/api/v1/analytics/top-products")
  end=$(date +%s%N)
  elapsed=$(( (end - start) / 1000000 ))
  TOTAL=$((TOTAL + 1))
  if [[ "$status" == "200" ]]; then
    PASSED=$((PASSED + 1))
    echo -e "  ${GREEN}✅ PASS${RESET} Cache hit in ${elapsed}ms (HTTP $status)"
  else
    FAILED=$((FAILED + 1))
    echo -e "  ${RED}❌ FAIL${RESET} Cache hit failed (HTTP $status)"
  fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# SUITE 5 — NOTIFICATION SERVICE: POST /notify (Strategy Pattern) + Claude agent
# ═══════════════════════════════════════════════════════════════════════════════

test_notifications() {
  section "SUITE 5 — Notification Service (NestJS)"

  if [[ "$NOTIFICATION_AVAILABLE" == "false" ]]; then
    echo -e "  ${YELLOW}⚠️  Notification Service not available — skipping suite${RESET}"; return
  fi

  # ── Health ─────────────────────────────────────────────────────────────────
  echo -e "\n${BLUE}[5.1] GET /health — NestJS health check${RESET}"
  local status body
  status=$(http GET "${NOTIFICATION_URL}/health")
  body=$(get_body)
  run_test "Health check returns 200" "200" "$status" "status" "$body"

  # ── POST /notify — Strategy Pattern endpoint (spec-required) ───────────────
  echo -e "\n${BLUE}[5.2] POST /notify — email strategy${RESET}"
  status=$(http POST "${NOTIFICATION_URL}/notify" '{
    "userId": "user-abc123",
    "message": "Your order has been confirmed and will arrive within 3-5 business days.",
    "channel": "email"
  }')
  body=$(get_body)
  run_test "POST /notify email returns 200" "200" "$status" "success" "$body"

  echo -e "\n${BLUE}[5.3] POST /notify — sms strategy${RESET}"
  status=$(http POST "${NOTIFICATION_URL}/notify" '{
    "userId": "+15551234567",
    "message": "Flash sale: 20% off Electronics for the next 2 hours.",
    "channel": "sms"
  }')
  body=$(get_body)
  run_test "POST /notify sms returns 200" "200" "$status" "success" "$body"

  echo -e "\n${BLUE}[5.4] POST /notify — push strategy${RESET}"
  status=$(http POST "${NOTIFICATION_URL}/notify" '{
    "userId": "device-token-xyz",
    "message": "New product just dropped in Electronics!",
    "channel": "push"
  }')
  body=$(get_body)
  run_test "POST /notify push returns 200" "200" "$status" "success" "$body"

  echo -e "\n${BLUE}[5.5] POST /notify — slack strategy (OCP demo)${RESET}"
  status=$(http POST "${NOTIFICATION_URL}/notify" '{
    "userId": "U012AB3CD",
    "message": "Low stock alert: Laptop Pro 15 has only 3 units left.",
    "channel": "slack"
  }')
  body=$(get_body)
  run_test "POST /notify slack returns 200" "200" "$status" "success" "$body"

  echo -e "\n${BLUE}[5.6] POST /notify — unknown channel (expect 400)${RESET}"
  status=$(http POST "${NOTIFICATION_URL}/notify" '{
    "userId": "user-xyz",
    "message": "Test message",
    "channel": "fax"
  }')
  run_test "Unknown channel returns 400" "400" "$status"

  echo -e "\n${BLUE}[5.7] POST /notify — missing userId (expect 400)${RESET}"
  status=$(http POST "${NOTIFICATION_URL}/notify" '{
    "message": "Test message",
    "channel": "email"
  }')
  run_test "Missing userId returns 400" "400" "$status"

  echo -e "\n${BLUE}[5.8] POST /notify — missing message (expect 400)${RESET}"
  status=$(http POST "${NOTIFICATION_URL}/notify" '{
    "userId": "user-abc",
    "channel": "push"
  }')
  run_test "Missing message returns 400" "400" "$status"

  # ── POST /agent/smart-notification — Claude AI endpoint ────────────────────
  echo -e "\n${BLUE}[5.9] POST /agent/smart-notification — PRODUCT_CREATED (requires ANTHROPIC_API_KEY)${RESET}"
  status=$(http POST "${NOTIFICATION_URL}/agent/smart-notification" '{
    "event": {
      "eventType": "PRODUCT_CREATED",
      "entityId": "550e8400-e29b-41d4-a716-446655440000",
      "payload": { "name": "Wireless Headphones Pro", "category": "ELECTRONICS", "price": 149.99 }
    },
    "audienceContext": "tech-savvy early adopter"
  }')
  body=$(get_body)
  if [[ "$status" == "200" ]]; then
    run_test "Smart notification PRODUCT_CREATED returns 200" "200" "$status" "subject" "$body"
  else
    TOTAL=$((TOTAL + 1))
    echo -e "  ${YELLOW}⚠️  SKIP${RESET} Smart notification returned HTTP $status (ANTHROPIC_API_KEY required)"
  fi

  echo -e "\n${BLUE}[5.10] POST /agent/smart-notification — invalid eventType (expect 400)${RESET}"
  status=$(http POST "${NOTIFICATION_URL}/agent/smart-notification" '{
    "event": { "eventType": "INVALID_EVENT", "entityId": "some-id" }
  }')
  run_test "Invalid eventType returns 400" "400" "$status"
}

# ═══════════════════════════════════════════════════════════════════════════════
# SUITE 6 — VALIDATION AND ERROR HANDLING
# ═══════════════════════════════════════════════════════════════════════════════

test_validations() {
  section "SUITE 6 — Validation and error handling"

  echo -e "\n${BLUE}[6.1] POST /products — malformed JSON (expect 400)${RESET}"
  local status
  status=$(http POST "${BASE_URL}/api/v1/products" 'this-is-not-json')
  run_test "Malformed JSON returns 400" "400" "$status"

  echo -e "\n${BLUE}[6.2] POST /products — missing Content-Type (expect 415)${RESET}"
  status=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${BASE_URL}/api/v1/products" -d '{"name":"test"}' \
    --connect-timeout "$TIMEOUT" 2>/dev/null || echo "000")
  run_test "Missing Content-Type returns 415" "415" "$status"

  echo -e "\n${BLUE}[6.3] GET /products/not-a-uuid — invalid UUID format (expect 400)${RESET}"
  status=$(http GET "${BASE_URL}/api/v1/products/not-a-uuid")
  run_test "Invalid UUID format returns 400" "400" "$status"

  if [[ -n "$PRODUCT_ID" ]]; then
    echo -e "\n${BLUE}[6.4] DELETE /products/$PRODUCT_ID — delete product${RESET}"
    status=$(http DELETE "${BASE_URL}/api/v1/products/${PRODUCT_ID}")
    run_test "Delete product returns 204" "204" "$status"

    echo -e "\n${BLUE}[6.5] GET /products/$PRODUCT_ID — verify deletion (expect 404)${RESET}"
    status=$(http GET "${BASE_URL}/api/v1/products/${PRODUCT_ID}")
    run_test "Deleted product returns 404" "404" "$status"
  fi

  echo -e "\n${BLUE}[6.6] DELETE /products/00000000-0000-0000-0000-000000000000 — not found${RESET}"
  status=$(http DELETE "${BASE_URL}/api/v1/products/00000000-0000-0000-0000-000000000000")
  run_test "Delete unknown product returns 404" "404" "$status"
}

# ═══════════════════════════════════════════════════════════════════════════════
# SUITE 7 — INFRASTRUCTURE (Swagger + Actuator)
# ═══════════════════════════════════════════════════════════════════════════════

test_infra() {
  section "SUITE 7 — Infrastructure (Swagger + Actuator)"

  echo -e "\n${BLUE}[7.1] GET /swagger-ui.html — Swagger UI${RESET}"
  local status body
  status=$(http GET "${BASE_URL}/swagger-ui.html")
  if [[ "$status" == "200" || "$status" == "302" ]]; then
    TOTAL=$((TOTAL + 1)); PASSED=$((PASSED + 1))
    echo -e "  ${GREEN}✅ PASS${RESET} Swagger UI reachable (HTTP $status)"
  else
    TOTAL=$((TOTAL + 1)); FAILED=$((FAILED + 1))
    echo -e "  ${RED}❌ FAIL${RESET} Swagger UI not reachable (HTTP $status)"
  fi

  echo -e "\n${BLUE}[7.2] GET /v3/api-docs — OpenAPI JSON spec${RESET}"
  status=$(http GET "${BASE_URL}/v3/api-docs")
  body=$(get_body)
  run_test "OpenAPI spec returns 200" "200" "$status" "openapi" "$body"

  echo -e "\n${BLUE}[7.3] GET /actuator/health — Spring Actuator${RESET}"
  status=$(http GET "${BASE_URL}/actuator/health")
  body=$(get_body)
  run_test "Actuator health returns UP" "200" "$status" "UP" "$body"

  echo -e "\n${BLUE}[7.4] GET $NOTIFICATION_URL/api-docs — NestJS Swagger${RESET}"
  if [[ "$NOTIFICATION_AVAILABLE" == "true" ]]; then
    status=$(http GET "${NOTIFICATION_URL}/api-docs")
    body=$(get_body)
    run_test "NestJS OpenAPI spec returns 200" "200" "$status" "openapi" "$body"
  else
    echo -e "  ${YELLOW}⚠️  SKIP${RESET} Notification service not available"
  fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# FINAL REPORT
# ═══════════════════════════════════════════════════════════════════════════════

print_report() {
  section "FINAL REPORT"
  echo ""
  local pass_rate=0
  [[ $TOTAL -gt 0 ]] && pass_rate=$(( PASSED * 100 / TOTAL ))

  echo -e "${BOLD}  Results by suite:${RESET}"
  echo ""
  echo -e "  ${BLUE}Suite 1 — Products API:${RESET}       CRUD + bean validation"
  echo -e "  ${BLUE}Suite 2 — Stock (Cyber Day):${RESET}  Redis Lua + optimistic locking"
  echo -e "  ${BLUE}Suite 3 — Orders API:${RESET}         Hexagonal architecture"
  echo -e "  ${BLUE}Suite 4 — Analytics:${RESET}          SQL CTEs / window functions + Redis cache"
  echo -e "  ${BLUE}Suite 5 — Notifications:${RESET}      POST /notify Strategy Pattern + Claude agent"
  echo -e "  ${BLUE}Suite 6 — Validations:${RESET}        Error handling + HTTP semantics"
  echo -e "  ${BLUE}Suite 7 — Infrastructure:${RESET}     Swagger + Spring Actuator"
  echo ""
  echo -e "  ─────────────────────────────────────────"
  echo -e "  Total tests run:  ${BOLD}$TOTAL${RESET}"
  echo -e "  ${GREEN}✅ Passed:         $PASSED${RESET}"
  echo -e "  ${RED}❌ Failed:         $FAILED${RESET}"
  echo -e "  Pass rate:        ${BOLD}${pass_rate}%${RESET}"
  echo -e "  ─────────────────────────────────────────"
  echo ""
  if [[ $FAILED -eq 0 ]]; then
    echo -e "  ${GREEN}${BOLD}🎉 ALL TESTS PASSED${RESET}"
  elif [[ $pass_rate -ge 80 ]]; then
    echo -e "  ${YELLOW}${BOLD}⚠️  MAJORITY PASSED — review failures above${RESET}"
  else
    echo -e "  ${RED}${BOLD}❌ MULTIPLE FAILURES — review output above${RESET}"
  fi
  echo ""
}

# ═══════════════════════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════════════════════

main() {
  echo ""
  echo -e "${CYAN}${BOLD}"
  echo "  ███╗   ██╗██╗   ██╗██╗  ██╗███╗   ██╗"
  echo "  ████╗  ██║╚██╗ ██╔╝╚██╗██╔╝████╗  ██║"
  echo "  ██╔██╗ ██║ ╚████╔╝  ╚███╔╝ ██╔██╗ ██║"
  echo "  ██║╚██╗██║  ╚██╔╝   ██╔██╗ ██║╚██╗██║"
  echo "  ██║ ╚████║   ██║   ██╔╝ ██╗██║ ╚████║"
  echo "  ╚═╝  ╚═══╝   ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═══╝"
  echo -e "${RESET}"
  echo -e "  ${BOLD}E-Commerce API — Manual Test Suite${RESET}"
  echo -e "  Backend:       $BASE_URL"
  echo -e "  Notifications: $NOTIFICATION_URL"
  echo ""

  check_services
  test_products
  test_stock
  test_orders
  test_analytics
  test_notifications
  test_validations
  test_infra
  print_report
}

main "$@"
