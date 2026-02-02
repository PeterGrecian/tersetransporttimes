# UK Train API Alternatives

## Current Status
**Huxley2** (`https://huxley2.azurewebsites.net/`) - Currently returning HTTP 500 errors (unreliable free service)

## Alternative APIs

### 1. RealTimeTrains API (Recommended) ⭐
- **URL**: https://api.rtt.io/
- **Cost**: Free for non-commercial use
- **Registration**: Required - sign up at https://api.rtt.io/
- **Auth**: Basic Auth (username/password from account dashboard)
- **Docs**: https://www.realtimetrains.co.uk/about/developer/pull/docs/
- **Reliability**: ⭐⭐⭐⭐⭐ (maintained by swlines Ltd)

**Example endpoint**:
```
GET https://api.rtt.io/api/v1/json/search/SUR/to/WAT
Authorization: Basic <base64(username:password)>
```

**Setup**:
1. Sign up at https://api.rtt.io/
2. Get API credentials from dashboard (NOT your login password)
3. Add credentials to Terraform variables
4. Update trains.py to use RTT API

### 2. National Rail Darwin API
- **URL**: https://www.nationalrail.co.uk/developers/
- **Cost**: Free
- **Registration**: Required via National Rail data portal
- **Format**: XML/SOAP (more complex to use)
- **Reliability**: ⭐⭐⭐⭐⭐ (official industry source)

### 3. TransportAPI
- **URL**: https://www.transportapi.com/
- **Cost**: Paid service with limited free tier
- **Registration**: Required
- **Reliability**: ⭐⭐⭐⭐⭐ (commercial service)

### 4. Self-hosted Huxley
- **URL**: https://huxley.unop.uk/
- **Cost**: Free (self-host)
- **Requirements**: Host your own instance as proxy to Darwin API
- **Reliability**: ⭐⭐⭐⭐ (depends on hosting)

## Recommendations

**Best option**: Switch to **RealTimeTrains API (api.rtt.io)**
- Free for personal use
- Simple JSON API
- Good documentation
- Reliable service
- Similar interface to Huxley2

**Fallback**: National Rail Darwin API (if you need official data)

## Implementation Plan

1. Register for RTT API account at https://api.rtt.io/
2. Update `trains.py` to use RTT API instead of Huxley2
3. Store credentials in AWS Secrets Manager or Terraform variables
4. Test and deploy

## Sources
- [National Rail Developers](https://www.nationalrail.co.uk/developers/)
- [RealTimeTrains API](https://api.rtt.io/)
- [RealTimeTrains Documentation](https://www.realtimetrains.co.uk/about/developer/)
- [TransportAPI](https://www.transportapi.com/)
- [Huxley Project](https://huxley.unop.uk/)
