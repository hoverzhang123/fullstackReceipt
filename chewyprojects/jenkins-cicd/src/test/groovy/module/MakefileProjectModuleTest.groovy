package module

import org.junit.jupiter.api.Test
import pipelines.BasePipelineTest
import org.junit.jupiter.api.BeforeEach
import pipelines.module.MakefileProjectModule
import static org.junit.jupiter.api.Assertions.assertThrows

class MakefileProjectModuleTest extends BasePipelineTest {

    private static final String makefileModuleJenkinsfile = 'src/test/resources/makefile.Jenkinsfile'

    @Override
    @BeforeEach
    void setUp() {
        super.setUp()
        setupBranchName('main')
        // set up required environment variables for GitHub repository access
        addEnvVar('GIT_URL', 'https://github.com/test-owner/test-repo.git')
        addEnvVar('CHANGE_URL', 'https://github.com/test-owner/test-repo/pull/123')
        addEnvVar('GH_TOKEN', 'test-token')

        // set up S3 mocks for version 1.0.0 (which is what the test uses)
        for (String env : ['dev', 'qat', 'stg']) {
            for (String region : ['us-east-1', 'us-east-2']) {
                helper.addShMock(s3LSPattern('1.0.0', env, region)) {
                    return [stdout: '', exitValue: 0]
                }
                helper.addShMock(s3CPPattern('1.0.0', env, region)) {
                    return [stdout: '[{"deployment":{"success": true}}]', exitValue: 0]
                }
            }
        }
    }

    @Test
    void testMakefileBuilderNullName(){
        def exception = assertThrows(Exception) {
            MakefileProjectModule.builder()
                    .name("makefile-project")
                    .makefilePath(" ")
                    .build()
        }
        assert exception.message.contains("The makefilePath of the MakefileProjectModule cannot be an empty string.")
    }

    @Test
    void testMakefileBuild() {
        runScript(makefileModuleJenkinsfile)
        assertJobStatusSuccess()
    }
}
