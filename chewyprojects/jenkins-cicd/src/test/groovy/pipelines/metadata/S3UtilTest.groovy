package pipelines.metadata

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class S3UtilTest {
    // used in tests where methods include echo
    static def echo(String message) {
        println "Mocked echo: $message"
    }
    static final DeploymentID deploymentId = new DeploymentID([app:'app', version:'v1.0', environment:'dev', shortRegion:'use1'])
    static final Validation validation = new Validation([success:['s1', 's2'], failure:['f1', 'f2']])
    static final Deployment deployment = new Deployment([success:true, currentVersion:'v0.1.1', rollbackVersion:'v0.1.0'])
    static final DeploymentMetadata deploymentMetadata = new DeploymentMetadata([timestamp:'2024-09-26T12:14:00Z', jenkinsLink:'jenkins.com/path', deployment:deployment, validation:validation])

    @Test
    void testJsonSerialization() {
        String json = S3Util.serializeDeploymentMetadata([deploymentMetadata])
        assertThat(json).isNotBlank()

        List<DeploymentMetadata> result = S3Util.deserializeDeploymentMetadata(json)
        assertThat(result).hasSize(1)
        assertThat(result[0]).usingRecursiveComparison().isEqualTo(deploymentMetadata)
    }

    class Jenkins {
        boolean validMetadataDownload

        Jenkins(isValidMetadata = true) {
            this.validMetadataDownload = isValidMetadata
        }
        boolean ecrLoginHit = false
        boolean awsDownloadCliHit = false
        boolean awsUploadCliHit = false
        String path = ''
        String contents = ''

        def ACCOUNTS = [shd: '278833423079']
        String ecrLogin() {
            'ecrLogin'
        }
        def sh(Map args) {
            return sh(args['script'], args.getOrDefault('returnStatus', true), args.getOrDefault('returnStdout', false))
        }
        def sh(String script, boolean returnStatus = true, boolean returnStdout=false) {
            if (script.startsWith(ecrLogin())) {
                this.ecrLoginHit = true
                return 'logged in'
            } else if (script.startsWith('aws s3 ls')) {
                return 0 // success
            } else if (script.startsWith('aws s3 cp')) {
                this.awsDownloadCliHit = true
                this.path = script.substring(script.indexOf('s3://'))
                return S3Util.serializeDeploymentMetadata(new ArrayList<DeploymentMetadata>(validMetadataDownload ? [deploymentMetadata]: []))
            } else if (script.startsWith("cat <<") && script.contains('aws s3 cp')) {
                this.awsUploadCliHit = true
                this.path = script.substring(script.indexOf('s3://'), script.indexOf('\n'))
                this.contents = script.substring(script.indexOf('\n') + 1, script.indexOf("\nEOFCICD"))
                return 'response'
            }
            return "Unexpected command"
        }
        Object withAWS(Map map, Closure inner) {
            inner()
        }
    }

    @Test
    void testDownloadMetadata() {
        def jenkins = new Jenkins()
        def s3Util = new S3Util(jenkins)
        assertThat(s3Util.downloadMetadata(deploymentId)).hasSize(1)
        assertThat(jenkins.getEcrLoginHit()).isTrue()
        assertThat(jenkins.getAwsDownloadCliHit()).isTrue()
        assertThat(jenkins.getAwsUploadCliHit()).isFalse()
        assertThat(jenkins.getPath()).isEqualTo("s3://shd-use1-jenkins-cicd-deployment-metadata-bucket/version-metadata/app/v1.0/dev/use1/jenkins-cicd.json -")
    }

    @Test
    void testFileExists() {
        def jenkins = new Jenkins()
        def s3Util = new S3Util(jenkins)
        assertThat(s3Util.fileExists(deploymentId)).isTrue()
        assertThat(jenkins.getEcrLoginHit()).isTrue()
        assertThat(jenkins.getAwsDownloadCliHit()).isTrue()
        assertThat(jenkins.getAwsUploadCliHit()).isFalse()
        assertThat(jenkins.getPath()).isEqualTo("s3://shd-use1-jenkins-cicd-deployment-metadata-bucket/version-metadata/app/v1.0/dev/use1/jenkins-cicd.json -")
    }

    @Test
    void testUploadMetadata() {
        def jenkins = new Jenkins()
        def s3Util = new S3Util(jenkins)
        s3Util.uploadMetadata(deploymentId, deploymentMetadata)
        assertThat(jenkins.getEcrLoginHit()).isTrue()
        assertThat(jenkins.getAwsDownloadCliHit()).isTrue()
        assertThat(jenkins.getAwsUploadCliHit()).isTrue()
        assertThat(jenkins.getPath()).isEqualTo("s3://shd-use1-jenkins-cicd-deployment-metadata-bucket/version-metadata/app/v1.0/dev/use1/jenkins-cicd.json")
        List<DeploymentMetadata> result = S3Util.deserializeDeploymentMetadata(jenkins.contents)
        assertThat(result).hasSize(2)
        assertThat(result[0]).usingRecursiveComparison().isEqualTo(deploymentMetadata)
    }

    @Test
    void testUpdateAndRetrieveVersionNumbers() {
        def jenkins = new Jenkins()
        def s3Util = new S3Util(jenkins)
        s3Util.updateLatestVersion(deploymentId)
        assertThat(jenkins.getEcrLoginHit()).isTrue()
        assertThat(jenkins.getAwsDownloadCliHit()).isTrue()
        assertThat(jenkins.getAwsUploadCliHit()).isTrue()
        assertThat(jenkins.getPath()).isEqualTo("s3://shd-use1-jenkins-cicd-deployment-metadata-bucket/version-metadata/app/latest-version.json")
        assertThat(S3Util.deserializeVersions(jenkins.contents)).isEqualTo(["dev":"v1.0"])

        jenkins = new Jenkins()
        s3Util = new S3Util(jenkins)
        assertThat(s3Util.getLatestVersion(deploymentId)).isNull()
        assertThat(jenkins.getEcrLoginHit()).isTrue()
        assertThat(jenkins.getAwsDownloadCliHit()).isTrue()
        assertThat(jenkins.getAwsUploadCliHit()).isFalse()
        assertThat(jenkins.getPath()).isEqualTo("s3://shd-use1-jenkins-cicd-deployment-metadata-bucket/version-metadata/app/latest-version.json -")
    }

    @Test
    void testValidateSuccessfulDeployment() {
        def jenkins = new Jenkins()
        def s3Util = new S3Util(jenkins)

        assertThat(s3Util.downloadMetadata(deploymentId)).hasSize(1);
        assertThat(jenkins.getEcrLoginHit()).isTrue()
        assertThat(jenkins.getAwsDownloadCliHit()).isTrue()
        assertThat(jenkins.getAwsUploadCliHit()).isFalse()
        assertThat(jenkins.getPath()).isEqualTo("s3://shd-use1-jenkins-cicd-deployment-metadata-bucket/version-metadata/app/v1.0/dev/use1/jenkins-cicd.json -")
        assertThat(s3Util.validateSuccessfulDeployment(deploymentId)).isTrue()
    }

    @Test
    void testValidateSuccessfulDeploymentFalse() {
        def jenkins = new Jenkins(false)
        def s3Util = new S3Util(jenkins)
        assertThat(s3Util.downloadMetadata(deploymentId)).isEmpty();
        assertThat(jenkins.getEcrLoginHit()).isTrue()
        assertThat(jenkins.getAwsDownloadCliHit()).isTrue()
        assertThat(jenkins.getAwsUploadCliHit()).isFalse()
        assertThat(jenkins.getPath()).isEqualTo("s3://shd-use1-jenkins-cicd-deployment-metadata-bucket/version-metadata/app/v1.0/dev/use1/jenkins-cicd.json -")
        assertThat(s3Util.validateSuccessfulDeployment(deploymentId)).isFalse()
    }

    @Test
    void testValidatePreviousEnvironmentMetadataValid() {
        def jenkins = new Jenkins(true)
        def s3Util = new S3Util(jenkins)
        assertThat(s3Util.downloadMetadata(deploymentId)).hasSize(1);
        assertThat(jenkins.getEcrLoginHit()).isTrue()
        assertThat(jenkins.getAwsDownloadCliHit()).isTrue()
        assertThat(jenkins.getAwsUploadCliHit()).isFalse()
        assertThat(jenkins.getPath()).isEqualTo("s3://shd-use1-jenkins-cicd-deployment-metadata-bucket/version-metadata/app/v1.0/dev/use1/jenkins-cicd.json -")
        assertThat(s3Util.validatePreviousEnvironmentMetadata("app", "v1.0", ["use1"], [["dev"]])).isTrue()
    }

    @Test
    void testValidatePreviousEnvironmentMetadataNoEnvironments() {
        def jenkins = new Jenkins(true)
        def s3Util = new S3Util(jenkins)
        assertThat(s3Util.downloadMetadata(deploymentId)).hasSize(1);
        assertThat(jenkins.getEcrLoginHit()).isTrue()
        assertThat(jenkins.getAwsDownloadCliHit()).isTrue()
        assertThat(jenkins.getAwsUploadCliHit()).isFalse()
        assertThat(jenkins.getPath()).isEqualTo("s3://shd-use1-jenkins-cicd-deployment-metadata-bucket/version-metadata/app/v1.0/dev/use1/jenkins-cicd.json -")
        assertThat(s3Util.validatePreviousEnvironmentMetadata("app", "v1.0", ["use1"], [[]])).isFalse()
    }
}
