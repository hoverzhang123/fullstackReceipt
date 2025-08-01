package module

import org.junit.jupiter.api.Test
import pipelines.module.ModuleProps

import static org.junit.jupiter.api.Assertions.*

class ModulePropsTest {
    private static final String TEST_NAME = "test-module"
    private static final String TEST_MODULE_NAME = "test-app"
    private static final Map<String, String> TEST_AWS_ACCOUNTS = [
        dev: "123456789",
        prod: "987654321",
    ]
    private static final Map<String, String> TEST_CLUSTER = [
        namespace: "test-namespace",
        region: "us-east-1",
    ]
    private static final String TEST_ECR_REPO = "test-repo"
    private static final String TEST_CHART_DIRECTORY = "helm/charts"
    private static final String TEST_DEPLOYMENT_TIMEOUT = "5m"

    @Test
    void testGetNameWithExistingName() {
        def props = new ModuleProps([name: TEST_NAME])
        assertEquals(TEST_NAME, props.name)
    }

    @Test
    void testGetNameWithStringType() {
        def props = new ModuleProps([type: "TestModule"])
        assertEquals("testModule", props.name)
    }

    @Test
    void testGetNameWithClassType() {
        def props = new ModuleProps([type: String.class])
        assertEquals("string", props.name)
    }

    @Test
    void testGetNameWithClassTypeEndingInModule() {
        def props = new ModuleProps([type: TestModuleClass.class])
        assertEquals("testmoduleclass", props.name)
    }

    @Test
    void testGetNameWithClassTypeEndingInModuleSuffix() {
        def props = new ModuleProps([type: SampleModule.class])
        assertEquals("sample", props.name)
    }

    @Test
    void testGetNameWithNoType() {
        def props = new ModuleProps([:])
        assertNull(props.name)
    }

    @Test
    void testGetNameWithNullType() {
        def props = new ModuleProps([type: null])
        assertNull(props.name)
    }

    @Test
    void testGetNameMultipleCalls() {
        def props = new ModuleProps([type: "TestModule"])
        // First call should initialize the name
        assertEquals("testModule", props.name)
        // Second call should return the same name without re-initialization
        assertEquals("testModule", props.name)
    }

    @Test
    void testGetModuleNameWithValue() {
        def props = new ModuleProps([moduleName: TEST_MODULE_NAME])
        assertEquals(TEST_MODULE_NAME, props.moduleName)
    }

    @Test
    void testGetModuleNameWithNull() {
        def props = new ModuleProps([:])
        assertNull(props.moduleName)
    }

    @Test
    void testGetAwsAccountsWithValue() {
        def props = new ModuleProps([awsAccounts: TEST_AWS_ACCOUNTS])
        assertEquals(TEST_AWS_ACCOUNTS, props.awsAccounts)
    }

    @Test
    void testGetAwsAccountsWithNull() {
        def props = new ModuleProps([:])
        assertNull(props.awsAccounts)
    }

    @Test
    void testGetAwsAccountsWithEmptyMap() {
        def emptyMap = [:]
        def props = new ModuleProps([awsAccounts: emptyMap])
        assertEquals(emptyMap, props.awsAccounts)
    }

    @Test
    void testGetClusterWithValue() {
        def props = new ModuleProps([cluster: TEST_CLUSTER])
        assertEquals(TEST_CLUSTER, props.cluster)
    }

    @Test
    void testGetClusterWithNull() {
        def props = new ModuleProps([:])
        assertNull(props.cluster)
    }

    @Test
    void testGetClusterWithEmptyMap() {
        def emptyMap = [:]
        def props = new ModuleProps([cluster: emptyMap])
        assertEquals(emptyMap, props.cluster)
    }

    @Test
    void testGetEcrRepoWithValue() {
        def props = new ModuleProps([ecrRepo: TEST_ECR_REPO])
        assertEquals(TEST_ECR_REPO, props.ecrRepo)
    }

    @Test
    void testGetEcrRepoWithNull() {
        def props = new ModuleProps([:])
        assertNull(props.ecrRepo)
    }

    @Test
    void testGetEcrRepoWithEmptyString() {
        def props = new ModuleProps([ecrRepo: ""])
        assertEquals("", props.ecrRepo)
    }

    @Test
    void testGetChartDirectoryWithValue() {
        def props = new ModuleProps([chartDirectory: TEST_CHART_DIRECTORY])
        assertEquals(TEST_CHART_DIRECTORY, props.chartDirectory)
    }

    @Test
    void testGetChartDirectoryWithNull() {
        def props = new ModuleProps([:])
        assertNull(props.chartDirectory)
    }

    @Test
    void testGetChartDirectoryWithEmptyString() {
        def props = new ModuleProps([chartDirectory: ""])
        assertEquals("", props.chartDirectory)
    }

