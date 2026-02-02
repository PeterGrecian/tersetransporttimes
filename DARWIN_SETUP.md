# National Rail Darwin API Setup

## Step 1: Register for Darwin API Access

1. Go to https://realtime.nationalrail.co.uk/OpenLDBWSRegistration/
2. Fill out the registration form
3. You'll receive an API key via email (usually instant)
4. Save your API key - you'll need it for deployment

**Note**: The API is free for up to 5 million requests per 4-week period.

## Step 2: Configure API Key

Add the Darwin API key to Terraform variables:

```bash
cd /home/tot/tersetransporttimes/terraform
```

Edit `terraform.tfvars` (create if it doesn't exist):
```hcl
darwin_api_key = "YOUR_API_KEY_HERE"
```

Or set as environment variable:
```bash
export TF_VAR_darwin_api_key="YOUR_API_KEY_HERE"
```

## Step 3: Update Terraform Configuration

Add the Darwin API key variable to `terraform/vars.tf`:

```hcl
variable "darwin_api_key" {
  type        = string
  description = "National Rail Darwin API key"
  default     = ""
  sensitive   = true
}
```

Update the trains Lambda function in `terraform/main.tf` to:
1. Use `trains_darwin.py` instead of `trains.py`
2. Add `DARWIN_API_KEY` environment variable

## Step 4: Test Locally

Before deploying, test the new Darwin implementation:

```bash
cd /home/tot/tersetransporttimes

# Set your API key
export DARWIN_API_KEY="your_api_key_here"

# Test the script
python3 trains_darwin.py sur wat
```

You should see JSON output with train departures.

## Step 5: Deploy

Once local testing works:

```bash
cd terraform

# Replace trains.py with trains_darwin.py
mv ../trains.py ../trains_huxley.py.backup
cp ../trains_darwin.py ../trains.py

# Deploy
terraform apply
```

## Troubleshooting

### "No Darwin API key provided"
- Make sure `DARWIN_API_KEY` environment variable is set in Lambda
- Check Terraform configuration has the environment variable

### SOAP/XML Errors
- Verify your API key is correct
- Check the Darwin API status: https://www.nationalrail.co.uk/service_disruptions/
- Use `./check-logs.sh t3-trains 5m` to see detailed errors

### "Service not available"
- Darwin API may be temporarily down
- Check OpenRailData status page

## API Limits

- **Free tier**: 5 million requests per 4-week period
- **Current usage**: ~100 requests/day with 30-second refresh = ~3000/month
- Well within free tier limits

## Alternative: Use Existing trains_darwin.py

Instead of replacing trains.py, you can:
1. Deploy trains_darwin.py as a separate Lambda function
2. Update API Gateway route to use it
3. Keep trains.py as backup

## Branding Requirements

**IMPORTANT**: If this app is ever published, you must acknowledge National Rail and use their official branding.

- Brand guidelines and logos: http://www.nationalrail.co.uk/static/documents/Brand_Guidelines_Logos.zip
- Must acknowledge National Rail as the data source
- Follow brand guidelines for logo usage and attribution

This is a requirement from the Darwin API registration process.

## Documentation

- [Darwin API Registration](https://realtime.nationalrail.co.uk/OpenLDBWSRegistration/)
- [National Rail Developers](https://www.nationalrail.co.uk/developers/)
- [Darwin Data Feeds](https://www.nationalrail.co.uk/developers/darwin-data-feeds/)
- [OpenRailData Wiki](https://wiki.openraildata.com/index.php/Darwin:Darwin_Web_Service_(Public))
