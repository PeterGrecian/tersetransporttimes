#!/bin/bash
# check-logs.sh - Examine Lambda function logs in CloudWatch

set -e

FUNCTION_NAME="${1:-t3-trains}"
TIME_RANGE="${2:-30m}"

echo "=== Checking logs for Lambda: $FUNCTION_NAME ==="
echo "Time range: $TIME_RANGE"
echo ""

LOG_GROUP="/aws/lambda/$FUNCTION_NAME"

# Check if log group exists
if ! aws logs describe-log-groups --log-group-name-prefix "$LOG_GROUP" --query 'logGroups[0].logGroupName' --output text 2>/dev/null | grep -q "$LOG_GROUP"; then
    echo "Error: Log group $LOG_GROUP not found"
    echo "Available Lambda functions:"
    aws lambda list-functions --query 'Functions[].FunctionName' --output table
    exit 1
fi

echo "--- Recent invocations ---"
aws logs tail "$LOG_GROUP" --since "$TIME_RANGE" --format short 2>/dev/null || echo "No recent logs"

echo ""
echo "--- Errors and exceptions ---"
aws logs filter-log-events \
    --log-group-name "$LOG_GROUP" \
    --start-time $(($(date +%s) - 1800))000 \
    --filter-pattern "?ERROR ?Exception ?error" \
    --query 'events[].message' \
    --output text 2>/dev/null || echo "No errors found"

echo ""
echo "--- Application output (print statements) ---"
aws logs filter-log-events \
    --log-group-name "$LOG_GROUP" \
    --start-time $(($(date +%s) - 1800))000 \
    --filter-pattern "Fetching Error Got" \
    --query 'events[].message' \
    --output text 2>/dev/null || echo "No application logs"

echo ""
echo "--- Request metrics (last 10 invocations) ---"
aws logs filter-log-events \
    --log-group-name "$LOG_GROUP" \
    --start-time $(($(date +%s) - 1800))000 \
    --filter-pattern "REPORT" \
    --max-items 10 \
    --query 'events[].message' \
    --output text 2>/dev/null | sed 's/\t/ | /g'

echo ""
echo "=== Usage ==="
echo "./check-logs.sh [function-name] [time-range]"
echo "Examples:"
echo "  ./check-logs.sh t3-trains 1h    # Check trains lambda, last hour"
echo "  ./check-logs.sh t3 30m          # Check bus lambda, last 30 minutes"