    @Test
    void testGetDeploymentTimeoutWithValue() {
        def props = new ModuleProps([deploymentTimeout: TEST_DEPLOYMENT_TIMEOUT])
        assertEquals(TEST_DEPLOYMENT_TIMEOUT, props.deploymentTimeout)
    }

    @Test
    void testGetDeploymentTimeoutWithNull() {
        def props = new ModuleProps([:])
        assertNull(props.deploymentTimeout)
    }

    @Test
    void testGetDeploymentTimeoutWithEmptyString() {
        def props = new ModuleProps([deploymentTimeout: ""])
        assertEquals("", props.deploymentTimeout)
    }

    @Test
    void testGetClosureMapWithValue() {
        def closureMap = [
            preBuild: { println "pre-build" },
            postBuild: { println "post-build" },
        ]
        def props = new ModuleProps([closureMap: closureMap])
        assertEquals(closureMap, props.closureMap)
    }

    @Test
    void testGetClosureMapWithNull() {
        def props = new ModuleProps([:])
        assertNull(props.closureMap)
    }

    @Test
    void testGetClosureMapWithEmptyMap() {
        def emptyMap = [:]
        def props = new ModuleProps([closureMap: emptyMap])
        assertEquals(emptyMap, props.closureMap)
    }

    @Test
    void testGetBuildEnvironmentWithValue() {
        def buildEnv = [
            jdk: "11",
            gradle: "7.0",
        ]
        def props = new ModuleProps([buildEnvironment: buildEnv])
        assertEquals(buildEnv, props.buildEnvironment)
    }

    @Test
    void testGetBuildEnvironmentWithNull() {
        def props = new ModuleProps([:])
        assertNull(props.buildEnvironment)
    }

    @Test
    void testGetBuildEnvironmentWithEmptyMap() {
        def emptyMap = [:]
        def props = new ModuleProps([buildEnvironment: emptyMap])
        assertEquals(emptyMap, props.buildEnvironment)
    }

    @Test
    void testGetBuildEnvironmentWithComplexValue() {
        def buildEnv = [
            jdk: "17",
            gradle: "8.0",
            dockerImage: "openjdk:17-jdk",
            environment: [
                "JAVA_OPTS": "-Xmx1g",
                "GRADLE_OPTS": "-Dorg.gradle.daemon=false",
            ],
        ]
        def props = new ModuleProps([buildEnvironment: buildEnv])
        assertEquals(buildEnv, props.buildEnvironment)
    }

    @Test
    void testInitializeNameWithClassTypeEndingInModule() {
        def props = new ModuleProps([type: TestModuleClass.class])
        assertEquals("testmoduleclass", props.name)
    }

    @Test
    void testInitializeNameWithStringType() {
        def props = new ModuleProps([type: "CustomTestModule"])
        assertEquals("customTestModule", props.name)
    }

    @Test
    void testInitializeNameWithStringTypeAllCaps() {
        def props = new ModuleProps([type: "TESTMODULE"])
        assertEquals("tESTMODULE", props.name)
    }

    @Test
    void testInitializeNameWithStringTypeLowerCase() {
        def props = new ModuleProps([type: "testmodule"])
        assertEquals("testmodule", props.name)
    }

    @Test
    void testAllPropertiesNull() {
        def props = new ModuleProps([:])
        assertNull(props.name)
        assertNull(props.moduleName)
        assertNull(props.awsAccounts)
        assertNull(props.cluster)
        assertNull(props.ecrRepo)
        assertNull(props.chartDirectory)
        assertNull(props.deploymentTimeout)
        assertNull(props.closureMap)
        assertNull(props.buildEnvironment)
    }

    @Test
    void testConstructorWithNullProps() {
        def props = new ModuleProps(null)
        // Should not throw exception, but accessing properties might
        assertThrows(NullPointerException.class, { props.name })
    }

    @Test
    void testAllPropertiesWithValues() {
        def closureMap = [build: { println "building" }]
        def buildEnv = [jdk: "11"]
        def props = new ModuleProps([
            name: TEST_NAME,
            moduleName: TEST_MODULE_NAME,
            awsAccounts: TEST_AWS_ACCOUNTS,
            cluster: TEST_CLUSTER,
            ecrRepo: TEST_ECR_REPO,
            chartDirectory: TEST_CHART_DIRECTORY,
            deploymentTimeout: TEST_DEPLOYMENT_TIMEOUT,
            closureMap: closureMap,
            buildEnvironment: buildEnv,
        ])

        assertEquals(TEST_NAME, props.name)
        assertEquals(TEST_MODULE_NAME, props.moduleName)
        assertEquals(TEST_AWS_ACCOUNTS, props.awsAccounts)
        assertEquals(TEST_CLUSTER, props.cluster)
        assertEquals(TEST_ECR_REPO, props.ecrRepo)
        assertEquals(TEST_CHART_DIRECTORY, props.chartDirectory)
        assertEquals(TEST_DEPLOYMENT_TIMEOUT, props.deploymentTimeout)
        assertEquals(closureMap, props.closureMap)
        assertEquals(buildEnv, props.buildEnvironment)
    }

