# Deployment Guide

## 🔐 Setup GitHub Secrets (One-time setup)

1. Go to your GitHub repo: https://github.com/PeterGrecian/tersetransporttimes
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret** and add these three secrets:

   | Name | Value | Description |
   |------|-------|-------------|
   | `AWS_ACCESS_KEY_ID` | Your AWS access key | Get from AWS IAM |
   | `AWS_SECRET_ACCESS_KEY` | Your AWS secret key | Get from AWS IAM |
   | `AWS_REGION` | `eu-west-1` | London region |

4. *(Optional)* Add `TFL_API_KEY` if you have one (improves rate limits)

### Getting AWS Credentials

If you don't have AWS credentials:
1. Go to [AWS IAM Console](https://console.aws.amazon.com/iam/)
2. Users → Your user → Security credentials
3. Create access key → CLI
4. Copy the Access Key ID and Secret Access Key

## 🚀 Automatic Deployment

Once secrets are configured, the backend deploys automatically when you:

- Push changes to `main` or any `claude/**` branch
- Modify `t3.py`, `update`, or the workflow file
- Manually trigger via **Actions** tab → **Deploy Backend** → **Run workflow**

## 📱 After Deployment

1. Go to **Actions** tab on GitHub
2. Click the latest workflow run
3. Look at the job summary for your API URL
4. Update Android app with the URL:

   ```kotlin
   // android/app/src/main/java/com/tersetransporttimes/api/ApiConfig.kt
   private const val BASE_URL = "https://YOUR-API-ID.execute-api.eu-west-1.amazonaws.com/"
   ```

## 🧪 Test Your API

```bash
# Test JSON endpoint
curl -H "Accept: application/json" https://YOUR-API-URL/

# Test HTML endpoint
curl https://YOUR-API-URL/

# Test plain text endpoint
curl -H "Accept: text/plain" https://YOUR-API-URL/
```

## 📊 Workflow Features

- ✅ Validates Python syntax before deploying
- ✅ Deploys only when backend files change
- ✅ Automatically finds and displays your API URL
- ✅ Tests the endpoint after deployment
- ✅ Shows summary in GitHub Actions UI

## 🔧 Manual Deployment (Alternative)

If you prefer to deploy manually with AWS CLI configured:

```bash
./update
```

Or with Terraform:

```bash
cd terraform
terraform init
terraform apply
```

## 🐛 Troubleshooting

**Deployment fails with "ResourceNotFoundException":**
- The Lambda function `t3` doesn't exist yet
- Run Terraform first to create all resources:
  ```bash
  cd terraform
  terraform init
  terraform apply
  ```

**Can't find API URL:**
- Check AWS Console → API Gateway
- Or run: `aws apigatewayv2 get-apis --region eu-west-1`

**Secrets not working:**
- Ensure secret names match exactly (case-sensitive)
- Secrets must be in repository secrets, not environment secrets
- Re-run the workflow after adding secrets
