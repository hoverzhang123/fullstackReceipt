module "core_info" {
  source              = "git@github.com:Chewy-Inc/core-info-aws-tf-module.git//terraform?ref=0.5.125"
  app_name            = var.app_name
  environment         = var.environment
  region              = var.region
  cost_center         = var.cost_center
  owner_email         = var.owner_email
  data_classification = var.data_classification
  lookup_key          = var.lookup_key
  segmented_account_name = var.segmented_account_name
  vpc_usage           = var.vpc_usage
}
# EFS File System
resource "aws_efs_file_system" "jenkins" {
  creation_token   = "jenkins-efs"
  encrypted        = var.encrypted
  kms_key_id       = aws_kms_key.jenkins_efs.arn
  performance_mode = "generalPurpose"
  throughput_mode  = "elastic"

  tags = {
    Name        = "jenkins-efs"
    Environment = var.environment
  }
}

# Security Group for EFS
resource "aws_security_group" "efs_sg" {
  name        = "efs-sg"
  description = "Allow NFS from EKS nodes"
  vpc_id      = module.core_info.vpc_id

  ingress {
    from_port       = 2049
    to_port         = 2049
    protocol        = "tcp"
    security_groups = var.eks_node_security_group_ids
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "efs-sg"
  }
}

# Mount Targets in each subnet
resource "aws_efs_mount_target" "mt" {
  count = length(module.core_info.private_subnets)
  file_system_id  = aws_efs_file_system.jenkins.id
  subnet_id       = module.core_info.private_subnets[count.index]
  security_groups = [aws_security_group.efs_sg.id]
}

# # (Optional) Access Point for Jenkins
resource "aws_efs_access_point" "jenkins_ap" {
  for_each = var.access_points

  file_system_id = aws_efs_file_system.jenkins.id

  posix_user {
    gid = var.access_points[each.key]["posix_user"]["gid"]
    uid = var.access_points[each.key]["posix_user"]["uid"]
  }

  root_directory {
    path = "/${each.key}"
    creation_info {
      owner_gid   = var.access_points[each.key]["creation_info"]["gid"]
      owner_uid   = var.access_points[each.key]["creation_info"]["uid"]
      permissions = var.access_points[each.key]["creation_info"]["permissions"]
    }
  }
  tags = {
    Name = "jenkins-access-point"
  }
}

# KMS key for encrypting Jenkins EFS file system
resource "aws_kms_key" "jenkins_efs" {
  description             = "KMS key for encrypting Jenkins EFS file system"
  enable_key_rotation     = true
  deletion_window_in_days = 10

  policy = jsonencode({
    Version = "2012-10-17"
    Id      = "jenkins-efs-kms-policy"
    Statement = [
      {
        Sid    = "AllowAdminForKeyOwner",
        Effect = "Allow",
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        },
        Action = "kms:*",
        Resource = "*"
      },
      {
        Effect = "Allow"
        Principal = {
          AWS = [
            "arn:aws:iam::590183723998:role/CHEWY-cross-jenkins",
            "arn:aws:iam::590183723998:role/CHEWY-cross-jenkins-inf"
          ]
        }
        Action = [
          "kms:Encrypt",
          "kms:Decrypt",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:DescribeKey"
        ]
        Resource = "*"
      },

      # Allow EFS service to use the key
      {
        Effect = "Allow"
        Principal = {
          Service = "elasticfilesystem.amazonaws.com"
        }
        Action = [
          "kms:Encrypt",
          "kms:Decrypt",
          "kms:GenerateDataKey*",
          "kms:DescribeKey"
        ]
        Resource = "*"
      }
    ]
  })

  tags = {
    Name = "jenkins-efs-kms"
  }
}
data "aws_caller_identity" "current" {}




# resource "aws_efs_access_point" "jenkins_ap" {
#   file_system_id = aws_efs_file_system.jenkins.id

#   root_directory {
#     path = "/jenkins"
#     creation_info {
#       owner_uid   = 1000
#       owner_gid   = 1000
#       permissions = "0775"
#     }
#   }

#   posix_user {
#     uid = 1000
#     gid = 1000
#   }

#   tags = {
#     Name = "jenkins-access-point"
#   }
# }