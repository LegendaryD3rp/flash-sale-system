#!/usr/bin/env bash
# Newman 全流程测试 + 并发测试
# 使用方式: bash newman_runner.sh

set -e

COLLECTION="postman_collection.json"
DATA_FILE="newman_concurrent.csv"
REPORT_DIR="newman_reports"
PASS=0
FAIL=0
PASS_LIST=""
FAIL_LIST=""

mkdir -p "$REPORT_DIR"

echo "========================================"
echo "  闪购秒杀系统 · Newman 全自动化测试"
echo "========================================"
echo ""

# ── 检查服务是否运行 ──
echo "→ 检查服务状态..."
if ! curl -sf http://localhost:8080/api/product?page=1\&size=1 > /dev/null 2>&1; then
  echo "✗ Gateway 未就绪，请先启动所有服务"
  exit 1
fi
echo "✓ Gateway 就绪"
echo ""

# ── 1. 全流程E2E测试 ──
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  【1/2】全流程E2E测试（21个请求）"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if newman run "$COLLECTION" \
  --reporters cli,junit \
  --reporter-junit-export "$REPORT_DIR/e2e_results.xml" \
  --timeout-request 10000 \
  --delay-request 200 \
  --bail; then
  echo ""
  echo "✓ 全流程E2E测试通过"
  PASS_LIST="$PASS_LIST E2E"
  PASS=$((PASS + 1))
else
  echo ""
  echo "✗ 全流程E2E测试部分失败，请检查 $REPORT_DIR/e2e_results.xml"
  FAIL_LIST="$FAIL_LIST E2E"
  FAIL=$((FAIL + 1))
fi
echo ""

# ── 2. Newman 并发秒杀测试 ──
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  【2/2】并发秒杀测试（10用户 × 3轮）"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if newman run "$COLLECTION" \
  --folder "6-秒杀" \
  --iteration-count 10 \
  --data "$DATA_FILE" \
  --reporters cli,junit \
  --reporter-junit-export "$REPORT_DIR/concurrent_results.xml" \
  --timeout-request 15000; then
  echo ""
  echo "✓ 并发秒杀测试通过"
  PASS_LIST="$PASS_LIST 并发"
  PASS=$((PASS + 1))
else
  echo ""
  echo "✗ 并发秒杀测试部分失败，请检查 $REPORT_DIR/concurrent_results.xml"
  FAIL_LIST="$FAIL_LIST 并发"
  FAIL=$((FAIL + 1))
fi
echo ""

# ── 结果汇总 ──
echo "========================================"
echo "  测试结果汇总"
echo "========================================"
[ -n "$PASS_LIST" ] && echo "✓ 通过: $PASS_LIST"
[ -n "$FAIL_LIST" ] && echo "✗ 失败: $FAIL_LIST"
echo "  总计: ${PASS}组通过, ${FAIL}组失败"
echo ""
echo "详细报告: $REPORT_DIR/"
echo "========================================"
