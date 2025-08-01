environment = "dev"
region = "us-east-1"
encrypted = true
kms_key_id = "arn:aws:kms:us-east-1:590183723998:key/mrk-711dd37e962e4665a788c6d9c6c179bc"
vpc_id =  "vpc-0b0b1d9e964237860"
eks_node_security_group_ids = [
    "sg-0af25bde31103ef69",
    "sg-0ab855684fcd892f5"
]
private_subnet_ids = [
    "subnet-03a3a722dc545c84a",
    "subnet-0d66f173e9660e483",
    "subnet-0da4ec8b54b60d77f"
]
performance_mode = "generalPurpose"
throughput_mode  = "elastic" 
access_points = {
  "jenkins" = {
    "posix_user" = {
      "uid" = "1001"
      "gid" = "1001"
    }
    "creation_info" = {
      "uid"         = "1001"
      "gid"         = "1001"
      "permissions" = "0755"
    }
  }
}
cluster_oidc_provider_arn = "arn:aws:iam::590183723998:oidc-provider/oidc.eks.us-east-1.amazonaws.com/id/A7B0CB4271DD847CF13B478233101956"
cluster_oidc_provider_url = "oidc.eks.us-east-1.amazonaws.com/id/A7B0CB4271DD847CF13B478233101956"
aws_account_id = "590183723998"
kms_key_name = "jenkins-eks"