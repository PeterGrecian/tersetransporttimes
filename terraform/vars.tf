variable "aws_region" {
  type    = string
  default = "eu-west-1"
}

variable "function_name" {
  type    = string
  default = "t3"
}

variable "tfl_api_key" {
  type        = string
  description = "TfL API key (optional, for higher rate limits)"
  default     = ""
  sensitive   = true
}

variable "rtt_username" {
  type        = string
  description = "RTT API username"
  default     = ""
  sensitive   = true
}

variable "rtt_password" {
  type        = string
  description = "RTT API password"
  default     = ""
  sensitive   = true
}
