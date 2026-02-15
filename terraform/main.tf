provider "aws" {
  region = var.aws_region
}

# Darwin API key now fetched from Parameter Store by Lambda code
# No Terraform data source needed - Lambda uses boto3 ssm client

# IAM role for Lambda
resource "aws_iam_role" "lambda_role" {
  name = "t3_lambda_role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

# Basic Lambda execution policy
resource "aws_iam_role_policy_attachment" "lambda_basic" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# IAM policy to allow Lambda to read from Parameter Store (FREE!)
resource "aws_iam_role_policy" "lambda_parameter_store" {
  name = "lambda_parameter_store_access"
  role = aws_iam_role.lambda_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters"
        ]
        Resource = "arn:aws:ssm:${var.aws_region}:*:parameter/berrylands/*"
      },
      {
        Effect = "Allow"
        Action = "kms:Decrypt"
        Resource = "*"
        Condition = {
          StringEquals = {
            "kms:ViaService" = "ssm.${var.aws_region}.amazonaws.com"
          }
        }
      }
    ]
  })
}

# Lambda function
resource "aws_lambda_function" "t3" {
  filename         = data.archive_file.lambda_zip.output_path
  function_name    = var.function_name
  role             = aws_iam_role.lambda_role.arn
  handler          = "t3.lambda_handler"
  source_code_hash = data.archive_file.lambda_zip.output_base64sha256
  runtime          = "python3.12"
  timeout          = 10
  memory_size      = 128

  environment {
    variables = {
      TFL_API_KEY = var.tfl_api_key
    }
  }
}

# Zip the Lambda code
data "archive_file" "lambda_zip" {
  type        = "zip"
  source_file = "${path.module}/../t3.py"
  output_path = "${path.module}/t3.zip"
}

# API Gateway
resource "aws_apigatewayv2_api" "t3_api" {
  name          = "t3-api"
  protocol_type = "HTTP"
}

# API Gateway stage
resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.t3_api.id
  name        = "$default"
  auto_deploy = true
}

# Lambda integration
resource "aws_apigatewayv2_integration" "lambda" {
  api_id             = aws_apigatewayv2_api.t3_api.id
  integration_type   = "AWS_PROXY"
  integration_uri    = aws_lambda_function.t3.invoke_arn
  integration_method = "POST"
}

# Route for root path
resource "aws_apigatewayv2_route" "root" {
  api_id    = aws_apigatewayv2_api.t3_api.id
  route_key = "GET /"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

# Route for any path
resource "aws_apigatewayv2_route" "any" {
  api_id    = aws_apigatewayv2_api.t3_api.id
  route_key = "GET /{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

# Lambda permission for API Gateway
resource "aws_lambda_permission" "api_gw" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.t3.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.t3_api.execution_arn}/*/*"
}

# Zip the trains Lambda code
data "archive_file" "trains_lambda_zip" {
  type        = "zip"
  source_file = "${path.module}/../trains.py"
  output_path = "${path.module}/trains.zip"
}

# Trains Lambda function
resource "aws_lambda_function" "trains" {
  filename         = data.archive_file.trains_lambda_zip.output_path
  function_name    = "${var.function_name}-trains"
  role             = aws_iam_role.lambda_role.arn
  handler          = "trains.lambda_handler"
  source_code_hash = data.archive_file.trains_lambda_zip.output_base64sha256
  runtime          = "python3.12"
  timeout          = 10
  memory_size      = 128

  # No environment variables needed - Lambda fetches Darwin API key from Parameter Store
}

# Lambda integration for trains
resource "aws_apigatewayv2_integration" "trains_lambda" {
  api_id             = aws_apigatewayv2_api.t3_api.id
  integration_type   = "AWS_PROXY"
  integration_uri    = aws_lambda_function.trains.invoke_arn
  integration_method = "POST"
}

# Route for trains endpoint
resource "aws_apigatewayv2_route" "trains" {
  api_id    = aws_apigatewayv2_api.t3_api.id
  route_key = "GET /trains"
  target    = "integrations/${aws_apigatewayv2_integration.trains_lambda.id}"
}

# Lambda permission for trains API Gateway
resource "aws_lambda_permission" "trains_api_gw" {
  statement_id  = "AllowAPIGatewayInvokeTrains"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.trains.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.t3_api.execution_arn}/*/*"
}

# Outputs
output "api_endpoint" {
  description = "API Gateway endpoint URL"
  value       = aws_apigatewayv2_stage.default.invoke_url
}

output "function_name" {
  description = "Lambda function name"
  value       = aws_lambda_function.t3.function_name
}

output "trains_function_name" {
  description = "Trains Lambda function name"
  value       = aws_lambda_function.trains.function_name
}
