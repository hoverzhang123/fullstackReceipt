# Jenkins on Kubernetes - Helm Chart Overview

This document provides a comprehensive guide for managing the Jenkins Helm chart deployment on Kubernetes, covering key configuration areas and how to modify them.

---

## Table of Contents

* [Admin Credentials](#admin-credentials)
* [Plugin Management](#plugin-management)
* [Additional Existing Secrets](#additional-existing-secrets)
* [Jenkins Configuration as Code (JCasC)](#jenkins-configuration-as-code-jcasc)
* [Ingress Configuration](#ingress-configuration)
* [Agent Configuration](#agent-configuration)
* [Persistent Volume](#persistent-volume)
* [Service Accounts](#service-accounts)
* [External Secrets](#external-secrets)
* [Deployment](#deployment)

---

## Admin Credentials

### Configuration Location

```yaml
controller:
  admin:
    createSecret: false
    existingSecret: "jenkins-admin-secret"
    userKey: username
    passwordKey: password
```

### How to Modify

* **Using External Secrets (Recommended):** Admin credentials are managed through AWS Secrets Manager.
* **Direct Secret Creation:** Set `createSecret: true` and provide username and password directly.
* **Custom Existing Secret:** Change `existingSecret` to point to your own secret.

### Update Process

Refer to the documentation for updating username and password.

---

## Plugin Management

### Configuration Location

```yaml
controller:
  installPlugins:
    - kubernetes:4358.vcfd9c5a_0a_f51
    - workflow-aggregator:608.v67378e9d3db_1
    - git:5.7.0
    - github:1.43.0
    - configuration-as-code:1971.vf9280461ea_89
    - job-dsl:1.93
    - credentials:1415.v831096eb_5534
    - docker-plugin:1274.vc0203fdf2e74
    - docker-workflow:621.va_73f881d9232
  installLatestPlugins: true
  initializeOnce: false
  overwritePlugins: false
  additionalPlugins: []
```

### How to Modify

* **Add New Plugins:** Append to `installPlugins` list with format `plugin-name:version`
* **Additional Plugins:** Use `additionalPlugins` for extra plugins without modifying the core list

### Version Control

* `installLatestPlugins: true` - Downloads latest versions
* `installLatestSpecifiedPlugins: false` - Uses specified versions
* `initializeOnce: true` - Prevents plugin updates on restart (requires persistence)

---

## Additional Existing Secrets

### Configuration Location

```yaml
controller:
  existingSecret: jenkins-admin-secret
  additionalExistingSecrets:
    - name: jenkins-admin-secret
      keyName: github-token
    - name: jenkins-admin-secret
      keyName: github-plugin-webhook
    - name: jenkins-admin-secret
      keyName: jfrog-access-token
    - name: jenkins-admin-secret
      keyName: hliu1-Jfrog-token
```

### How to Modify

* **If Using External Secrets from AWS Secrets Manager:**

  * Apply Terraform changes to integrate Secrets Manager
  * Deploy a Kubernetes `ExternalSecret` resource to inject secrets
* **Add New Secret References:** Append to `additionalExistingSecrets` list
* **Secret Format:** Each entry must include `name` and `keyName`
* **Usage in JCasC:** Reference as `${secret-name-key-name}`

#### Example Addition

```yaml
additionalExistingSecrets:
  - name: my-custom-secret
    keyName: api-token
  - name: my-custom-secret
    keyName: webhook-secret
```

---

## Jenkins Configuration as Code (JCasC)

### Configuration Location

```yaml
controller:
  JCasC:
    defaultConfig: true
    overwriteConfiguration: false
    configScripts:
      plugin-configs-and-credentials: |
        jenkins:
          systemMessage: "Jenkins configured automatically..."
        credentials:
          system:
            domainCredentials:
              - credentials:
                  - usernamePassword:
                      scope: GLOBAL
                      id: "github-username"
                      username: "sa-jenkins-github-chwy"
                      password: "${jenkins-admin-secret-github-token}"
```

### How to Modify

* **Add New Config Scripts:** Create entries under `configScripts`
* **Modify Existing Scripts:** Edit the YAML block directly
* **Secret References:** Use `${secret-name-key-name}` format

### Configuration Sections

* `jenkins`: Core Jenkins settings
* `credentials`: Credential management
* `unclassified`: Plugin-specific configurations

#### Example: LDAP Configuration

```yaml
configScripts:
  ldap-config: |
    jenkins:
      securityRealm:
        ldap:
          configurations:
            - server: "ldap://your-ldap-server:389"
              rootDN: "dc=company,dc=com"
              userSearchBase: "ou=users"
```

---

## Ingress Configuration

### Configuration Location

```yaml
controller:
  ingress:
    enabled: true
    ingressClassName: nginx
    hostName: dev-use1-mgmt-main.management.dev.aws.chewy.cloud
    path: /jenkins
    annotations:
      nginx.ingress.kubernetes.io/proxy-body-size: "0"
      nginx.ingress.kubernetes.io/proxy-read-timeout: "600"
      nginx.ingress.kubernetes.io/proxy-send-timeout: "600"
      nginx.ingress.kubernetes.io/proxy-connect-timeout: "600"
```

### How to Modify

* **Change Hostname:** Update `hostName`
* **Path Modification:** Adjust `path` and `jenkinsUriPrefix` accordingly
* **Annotations:** Update to support NGINX/ALB controllers

#### Example: ALB Ingress

```yaml
ingress:
  ingressClassName: alb
  annotations:
    alb.ingress.kubernetes.io/scheme: internal
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:...
```

---

## Agent Configuration

### Configuration Location

```yaml
agent:
  enabled: true
  serviceAccount: jenkins-ecr-pull-role
  image:
    repository: jenkins/inbound-agent
    tag: 3324.vea_eda_e98cd69-1
  podTemplates:
    java17: |
      - name: java17
        label: java17
        serviceAccount: jenkins-ecr-pull-role
        containers:
          - name: java17
            image: 278833423079.dkr.ecr.us-east-1.amazonaws.com/hlth/hlth-docker-jenkins-agents:al2023-corretto17-latest
```

### How to Modify

* **Add New Agent Types:** Add new entries to `podTemplates`
* **Modify Resources:** Configure CPU/memory in the pod templates
* **Custom Images:** Update `image.repository` and `image.tag`
* **Service Account:** Set service accounts per pod type

#### Example: Python Agent

```yaml
podTemplates:
  python39: |
    - name: python39
      label: python39
      serviceAccount: jenkins-ecr-pull-role
      containers:
        - name: python39
          image: python:3.9-slim
          command: "sleep"
          args: "99d"
          ttyEnabled: true
```

---

## Persistent Volume

### Configuration Location

```yaml
persistence:
  enabled: true
  storageClass: "efs-sc"
  accessMode: "ReadWriteMany"
  size: "100Gi"
  annotations:
  labels:
    app: jenkins
    storage: efs
```

### How to Modify

* **Terraform Provisioning:** Follow infra documentation for setting up EFS
* **Storage Class:** Should match EFS-backed class
* **Size:** Adjust `size` for required capacity
* **Access Mode:**

  * `ReadWriteOnce`: Single-node
  * `ReadWriteMany`: Multi-node (recommended for Jenkins)
* **Use Existing Claim:** Define `existingClaim` if PVC is pre-created

#### Storage Options

```yaml
# EBS Storage
storageClass: "gp3"
accessMode: "ReadWriteOnce"

# EFS Storage (current)
storageClass: "efs-sc"
accessMode: "ReadWriteMany"
```

---

## Service Accounts

### Configuration Location

```yaml
serviceAccount:
  create: true
  name:
  annotations: {}

serviceAccountAgent:
  create: true
  name: jenkins-ecr-pull-role
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::278833423079:role/jenkins-eks-ecr-pull-role
```

### How to Modify

* **Controller Account:** Modify `serviceAccount`
* **Agent Account:** Modify `serviceAccountAgent`
* **IAM Roles:** Update `eks.amazonaws.com/role-arn`
* **Add Permissions:** Extend with RBAC policies if needed

#### Example: Custom IAM Role

```yaml
serviceAccountAgent:
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456789:role/my-custom-jenkins-role
```

---

## External Secrets

### Configuration Location

```yaml
externalSecrets:
  enabled: true
  secretStore:
    name: jenkins-eks-secretstore
    kind: SecretStore
    aws:
      service: SecretsManager
      region: us-east-1
  externalSecret:
    name: jenkins-admin-secret
    namespace: jenkins
    refreshInterval: 1h
    awsSecret:
      name: /chewy/dev/us-east-1/jenkins-eks/admin-credentials
      keys:
        - secretKey: username
          property: username
        - secretKey: password
          property: password
```

### How to Setup External Secrets

1. **Install External Secrets Operator**
   [SOPS Implementation Guide (Chewy)](https://chewyinc.atlassian.net/wiki/spaces/CND/pages/2355366466/SOPS+Implementation+Guide)
2. **Update Secrets**

```yaml
externalSecret:
  awsSecret:
    keys:
      - secretKey: new-api-token
        property: "New API Token"
```

---

## Deployment

The deployment of the Jenkins EKS Helm chart is automated through the CI/CD pipeline.

### Versioning the Helm Chart

Versioned using semantic release standards before packaging.

### Packaging the Helm Chart

Packaged into a `.tgz` archive for publishing.

### Checking for Terraform Changes

If Terraform changes are detected, they are packaged and uploaded to:

```
https://chewyinc.jfrog.io/artifactory/releng-binary-dev-local/
```

### Publishing to JFrog Artifactory

Helm charts are uploaded to:

```
https://chewyinc.jfrog.io/artifactory/chewy-helm-dev-virtual
```

### Argo CD Sync

Argo CD detects chart updates and syncs the deployment automatically to the EKS cluster.