    /**
     * Test class used for testing class type handling in ModuleProps
     */
    private static class TestModuleClass {
        String getTestProperty() {
            return "test"
        }
    }

    /**
     * Test class with "Module" suffix for testing StringUtils.removeEnd functionality
     */
    private static class SampleModule {
        String getSampleProperty() {
            return "sample"
        }
    }

    // ========== NEW TESTS TO IMPROVE COVERAGE ==========

    @Test
    void testGetPropsMethod() {
        def testProps = [name: TEST_NAME, moduleName: TEST_MODULE_NAME]
        def props = new ModuleProps(testProps)
        assertEquals(testProps, props.getProps())
    }

    @Test
    void testSetPropsMethod() {
        def originalProps = [name: "original"]
        def props = new ModuleProps(originalProps)

        def newProps = [name: "updated", moduleName: "new-module"]
        props.setProps(newProps)

        assertEquals(newProps, props.getProps())
        assertEquals("updated", props.getName())
        assertEquals("new-module", props.getModuleName())
    }

    @Test
    void testInitializeNameWithExistingNameDoesNotOverwrite() {
        def props = new ModuleProps([name: "existing-name", type: "ShouldNotUseThis"])
        // First call should return existing name without initialization
        assertEquals("existing-name", props.getName())
        // Second call should still return same name
        assertEquals("existing-name", props.getName())
    }

    @Test
    void testInitializeNameWithNullTypeAndNoName() {
        def props = new ModuleProps([type: null])
        // Should not throw exception but name should be null
        assertNull(props.getName())
    }

    @Test
    void testInitializeNameWithEmptyStringType() {
        def props = new ModuleProps([type: ""])
        // StringUtils.uncapitalize on empty string should return empty string
        assertEquals("", props.getName())
    }

    @Test
    void testInitializeNameWithSingleCharacterType() {
        def props = new ModuleProps([type: "A"])
        assertEquals("a", props.getName())
    }

    @Test
    void testInitializeNameWithClassTypeNotEndingInModule() {
        def props = new ModuleProps([type: String.class])
        // String.class.getSimpleName().toLowerCase() = "string", no "module" to remove
        assertEquals("string", props.getName())
    }

    @Test
    void testInitializeNameWithClassTypeExactlyModule() {
        def props = new ModuleProps([type: ModuleClass.class])
        // ModuleClass.getSimpleName().toLowerCase() = "moduleclass", removeEnd("moduleclass", "module") = "moduleclass"
        // Because "moduleclass" doesn't end with "module", nothing is removed
        assertEquals("moduleclass", props.getName())
    }

    @Test
    void testInitializeNameWithStringTypeExactlyModule() {
        def props = new ModuleProps([type: "Module"])
        // StringUtils.uncapitalize("Module") = "module"
        assertEquals("module", props.getName())
    }

    @Test
    void testInitializeNameWithStringTypeEndingInModule() {
        def props = new ModuleProps([type: "TestModule"])
        // StringUtils.uncapitalize("TestModule") = "testModule"
        assertEquals("testModule", props.getName())
    }

    @Test
    void testInitializeNameWithStringTypeMultipleModuleSuffixes() {
        def props = new ModuleProps([type: "ModuleModule"])
        // StringUtils.uncapitalize("ModuleModule") = "moduleModule"
        assertEquals("moduleModule", props.getName())
    }

    @Test
    void testInitializeNameWithComplexClassType() {
        def props = new ModuleProps([type: ComplexTestModule.class])
        // "complextestmodule" -> remove "module" -> "complextest"
        assertEquals("complextest", props.getName())
    }

    @Test
    void testGetNameCalledMultipleTimes() {
        def props = new ModuleProps([type: "TestType"])

        // First call should initialize
        String firstName = props.getName()
        assertEquals("testType", firstName)

        // Subsequent calls should return cached value
        String secondName = props.getName()
        assertEquals("testType", secondName)

        // Should be the same object reference (cached)
        assertSame(firstName, secondName)
    }

