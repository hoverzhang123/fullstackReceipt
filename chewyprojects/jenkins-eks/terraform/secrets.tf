resource "null_resource" "secret_watcher" {
  triggers = {
    file_hash = filesha256("${path.module}/secret.enc.json")
  }

  provisioner "local-exec" {
    command = "echo 'Secret changed. Running update...'"
  }
}
data "sops_file" "jenkins_creds" {
  source_file = "${path.module}/secret.enc.json"
}
module "key_value_secret" {
  source             = "git@github.com:Chewy-Inc/app-secret-aws-tf-module.git//terraform/modules/structured?ref=2.0.1"
  name               = "admin-credentials"
  core_info          = module.core_info
  bypass_replication = true
  secret_data        = data.sops_file.jenkins_creds.data
  kms_key_alias = module.kms_primary.kms_key_alias
}
module "kms_primary" {
  source    = "git@github.com:Chewy-Inc/kms-core-aws-tf-module.git//terraform?ref=v2.1.0"
  core_info = module.core_info
  key_name = var.kms_key_name
  multi_region = true
  allowed_service_names = ["secretsmanager"]
  allowed_principals = ["arn:aws:iam::590183723998:role/eksctl-global-dev-use1-mgmt-addon-iamservicea-Role1-irBYNed6DEgU"]

  providers = {
    aws = aws
  }
}

