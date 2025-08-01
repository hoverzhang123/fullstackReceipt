package module

import org.junit.jupiter.api.Test
import pipelines.PipelineConfig
import pipelines.module.ChewyCommonsModule
import pipelines.module.DatadogMonitorModule
import pipelines.module.DockerModule
import pipelines.module.DynatraceMonitorModule
import pipelines.module.E2EModule
import pipelines.module.GatlingModule
import pipelines.module.HelmChartModule
import pipelines.module.LambdaModule
import pipelines.module.LibraryModule
import pipelines.module.MakefileProjectModule
import pipelines.module.ModuleFactory
import pipelines.module.PlaywrightModule
import pipelines.module.ReactModule
import pipelines.module.SpecModule

import static org.junit.jupiter.api.Assertions.assertThrows

class ModuleFactoryTest {
    def jenkins = [env: [ENVIRONMENT: 'dev'], params: []]
    ArrayList<List<String>> deploymentLowerEnvironments = [['dev', 'qat'], ['stg']]
    PipelineConfig mockPipelineConfig = PipelineConfig.builder()
    .jenkins(jenkins)
    .deploymentLowerEnvironments(deploymentLowerEnvironments)
    .slackParams([slackChannel: 'slack-channel'])
    .build()

    @Test
    void shouldBuildAppModule() {
        def module = ModuleFactory.createModule(mockPipelineConfig, [type: 'App', name: 'app'])
        assert module instanceof DockerModule
        assert module.name.contains('app')
    }

    @Test
    void shouldBuildChewyCommonsModule() {
        def module = ModuleFactory.createModule(mockPipelineConfig, [type: 'ChewyCommons', name: 'commons'])
        assert module instanceof ChewyCommonsModule
        assert module.name.contains('commons')
    }

    @Test
    void shouldBuildDatadogModule() {
        def module = ModuleFactory.createModule(mockPipelineConfig, [type: 'Datadog'])
        assert module instanceof DatadogMonitorModule

        module = ModuleFactory.createModule(mockPipelineConfig, [type: 'DatadogMonitor'])
        assert module instanceof DatadogMonitorModule
    }

    @Test
    void shouldBuildDyantraceModule() {
        def module = ModuleFactory.createModule(mockPipelineConfig, [type: 'Dynatrace'])
        assert module instanceof DynatraceMonitorModule

        module = ModuleFactory.createModule(mockPipelineConfig, [type: 'DynatraceMonitor'])
        assert module instanceof DynatraceMonitorModule
    }

    @Test
    void shouldBuildE2EModule() {
        def module = ModuleFactory.createModule(mockPipelineConfig, [type: 'E2E'])
        assert module instanceof E2EModule
        assert module.e2eTestEnvironments == ['dev', 'qat', 'stg']

        module = ModuleFactory.createModule(mockPipelineConfig, [type: 'E2E', testEnvironments: ['qat']])
        assert module instanceof E2EModule
        assert module.e2eTestEnvironments == ['qat']
    }

    @Test
    void shouldBuildGatlingModule() {
        def module = ModuleFactory.createModule(mockPipelineConfig, [type: 'Gatling'])
        assert module instanceof GatlingModule
        assert module.gatlingModuleConfig.slackChannel == 'slack-channel'

        module = ModuleFactory.createModule(mockPipelineConfig, [type: 'Gatling', slackChannel: 'channel-override'])
        assert module instanceof GatlingModule
        assert module.gatlingModuleConfig.slackChannel == 'channel-override'
    }

    @Test
    void shouldBuildHelmChartModule() {
        def timeout = '15m'
        def module = ModuleFactory.createModule(mockPipelineConfig, [type: 'HelmChart', chartDirectory: 'chart-dir', deploymentTimeout: timeout])
        assert module instanceof HelmChartModule
        assert module.chartDirectory.name.contains('chart-dir')
        assert module.deploymentTimeout == timeout
    }

    @Test
    void shouldBuildLambdaModule() {
        def module = ModuleFactory.createModule(mockPipelineConfig, [type: 'Lambda', name: 'lambda-test'])
        assert module instanceof LambdaModule
        assert module.name.contains('lambda-test')
    }

    @Test
    void shouldBuildLibraryModule() {
        def module = ModuleFactory.createModule(mockPipelineConfig, [type: 'Library', name: 'lib0'])
        assert module instanceof LibraryModule
        assert module.libraries == ['lib0']

        module = ModuleFactory.createModule(mockPipelineConfig, [type: 'Library', names: ['lib1', 'lib2']])
        assert module instanceof LibraryModule
        assert module.libraries == ['lib1', 'lib2']
    }

    @Test
    void shouldBuildMakefileProjectModule() {
        def module = ModuleFactory.createModule(mockPipelineConfig, [type: 'Makefile', name: 'makefile-project', makefilePath: 'makefile-path'])
        assert module instanceof MakefileProjectModule
        assert module.name == 'makefile-project'
        assert module.makefileFile.name.contains('makefile-path')

        module = ModuleFactory.createModule(mockPipelineConfig, [type: 'Make', name: 'makefile-project'])
        assert module instanceof MakefileProjectModule


        module = ModuleFactory.createModule(mockPipelineConfig, [type: 'MakefileProject'])
        assert module instanceof MakefileProjectModule
    }

    @Test
    void shouldBuildPlaywrightModule() {
        def module = ModuleFactory.createModule(mockPipelineConfig, [type: 'Playwright', name: 'playwright-test'])
        assert module instanceof PlaywrightModule
        assert module.name.contains('playwright-test')
    }

    @Test
    void shouldBuildReactModule() {
        def module = ModuleFactory.createModule(mockPipelineConfig, [type: 'React', name: 'react-test'])
        assert module instanceof ReactModule
        assert module.name == 'react-test'
    }

    @Test
    void shouldBuildSpecModule() {
        def module = ModuleFactory.createModule(mockPipelineConfig, [type: 'Spec', name: 'spec-test'])
        assert module instanceof SpecModule
        assert module.name == 'spec-test'
    }

    @Test
    void shouldThrowInvalidModuleTypeException() {
        def exception = assertThrows(IllegalArgumentException) {
            ModuleFactory.createModule(mockPipelineConfig, [type: 'InvalidModuleType'])
        }
        assert exception.message.contains( "Unable to load module")
        assert exception.message.contains( "InvalidModuleType")
    }
}
