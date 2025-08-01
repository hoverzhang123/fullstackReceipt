package module

import com.lesfurets.jenkins.unit.RegressionTest
import com.lesfurets.jenkins.unit.declarative.DeclarativePipelineTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pipelines.PipelineConfig
import pipelines.module.ChewyCommonsModule
import pipelines.module.ModuleConfig

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

class ChewyCommonsModuleTest extends DeclarativePipelineTest implements RegressionTest {

    ChewyCommonsModule chewyCommonsModule
    PipelineConfig pipelineConfig
    ModuleConfig moduleConfig
    def mockJenkins

    @Override
    @BeforeEach
    void setUp() {
        super.setUp()

        mockJenkins = TestUtil.createMockJenkins()

        mockJenkins.commandsExecuted = []
        mockJenkins.sh = { String script ->
            mockJenkins.commandsExecuted << script.replaceAll('\\s+', ' ')
        }

        mockJenkins.withSonarQubeEnv = { String envName, Closure body ->
            mockJenkins.sonarEnvUsed = envName
            body()
        }

        pipelineConfig = PipelineConfig.builder()
                .jenkins(mockJenkins)
                .projectName('chewy-commons-test')
                .build()

        moduleConfig = ModuleConfig.builder()
                .jenkins(mockJenkins)
                .projectName('chewy-commons-test')
                .projectVersion('2.2.2')
                .vertical('hlth')
                .namespace('applications')
                .metadataBucket('')
                .env('dev')
                .region('us-east-1')
                .build()

        chewyCommonsModule = new ChewyCommonsModule(pipelineConfig, [:])
    }

    @Test
    void testBuild() {
        chewyCommonsModule.build(moduleConfig)
        assertTrue(mockJenkins.commandsExecuted.contains("./gradlew -Pversion=2.2.2 build"))
        assertEquals('sonarqube-nonprod', mockJenkins.sonarEnvUsed)
    }

    @Test
    void testValidate() {
        chewyCommonsModule.validate(moduleConfig)
        assertTrue(mockJenkins.commandsExecuted.contains('./gradlew -Pversion=2.2.2 verify'))
    }

    @Test
    void testPublish() {
        chewyCommonsModule.publish(moduleConfig)
        assertTrue(mockJenkins.commandsExecuted.contains('./gradlew -Pversion=2.2.2 publish'))
    }

    @Test
    void testRegionDeploy() {
        chewyCommonsModule.regionDeploy(moduleConfig)
        assertTrue(mockJenkins.commandsExecuted.contains("""./gradlew \
            -Pversion=2.2.2 \
            -Phelm.environment=${moduleConfig.env} \
            -Phelm.region=${moduleConfig.region} \
            deploy
        """.replaceAll('\\s+', ' ')))
    }

    @Test
    void testRegionRollback() {
        chewyCommonsModule.regionRollback(moduleConfig)
        assertTrue(mockJenkins.commandsExecuted.contains("""./gradlew \
            -Pversion=2.2.2 \
            -Phelm.environment=dev \
            -Phelm.region=us-east-1 \
            rollback
        """.replaceAll('\\s+', ' ')))
    }
}
