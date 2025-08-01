# AWS Secrets Manager for Jenkins admin credentials
terraform {
  backend "s3" {
    bucket      = "shd-use1-apps-terraform"
    key         = "plat/jenkins-eks/terraform.tfstate"
    region      = "us-east-1"
    acl         = "bucket-owner-full-control"
  }
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
    sops = {
      source  = "carlpett/sops"
      version = "~> 0.6"
    }
  }
}

provider "aws" {
  region  = var.region
  profile = var.profile != "" ? var.profile : null
  assume_role {
    role_arn = var.profile != "" ? null : var.provider_iam_role[var.environment]
  }
  default_tags {
    tags = {
      "chewy:cost_center"         = var.cost_center
      "chewy:data_classification" = var.data_classification
      "chewy:environment"         = var.environment
      "chewy:app_name"            = var.app_name
      "chewy:owner_email"         = var.owner_email
      "chewy:created_by"          = "terraform"
      "chewy:tf-project"          = "https://github.com/Chewy-Inc/jenkins-eks.git"
    }
  }
}

provider "kubernetes" {
  config_path = "~/.kube/config"
}