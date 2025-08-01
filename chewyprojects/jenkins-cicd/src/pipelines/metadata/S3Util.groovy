package pipelines.metadata

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import pipelines.PipelineConfig

import java.lang.reflect.Type

class S3Util {
    static final S3_BUCKET_ENV = "shd"
    static String S3_BUCKET

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create()
    static final Type DM_TYPE = TypeToken.getParameterized(List.class, DeploymentMetadata.class).getType()
    static final Type VERSION_TYPE = TypeToken.getParameterized(Map.class, String.class, String.class).getType()

    // TODO: determine if these variables are hard coded
    final String S3_PATH_PREFIX = "version-metadata"
    final String S3_FILENAME = "jenkins-cicd.json"
    final String S3_LATEST_FILENAME = "latest-version.json"

    def jenkins

    S3Util(def jenkins, String metadataBucket = "shd-use1-jenkins-cicd-deployment-metadata-bucket") {
        this.jenkins = jenkins
        S3_BUCKET = metadataBucket
    }

    static List<DeploymentMetadata> deserializeDeploymentMetadata(String data) {
        try {
            List<DeploymentMetadata> result = GSON.fromJson(data ?: '', DM_TYPE)
            return result ?: []
        } catch (JsonSyntaxException ignored) {
            return []
        }
    }

    // resolves GString to String conversion errors
    static void normalizeAllStrings(Object obj) {
        if (obj instanceof Map) {
            obj.each { k, v -> obj[k] = normalizeValue(v) }
        } else if (obj instanceof List) {
            obj.eachWithIndex { v, i -> obj[i] = normalizeValue(v) }
        } else if (!(obj instanceof String)) {
            // Try to convert object properties to strings if possible
            obj.properties.each { k, v ->
                if (!['class', 'metaClass'].contains(k)) {
                    try {
                        obj."$k" = normalizeValue(v)
                    } catch (ignored){}
                }
            }
        }
    }

    static Object normalizeValue(Object val) {
        if (val instanceof GString) {
            return val.toString()
        } else {
            normalizeAllStrings(val)
            return val
        }
    }


    static String serializeDeploymentMetadata(List<DeploymentMetadata> metadata) {
        metadata.each { normalizeAllStrings(it) }
        return GSON.toJson(metadata)
    }

    static Map<String, String> deserializeVersions(String data) {
        try {
            Map<String, String> result = GSON.fromJson(data ?: '', VERSION_TYPE)
            return result ?: [:]
        } catch (JsonSyntaxException ignored) {
            return [:]
        }
    }

    static String serializeVersions(Map<String, String> versions) {
        return GSON.toJson(versions)
    }

    static List<String> getPreviousDeployedEnvs(List<List<String>> envs, String env) {
        if (envs == null) {
            return new ArrayList<>();
        }
        if (env == 'prd') {
            return envs.last()
        }

        List<String> prevEnv = new ArrayList<>();
        for (List<String> envList: envs) {
            if (envList.contains(env)) {
                break;
            }
            prevEnv = envList;
        }
        return prevEnv;
    }

    boolean fileExists(DeploymentID did) {
        return downloadFromS3(getMetadataPath(did)) != '';
    }

    List<DeploymentMetadata> downloadMetadata(DeploymentID did) {
        return deserializeDeploymentMetadata(downloadFromS3(getMetadataPath(did)))
    }

    void uploadMetadata(DeploymentID did, DeploymentMetadata dm) {
        List<DeploymentMetadata> ldm = new ArrayList<>(downloadMetadata(did))
        ldm.add(dm)
        uploadToS3(getMetadataPath(did), serializeDeploymentMetadata(ldm))
    }

    String getLatestVersion(DeploymentID did) {
        return deserializeVersions(downloadFromS3(getLatestFilePath(did))).get(did.environment)
    }

    void updateLatestVersion(DeploymentID did) {
        Map<String, String> versions = new HashMap<>(deserializeVersions(downloadFromS3(getLatestFilePath(did))))
        versions.put(did.environment, did.version)
        uploadToS3(getLatestFilePath(did), serializeVersions(versions))
    }

    boolean validateSuccessfulDeployment(DeploymentID did) {
        List<DeploymentMetadata> ldm = downloadMetadata(did)
        if (ldm.isEmpty()) {
            return false;
        }
        DeploymentMetadata metadata = ldm.last();
        return metadata != null && metadata.deployment != null && metadata.deployment.success;
    }

