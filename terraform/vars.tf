variable "aws_region" {
  type    = string
  default = "eu-west-1"
}

variable "function_name" {
  type    = string
  default = "t3"
}

# TfL API key is stored in SSM Parameter Store at /berrylands/tfl-api-key
# Darwin API key is stored in SSM Parameter Store at /berrylands/darwin-api-key
