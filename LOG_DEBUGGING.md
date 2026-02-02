# Log Debugging Guide

## Quick Start

Check Lambda function logs:
```bash
./check-logs.sh t3-trains 30m    # Check trains Lambda, last 30 minutes
./check-logs.sh t3 1h             # Check bus Lambda, last hour
```

## Log Script Usage

The `check-logs.sh` script provides easy access to CloudWatch logs for Lambda functions.

### Syntax
```bash
./check-logs.sh [function-name] [time-range]
```

### Parameters
- **function-name**: Lambda function name (default: `t3-trains`)
  - `t3` - Bus times Lambda
  - `t3-trains` - Train times Lambda
- **time-range**: How far back to search (default: `30m`)
  - `5m`, `10m`, `30m` - Minutes
  - `1h`, `2h`, `12h` - Hours
  - `1d`, `7d` - Days

### What It Shows

1. **Recent invocations** - Last function executions with timing
2. **Errors and exceptions** - Any ERROR/Exception patterns
3. **Application output** - Print statements from your code
4. **Request metrics** - Duration, memory usage, cold starts

### Examples

```bash
# Check for recent errors in trains Lambda
./check-logs.sh t3-trains 10m

# See last hour of bus Lambda activity
./check-logs.sh t3 1h

# Quick check of most recent invocation
./check-logs.sh t3-trains 5m
```

## Manual CloudWatch Commands

### View recent logs
```bash
aws logs tail /aws/lambda/t3-trains --since 30m --format short
```

### Search for errors
```bash
aws logs filter-log-events \
  --log-group-name /aws/lambda/t3-trains \
  --start-time $(($(date +%s) - 3600))000 \
  --filter-pattern "?ERROR ?Exception ?error"
```

### Get metrics
```bash
aws logs filter-log-events \
  --log-group-name /aws/lambda/t3-trains \
  --filter-pattern "REPORT" \
  --max-items 10
```

### List all Lambda functions
```bash
aws lambda list-functions --query 'Functions[].FunctionName' --output table
```

## Common Issues

### Issue: "Log group not found"
**Solution**: Check function name is correct
```bash
aws lambda list-functions --query 'Functions[].FunctionName'
```

### Issue: No recent logs
**Possible causes**:
- Function hasn't been invoked recently
- Time range too narrow
- Function failing before logging

**Debug**:
```bash
# Test the endpoint directly
curl "https://vz66vhhtb9.execute-api.eu-west-1.amazonaws.com/trains?from=sur&to=wat"

# Check logs immediately after
./check-logs.sh t3-trains 1m
```

### Issue: Errors not showing in filtered output
**Solution**: Check raw logs
```bash
aws logs tail /aws/lambda/t3-trains --since 30m
```

## Adding Logging to Code

### Python (trains.py, t3.py)
```python
# Print statements appear in CloudWatch
print(f"Fetching from: {url}")
print(f"Error: {e}")

# Timestamps are added automatically by CloudWatch
```

### Enable detailed logging
In `trains.py` or `t3.py`, add debug prints:
```python
import json
print(f"Request params: {json.dumps(event)}")
print(f"Response: {json.dumps(result)[:500]}")  # First 500 chars
```

## Viewing Logs in AWS Console

1. Go to AWS Console → CloudWatch → Log groups
2. Find `/aws/lambda/t3-trains` or `/aws/lambda/t3`
3. Click on a log stream (most recent at top)
4. Use the filter box to search for patterns

## Log Retention

- Default: Logs kept indefinitely (costs money!)
- Recommendation: Set retention to 7-30 days
```bash
aws logs put-retention-policy \
  --log-group-name /aws/lambda/t3-trains \
  --retention-in-days 7
```

## Monitoring Best Practices

1. **Add strategic print statements** at key points:
   - API calls: URL being fetched
   - Data processing: Number of records
   - Errors: Full exception details

2. **Use structured logging** for easier filtering:
   ```python
   print(f"[FETCH] URL={url} status=starting")
   print(f"[ERROR] operation=fetch error={str(e)}")
   ```

3. **Check logs after deployment**:
   ```bash
   # Deploy
   cd terraform && terraform apply -auto-approve

   # Trigger the endpoint
   curl "https://vz66vhhtb9.execute-api.eu-west-1.amazonaws.com/trains"

   # Check logs
   ./check-logs.sh t3-trains 1m
   ```

## Current Known Issues

### Huxley2 API Errors
**Symptom**: `Error fetching departures: HTTPError: HTTP Error 500`

**Cause**: Huxley2 free service is down/unreliable

**Solution**: See TRAIN_API_OPTIONS.md for alternative APIs
