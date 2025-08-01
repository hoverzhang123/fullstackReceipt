# Jenkins CICD Library

Repo with common jenkins-cicd functionality for Chewy teams to adopt CI/CD for their applications

## Development

For local development help, see [Development](docs/development.md)

## Using the Library

To see onboarding doc, check out [Onboarding guide](https://chewyinc.atlassian.net/wiki/spaces/RELENG/pages/2934605052/Jenkins+CICD+Onboarding+Guide)
To see a good reference implementation check out [cicd-sandbox](https://github.com/Chewy-Inc/cicd-sandbox)

## Testing
### Testing in feature branch
In [cicd-sandbox](https://github.com/Chewy-Inc/cicd-sandbox), create a feature branch and adopt the jenkins-cicd feature branch in the Jenkinsfile.
Example:
```
library identifier: 'jenkins-cicd@feature/RXM-5634' ...
```

Next, cut the PR with title `Test for jenkins-cicd {feature branch name}`, and then it will trigger the cicd-sandbox feature branch pipeline.
Finally, check the results in [cicd-sandbox jenkins](https://jenkins-nonprod.shss.chewy.com/job/build-cicd-sandbox/)

### Testing in preProd pipeline
Adopting the same jenkins-cicd feature branch above, then you can commit to a `feature/deployment` branch in cicd-sandbox,
which should mimic the PreprodPipeline. It will generate a tag though so make sure that you will clean up the tag after testing.

### Testing in prd pipeline
In cicd-sandbox jenkins prd, click "Build with Parameters", then use the appropriate tag(default to LATEST), finally click "Build".


## Using the `branchApplyTerraform` Method

The `branchApplyTerraform` method is designed for testing new resources or resources that other resources are not dependent on.
It should only be used under these circumstances to avoid unintended consequences or conflicts with existing resources.

Before running `branchApplyTerraform`, ensure that a valid Terraform plan has been generated and reviewed.
The plan should match your expectations for the changes that will be made.
If there is any uncertainty or if the plan does not match your expectations, abort the operation.

Remember, `branchApplyTerraform` is a powerful tool that directly affects your infrastructure.
Use it with caution and always review your Terraform plans before applying them.

## STRUCTURE OF THE LIBRARY

## [Pipeline General Stages](https://github.com/Chewy-Inc/jenkins-cicd/tree/main/src/pipelines/stages)
- [BuildModules](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/stages/BuildModules.groovy)
- [ValidateModules](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/stages/ValidateModules.groovy)
- [PublishModules](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/stages/PublishModules.groovy)
- [DeployModules](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/stages/DeployModules.groovy)
  - preCheck
  - globalDeploy
  - regionDeploy
  - postCheck
- [RollbackModules](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/stages/RollbackModules.groovy)
  - globalRollback
  - regionRollback

## Available Modules (for applications)
- [E2E](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/E2EModule.groovy)
- [ChewyCommons](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/ChewyCommonsModule.groovy)
- [Datadog](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/DatadogMonitorModule.groovy)
- [Docker (Gradle dockerBuildImage)](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/DockerModule.groovy)
- [Dynatrace](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/DynatraceMonitorModule.groovy)
- [Flyway](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/FlywayModule.groovy)
- [Gatling](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/GatlingModule.groovy)
- [AptHelm](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/AptHelmModule.groovy)
- [HelmChart](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/HelmChartModule.groovy)
- [Helm (RxP cluster)](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/HelmModule.groovy)
- [Lambda](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/LambdaModule.groovy)
- [Library (Java, Artifactory)](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/LibraryModule.groovy)
- [Makefile](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/MakefileProjectModule.groovy)
- [Playwright](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/PlaywrightModule.groovy)
- [React](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/ReactModule.groovy)
- [Spec (OpenAPI)](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/SpecModule.groovy)
- [Terraform](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/TerraformModule.groovy)
- add more when needed...

### Customizable Module
If there's not a pre-defined module that works for a special case, then the generic [ClosureModule](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/ClosureModule.groovy) should be used.

Example which calls a custom jenkins build during `postCheck` in `stg` environment.

First, define a Groovy closure (lambda method) that takes a [ModuleConfig](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/ModuleConfig.groovy) parameter.
```
def regressionTests = {
  config ->
    def jenkins = config.jenkins

    jenkins.echo("Running regression tests")
    jenkins.build job: 'build-regression-tests/main', ...
```
Note that `config.jenkins` allows access to pre-defined registered methods and jenkins plugins like `echo` and `build` in this example.

Then define the module in `cicd.init`
```
cicd.init(
  ...
  modules: [
    [
      type: 'Closure',
      name: 'regression-tests',
      closureMap: ['postCheck': regressionTests],
      envs: ['stg'],
    ],
  ],
)
```

## CicdSandboxTriggerModule

The `CicdSandboxTriggerModule` automatically validates jenkins-cicd feature branches by triggering the cicd-sandbox project during PR builds. This ensures that changes to the jenkins-cicd library are tested before being merged.

### Key Components
- **shouldTriggerSandbox**: Only triggers for PR builds (`config.isPrBuild`)
- **triggerSandboxTest**: Executes the cicd-sandbox job with the current branch
- **checkSandboxTest**: Validates the result and fails the pipeline if sandbox test fails
- **Configuration**: Uses `build-cicd-sandbox/main` job path by default

### Usage
Add to your jenkins-cicd jenkinsfile:
```groovy
modules: [
    [
        type: 'CicdSandboxTrigger'
    ]
]
```

### Module Configuration Examples

#### TerraformModule
```groovy
cicd.init(
    modules: [
        [
            type: 'Terraform',
            name: 'xx',
            ecrRepo: 'xxx'
        ]
    ]
)

```

## Example apps for each module
- CicdSandboxTriggerModule
  - [jenkins-cicd itself](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/Jenkinsfile) - validates jenkins-cicd feature branches
  - [cicd-sandbox](https://github.com/Chewy-Inc/cicd-sandbox) - test project triggered by this module
- DockerModule
  - [rxp-rx-service-b](https://github.com/Chewy-Inc/rxp-rx-service-b/blob/master/Jenkinsfile)
- DatadogMonitorModule
  - [used in pre and post check monitoring/gating](https://app.datadoghq.com/monitors/165177692)
- E2EModule (GLOBAL)
  - [rxp-rx-service-b](https://github.com/Chewy-Inc/rxp-rx-service-b/blob/master/Jenkinsfile)
- FlywayModule
  - [rxp-notification-service](https://github.com/Chewy-Inc/rxp-notification-service/blob/main/Jenkinsfile)
- GatlingModule
  - [pethealth-gateway](https://github.com/Chewy-Inc/pethealth-gateway/blob/main/Jenkinsfile)
- HelmModule (used internally within app modules)
- LambdaModule
  - [rxp-notification-service](https://github.com/Chewy-Inc/rxp-notification-service/blob/main/Jenkinsfile)
- LibraryModule
  - [rxp-auth-utils](https://github.com/Chewy-Inc/rxp-auth-utils/blob/main/Jenkinsfile)
- PlaywrightModule
  - [hc-pet-insurance](https://github.com/Chewy-Inc/hc-pet-insurance/blob/PW_example/Jenkinsfile)
- ReactModule (WORK IN PROGRESS)
  - [rxp-pharamcy-admin-ui](https://github.com/Chewy-Inc/rxp-pharmacist-admin-ui)
- SpecModule
  - [rxp-drug-rx-service](https://github.com/Chewy-Inc/rxp-drug-rx-service/blob/main/Jenkinsfile)
- TerraformModule
  - [rxp-drug-rx-service](https://github.com/Chewy-Inc/rxp-drug-rx-service/blob/main/Jenkinsfile)

## [Pipeline Specialized Stages](https://github.com/Chewy-Inc/jenkins-cicd/tree/main/src/pipelines/module/stage)
- DockerBuildStage
  - used in BuildModules
- KubernetesRegionDeployStage
  - used in regionDeploy
- KubernetesRegionRollbackStage
  - used in regionRollback
- PublishDockerStage
  - used in PublishModules
- PublishLibraryStage
  - used in library modules
- SonarqubeStage
  - used in code coverage gating

## Module and Stage Classes
- [ModuleConfig](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/ModuleConfig.groovy)
  - defines module properties (e.g. rollback, jenkins, region, env, etc.)
- [Module](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/Module.groovy)
  - abstract class to define stages for other modules
- [AbstractStage](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/module/AbstractStage.groovy)
  - abstract stage base class

## [Pipelines](https://github.com/Chewy-Inc/jenkins-cicd/tree/main/src/pipelines)
- [PipelineConfig](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/PipelineConfig.groovy)
- [Pipeline](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/Pipeline.groovy)
  - Pipeline factory returned by `cicd.init` within app Jenkinsfiles
- DefaultStages
  - Default stages contain a list of stages with their matching criteria and corresponding method calls. This encapsulates common stages shared across the jenkins-cicd apps.
- BranchPipeline
  - Branch pipeline initialized when PRs are created (not merged)
  - Generates short-lived namespace for user testing
- TerraformBranchPipeline
  - Same as branch pipeline but initiates a terraform apply in the (default rxp) dev eks cluster
- PreprodPipeline
  - Occurs when PRs are merged
- ProdPipeline
  - Production deployment
  - More information referenced below

## [CR Modules - methods and order of operations](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/stages/CRModules.groovy)
[Automated Deployment Overview](https://chewyinc.atlassian.net/wiki/spaces/PPE/pages/2381056251/Automated+Deployment+Overview)
ALL CR TYPES RUN THROUGH THE SAME STAGES (precheck, globalDeploy, regionDeploy, postcheck, global rollback, region rollback)
There are 3 types of production deployments (logic separated in the [run](https://github.com/Chewy-Inc/jenkins-cicd/blob/main/src/pipelines/stages/CRModules.groovy#L38C1-L62C6) method:
- Manual CR deployment - requires a CR number
  - takes CR number (and optional ECR flag) as input
  - validates and starts the change
- Automated one-click deployment - user specifies a tag
  - "one-click" deploy (specify tag and click run)
  - CR must be manually closed
- Automated daily deployment - CRON trigger automatically triggers a deploy to the latest commit tag
  - Latest tag commit is pulled and deployed
    - if the latest tag is currently deployed
      - CR is not generated. Jenkins job returns with status NOT_BUILT
  - CR is automatically generated, assigned, and started
  - On successful post-check validation, CR is automatically closed
  - On failed post-check validation, the app (helm) is rolled back to the previous version and the CR is NOT closed)