resource "aws_iam_role" "efs_csi_driver" {
  name = "AmazonEKS_EFS_CSI_DriverRole"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Federated = var.cluster_oidc_provider_arn
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "${var.cluster_oidc_provider_url}:sub" = "system:serviceaccount:jenkins:efs-csi-controller-sa"
          }
        }
      }
    ]
  })

  tags = {
    Name        = "AmazonEKS_EFS_CSI_DriverRole"
    Environment = var.environment
  }
}
resource "aws_iam_policy" "EKS_EFS_CSI_Driver_Policy"{
    name = "EKS_EFS_CSI_Driver_Policy"
    policy = file("EKS_EFS_CSI_Driver_Policy.json")
}
resource "aws_iam_policy_attachment" "efs_csi_policy_attach" {
  name       = "efs-csi-driver-policy-attach"
  policy_arn = aws_iam_policy.EKS_EFS_CSI_Driver_Policy.arn
  roles      = [aws_iam_role.efs_csi_driver.name]
}
