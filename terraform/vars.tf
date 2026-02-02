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

variable "darwin_api_key" {
  type        = string
  description = "National Rail Darwin API key (get from https://realtime.nationalrail.co.uk/OpenLDBWSRegistration/)"
  default     = ""
  sensitive   = true
}
