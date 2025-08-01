package pipelines.module

import org.apache.commons.lang.StringUtils

class ModuleProps {
    static final YACLI_IMAGE = '278833423079.dkr.ecr.us-east-1.amazonaws.com/plat/yacli:1.0'
    static final CI_BASE_BUILD_IMAGE = '278833423079.dkr.ecr.us-east-1.amazonaws.com/plat/ci-base-build:2.0'
    static final CHEWY_TERRAFORM_IMAGE = '278833423079.dkr.ecr.us-east-1.amazonaws.com/plat/chewy-terraform:0.13.7-d68bc7b'
    def props

    ModuleProps(props) {
        // To avoid CPS issues do not call any methods in a constructor
        this.props = props
    }

    String getName() {
        initializeName()
        return props.name
    }

    String getModuleName() {
        return props.moduleName
    }

    Map getAwsAccounts() {
        return props.awsAccounts
    }

    Map getCluster() {
        return props.cluster
    }

    String getDockerImage() {
        //TODO: add generic build tooling support
        return props?.buildEnvironment?.dockerImage ?: props?.dockerImage
    }

    String getEcrRepo() {
        return props.ecrRepo
    }

    String getChartDirectory() {
        return props.chartDirectory
    }

    String getDeploymentTimeout() {
        return props.deploymentTimeout
    }

    Map getClosureMap() {
        return props.closureMap
    }

    /**
     * Get build environment configuration
     * @return Build environment configuration Map, returns null if not configured
     */
    Map<String, Object> getBuildEnvironment() {
        return props.buildEnvironment
    }

    private void initializeName() {
        if (!props.name) {
            if (props.type instanceof Class) {
                props.name = StringUtils.removeEnd(props.type.getSimpleName().toLowerCase(), 'module')
            } else {
                props.name = StringUtils.uncapitalize(props?.type)
            }
        }
    }
}
