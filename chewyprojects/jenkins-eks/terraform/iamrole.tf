resource "aws_iam_role" "jenkins-eks-ecr-pull-role" {
  name = "jenkins-eks-ecr-pull-role"
  assume_role_policy = data.aws_iam_policy_document.jenkins-ecr-pull-policy_document.json
}
data "aws_iam_policy_document" "jenkins-ecr-pull-policy_document" {
    statement {
      sid = "ServiceAccountRoleAssumption"
      effect = "Allow"
      actions = ["sts:AssumeRoleWithWebIdentity"]
      principals {
        type = "Federated"
        identifiers = ["arn:aws:iam::590183723998:oidc-provider/oidc.eks.us-east-1.amazonaws.com/id/A7B0CB4271DD847CF13B478233101956"]
      }
      condition {
        test = "StringEquals"
        variable = "oidc.eks.us-east-1.amazonaws.com/id/A7B0CB4271DD847CF13B478233101956:sub"
        values = ["system:serviceaccount:jenkins:jenkins-ecr-pull-role"]
      }
    }
}
resource "aws_iam_policy" "jenkins-ecr-pull-policy" {
    name = "jenkins-ecr-pull-policy"
    policy = jsonencode({
      Version = "2012-10-17"
      Statement = [
        {
          Sid    = "AllowECRGetAuthToken"
          Effect = "Allow"
          Action = [
            "ecr:GetAuthorizationToken"
          ]
          Resource = "*"
        },
        {
          Sid    = "AllowECRPull"
          Effect = "Allow"
          Action = [
            "ecr:BatchCheckLayerAvailability",
            "ecr:GetDownloadUrlForLayer",
            "ecr:BatchGetImage"
          ]
          Resource = [
            "arn:aws:ecr:us-east-1:278833423079:repository/*"
          ]
        }
      ]
    })
}
resource "aws_iam_role_policy_attachment" "jenkins-ecr-pull-policy-attachment" {
    role = aws_iam_role.jenkins-eks-ecr-pull-role.name
    policy_arn = aws_iam_policy.jenkins-ecr-pull-policy.arn
}