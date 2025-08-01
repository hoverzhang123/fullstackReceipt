output "efs_file_system_id" {
  description = "The ID of the created EFS file system"
  value       = aws_efs_file_system.jenkins.id
}

output "efs_access_point_id" {
  description = "Access Point ID for Jenkins"
  value       = aws_efs_access_point.jenkins_ap["jenkins"].id
}
output "jenkins_kms_key_arn" {
  description = "The ARN of the KMS key used for Jenkins EFS"
  value       = aws_kms_key.jenkins_efs.arn
}