    // retrieve the latest lower environment version
    boolean validatePreviousEnvironmentMetadata(String projectName, String projectVersion, List<String> regions, List<List<String>> deploymentLowerEnvironments) {
        String simplifiedVersion = projectVersion
        if (projectVersion.startsWith("v")) {
            simplifiedVersion = projectVersion.substring(1)
        }
        // get previous lower environment
        List<String> previousEnvs = getPreviousDeployedEnvs(deploymentLowerEnvironments, 'prd')
        if (previousEnvs == null || previousEnvs.isEmpty()) {
            jenkins.echo("validatePreviousEnvironmentMetadata: no previous environment metadata found");
            return false
        }
        for (String env : previousEnvs) {
            for (String region : regions) {
                jenkins.echo("validating previous environment metadata for ${env} in ${region} for version ${simplifiedVersion}")
                DeploymentID did = new DeploymentID([
                    app: projectName,
                    version: simplifiedVersion,
                    environment: env,
                    shortRegion: region,])
                if (!validateSuccessfulDeployment(did)) {
                    jenkins.echo("deployed version ${projectVersion} to ${env} in ${region} is not successful")
                    return false
                }
            }
        }

        return true
    }

    boolean validateSuccessfulDeploymentMetadata(PipelineConfig pipelineConfig, String projectVersion, String environment) {
        def s3Util = new S3Util(jenkins, pipelineConfig.deploymentMetadataBucket)
        String simplifiedVersion = projectVersion
        if (projectVersion.startsWith("v")) {
            simplifiedVersion = projectVersion.substring(1)
        }

        for(String region: pipelineConfig.regions) {
            DeploymentID id = new DeploymentID([
                app        : pipelineConfig.projectName,
                version    : simplifiedVersion,
                environment: environment,
                shortRegion: region,])

            List<DeploymentMetadata> metadataList = s3Util.downloadMetadata(id)
            if(metadataList == null || metadataList.isEmpty()) {
                return false;
            }
            DeploymentMetadata metadata = metadataList.get(0)
            if (metadata == null || metadata.deployment == null || !metadata.deployment.success) {
                return false
            }
        }

        return true
    }

    boolean validateExistenceOfDeploymentMetadata(PipelineConfig pipelineConfig, String projectVersion, String environment) {
        def s3Util = new S3Util(jenkins, pipelineConfig.deploymentMetadataBucket)
        String simplifiedVersion = projectVersion
        if (projectVersion.startsWith("v")) {
            simplifiedVersion = projectVersion.substring(1)
        }

        for (String region: pipelineConfig.regions) {
            DeploymentID id = new DeploymentID([
                app        : pipelineConfig.projectName,
                version    : simplifiedVersion,
                environment: environment,
                shortRegion: region,])

            boolean metadataExists = s3Util.fileExists(id)
            if (metadataExists) {
                jenkins.echo("${environment} deployment metadata for ${simplifiedVersion} in region ${region} exists")
                return true
            }
        }
        return false
    }

    private String getMetadataPath(DeploymentID did) {
        return "s3://${S3_BUCKET}/${S3_PATH_PREFIX}/${did.app}/${did.version}/${did.environment}/${did.shortRegion}/${S3_FILENAME}"
    }

    private String getLatestFilePath(DeploymentID did) {
        return "s3://${S3_BUCKET}/${S3_PATH_PREFIX}/${did.app}/${S3_LATEST_FILENAME}"
    }

    private String downloadFromS3(String path) {
        jenkins.sh "${jenkins.ecrLogin()}"
        // the metadata bucket is stored in DEV
        jenkins.withAWS(role: "arn:aws:iam::${jenkins.ACCOUNTS[S3_BUCKET_ENV]}:role/CHEWY-cross-jenkins") {
            def fileExists = jenkins.sh(returnStatus: true, script: "aws s3 ls ${path}")
            if (fileExists == 0) {
                jenkins.echo("downloadFromS3: metadata found")
                return jenkins.sh(returnStdout: true, script: "aws s3 cp --quiet ${path} -")
            } else {
                jenkins.echo("downloadFromS3: metadata not found")
                return ''
            }
        }
    }

    private void uploadToS3(String path, String contents) {
        jenkins.sh "${jenkins.ecrLogin()}"
        // the metadata bucket is stored in DEV
        jenkins.withAWS(role: "arn:aws:iam::${jenkins.ACCOUNTS[S3_BUCKET_ENV]}:role/CHEWY-cross-jenkins") {
            jenkins.sh "cat <<EOFCICD | aws s3 cp - ${path}\n${contents}\nEOFCICD"
        }
    }
}