    @Test
    void testPropsWithComplexNestedStructure() {
        def complexProps = [
            name: null,
            type: "ComplexModule",
            awsAccounts: [
                dev: "111111111",
                staging: "222222222",
                prod: "333333333",
            ],
            cluster: [
                namespace: "complex-namespace",
                region: "us-west-2",
                size: "large",
            ],
            buildEnvironment: [
                jdk: "17",
                gradle: "8.0",
                environment: [
                    "JAVA_OPTS": "-Xmx2g",
                    "GRADLE_OPTS": "-Dorg.gradle.daemon=false",
                ],
                dockerImage: "openjdk:17-jdk-alpine",
            ],
            closureMap: [
                build: { config -> println "Building ${config.name}" },
                test: { config -> println "Testing ${config.name}" },
                deploy: { config -> println "Deploying ${config.name}" },
            ],
        ]

        def props = new ModuleProps(complexProps)

        // Test all getters
        assertEquals("complexModule", props.getName()) // "ComplexModule" -> uncapitalize -> "complexModule"
        assertNull(props.getModuleName())
        assertEquals(complexProps.awsAccounts, props.getAwsAccounts())
        assertEquals(complexProps.cluster, props.getCluster())
        assertEquals(complexProps.buildEnvironment, props.getBuildEnvironment())
        assertEquals(complexProps.closureMap, props.getClosureMap())
        assertNull(props.getEcrRepo())
        assertNull(props.getChartDirectory())
        assertNull(props.getDeploymentTimeout())
    }

    @Test
    void testPropsWithEmptyMapsAndLists() {
        def props = new ModuleProps([
            name: "empty-test",
            awsAccounts: [:],
            cluster: [:],
            buildEnvironment: [:],
            closureMap: [:],
            ecrRepo: "",
            chartDirectory: "",
            deploymentTimeout: "",
        ])

        assertEquals("empty-test", props.getName())
        assertEquals([:], props.getAwsAccounts())
        assertEquals([:], props.getCluster())
        assertEquals([:], props.getBuildEnvironment())
        assertEquals([:], props.getClosureMap())
        assertEquals("", props.getEcrRepo())
        assertEquals("", props.getChartDirectory())
        assertEquals("", props.getDeploymentTimeout())
    }

    @Test
    void testPropsWithNullValues() {
        def props = new ModuleProps([
            name: null,
            type: null,
            moduleName: null,
            awsAccounts: null,
            cluster: null,
            buildEnvironment: null,
            closureMap: null,
            ecrRepo: null,
            chartDirectory: null,
            deploymentTimeout: null,
        ])

        assertNull(props.getName())
        assertNull(props.getModuleName())
        assertNull(props.getAwsAccounts())
        assertNull(props.getCluster())
        assertNull(props.getBuildEnvironment())
        assertNull(props.getClosureMap())
        assertNull(props.getEcrRepo())
        assertNull(props.getChartDirectory())
        assertNull(props.getDeploymentTimeout())
    }

    @Test
    void testConstructorWithComplexProps() {
        def complexProps = [
            name: "constructor-test",
            type: ConstructorTestModule.class,
            moduleName: "constructor-module",
            awsAccounts: [dev: "123", prod: "456"],
            cluster: [namespace: "test-ns"],
            ecrRepo: "test-ecr",
            chartDirectory: "/helm/charts",
            deploymentTimeout: "10m",
            buildEnvironment: [jdk: "11"],
            closureMap: [build: { -> "building" }],
        ]

        def props = new ModuleProps(complexProps)

        // Verify all properties are accessible
        assertEquals("constructor-test", props.getName())
        assertEquals("constructor-module", props.getModuleName())
        assertEquals([dev: "123", prod: "456"], props.getAwsAccounts())
        assertEquals([namespace: "test-ns"], props.getCluster())
        assertEquals("test-ecr", props.getEcrRepo())
        assertEquals("/helm/charts", props.getChartDirectory())
        assertEquals("10m", props.getDeploymentTimeout())
        assertEquals([jdk: "11"], props.getBuildEnvironment())
        // For closures, we can't directly compare them, so we check the map structure
        def closureMap = props.getClosureMap()
        assertNotNull(closureMap)
        assertTrue(closureMap.containsKey("build"))
        assertNotNull(closureMap.build)
    }

    @Test
    void testEdgeCasesForStringUncapitalize() {
        // Test various edge cases for StringUtils.uncapitalize
        def testCases = [
            [type: "ALLCAPS", expected: "aLLCAPS"],
            [type: "lowercase", expected: "lowercase"],
            [type: "MixedCase", expected: "mixedCase"],
            [type: "A", expected: "a"],
            [type: "AB", expected: "aB"],
            [type: "CamelCase", expected: "camelCase"],
        ]

        testCases.each { testCase ->
            def props = new ModuleProps([type: testCase.type])
            assertEquals(testCase.expected, props.getName(),
                    "Failed for type: ${testCase.type}")
        }
    }

    /**
     * Additional test classes for testing various scenarios
     */
    private static class ModuleClass {
        // Class name is exactly "Module"
        String getModuleProperty() {
            return "module"
        }
    }

    private static class ComplexTestModule {
        // Class name ends with "Module"
        String getComplexProperty() {
            return "complex"
        }
    }

    private static class ConstructorTestModule {
        // Class for constructor testing
        String getConstructorProperty() {
            return "constructor"
        }
    }
}
