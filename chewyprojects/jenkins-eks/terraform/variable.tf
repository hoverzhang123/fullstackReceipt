variable "provider_iam_role" {
  description = "Amazon Resource Name (ARN) of the IAM Role to assume."
  default     = {
    dev = "arn:aws:iam::590183723998:role/CHEWY-cross-jenkins"
  }
}

variable "account_number" {
  description = "The AWS account number."
  default     = {
    dev = "590183723998"
  }
} 
variable "lookup_key" {
  description = "The lookup key of the resource."
  type        = string
  default     = "dev"
}
variable "environment" {
  description = "The Chewy environment this resource will be deployed in, e.g. sbx | dev | qat | stg | prd . "
}

variable "app_name" {
  description = "The name of the application that this resource belongs to."
  default     = "jenkins-eks"
}

variable "cost_center" {
  description = "The Chewy cost center that this resource belongs to, e.g. csbb | demm | hlth | scff | opta | mobi | corp | plat "
  default     = "plat"
}

variable "owner_email" {
  description = "A distribution list that contains the owners of this resource. Do not specifiy an individual's email address."
  default     = "releng@chewy.com"
}

variable "data_classification" {
  description = "The level of data sensitity of data on or through this resource."
  default     = "not_applicable"
}

variable "region" {
  description = "The AWS region."
}
variable "segmented_account_name" {
  description = "The name of the account that this resource belongs to."
  default     = "management"
}

variable "profile" {
  default = ""
}

variable "vpc_usage" {
  description = "The VPC usage of the resource."
  default     = "tools"
}
variable "vpc_id" {
  description = "The VPC ID of the resource."
  type        = string
}

variable "eks_node_security_group_ids" {
  description = "The EKS node security group IDs of the resource."
  type        = list(string)
}
variable "private_subnet_ids" {
  description = "The private subnet IDs of the resource."
  type        = list(string)
}
variable "kms_key_id" {
  description = "The KMS key ID of the resource."
  type        = string
}
variable "encrypted" {
  description = "Whether to encrypt the resource."
  type        = bool
}
variable "access_points" {
  description = "The access points of the resource."
  type        = map(any)
}
variable "cluster_oidc_provider_arn" {
  type        = string
  default     = ""
  description = "OIDC provider for the EKS cluster"
}

variable "cluster_oidc_provider_url" {
  type        = string
  default     = ""
  description = "OIDC URL suffix used in trust policy"
}

variable "aws_account_id" {
  type        = string
  default     = ""
  description = "AWS account ID"
}
variable "kms_key_name" {
  type        = string
  default     = ""
  description = "KMS key name"
}
variable "kms_key_alias" {
  type        = string
  default     = ""
  description = "KMS key alias"
}