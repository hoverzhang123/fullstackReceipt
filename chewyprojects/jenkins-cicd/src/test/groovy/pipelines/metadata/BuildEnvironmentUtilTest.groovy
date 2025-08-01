package pipelines.metadata

import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

/**
 * Test class for BuildEnvironmentUtil
 * Verifies the behavior of build environment utility methods
 */
class BuildEnvironmentUtilTest {

    @Test
    void testHasDockerfileActionWithValidDockerfilePath() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: [
                build: [
                    task: 'build',
                    dockerfilepath: 'Dockerfile',
                ],
            ],
        ]

        assertTrue(BuildEnvironmentUtil.hasDockerfileAction(buildEnvironment))
    }

    @Test
    void testHasDockerfileActionWithMultipleActions() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: [
                build: [task: 'build'],
                publish: [
                    task: 'publish',
                    dockerfilepath: 'docker/Dockerfile',
                ],
            ],
        ]

        assertTrue(BuildEnvironmentUtil.hasDockerfileAction(buildEnvironment))
    }

    @Test
    void testHasDockerfileActionWithEmptyDockerfilePath() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: [
                build: [
                    task: 'build',
                    dockerfilepath: '',
                ],
            ],
        ]

        assertFalse(BuildEnvironmentUtil.hasDockerfileAction(buildEnvironment))
    }

    @Test
    void testHasDockerfileActionWithWhitespaceDockerfilePath() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: [
                build: [
                    task: 'build',
                    dockerfilepath: '   ',
                ],
            ],
        ]

        assertFalse(BuildEnvironmentUtil.hasDockerfileAction(buildEnvironment))
    }

    @Test
    void testHasDockerfileActionWithNullDockerfilePath() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: [
                build: [
                    task: 'build',
                    dockerfilepath: null,
                ],
            ],
        ]

        assertFalse(BuildEnvironmentUtil.hasDockerfileAction(buildEnvironment))
    }

    @Test
    void testHasDockerfileActionWithNoDockerfilePath() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: [
                build: [task: 'build'],
            ],
        ]

        assertFalse(BuildEnvironmentUtil.hasDockerfileAction(buildEnvironment))
    }

    @Test
    void testHasDockerfileActionWithNonMapActions() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: 'not-a-map',
        ]

        assertFalse(BuildEnvironmentUtil.hasDockerfileAction(buildEnvironment))
    }

    @Test
    void testHasDockerfileActionWithNullActions() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: null,
        ]

        assertFalse(BuildEnvironmentUtil.hasDockerfileAction(buildEnvironment))
    }

    @Test
    void testHasDockerfileActionWithNonMapActionConfig() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: [
                build: 'not-a-map',
            ],
        ]

        assertFalse(BuildEnvironmentUtil.hasDockerfileAction(buildEnvironment))
    }

    @Test
    void testHasDockerfileActionWithEmptyActions() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: [:],
        ]

        assertFalse(BuildEnvironmentUtil.hasDockerfileAction(buildEnvironment))
    }

    @Test
    void testGetEffectiveToolWithGradle() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: [
                build: [task: 'build'],
            ],
        ]

        assertEquals('gradle', BuildEnvironmentUtil.getEffectiveTool(buildEnvironment))
    }

    @Test
    void testGetEffectiveToolWithGradleAndDockerfile() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: [
                build: [
                    task: 'build',
                    dockerfilepath: 'Dockerfile',
                ],
            ],
        ]

        assertEquals('gradledocker', BuildEnvironmentUtil.getEffectiveTool(buildEnvironment))
    }

    @Test
    void testGetEffectiveToolWithGradleUppercase() {
        def buildEnvironment = [
            tool: 'GRADLE',
            actions: [
                build: [
                    task: 'build',
                    dockerfilepath: 'Dockerfile',
                ],
            ],
        ]

        assertEquals('gradledocker', BuildEnvironmentUtil.getEffectiveTool(buildEnvironment))
    }

    @Test
    void testGetEffectiveToolWithPython() {
        def buildEnvironment = [
            tool: 'python',
            actions: [
                build: [task: 'build'],
            ],
        ]

        assertEquals('python', BuildEnvironmentUtil.getEffectiveTool(buildEnvironment))
    }

    @Test
    void testGetEffectiveToolWithPythonAndDockerfile() {
        def buildEnvironment = [
            tool: 'python',
            actions: [
                build: [
                    task: 'build',
                    dockerfilepath: 'Dockerfile',
                ],
            ],
        ]

        // Python with dockerfile should remain python (not gradledocker)
        assertEquals('python', BuildEnvironmentUtil.getEffectiveTool(buildEnvironment))
    }

    @Test
    void testGetEffectiveToolWithMaven() {
        def buildEnvironment = [
            tool: 'maven',
            actions: [
                build: [task: 'build'],
            ],
        ]

        assertEquals('maven', BuildEnvironmentUtil.getEffectiveTool(buildEnvironment))
    }

    @Test
    void testGetEffectiveToolWithCustomTool() {
        def buildEnvironment = [
            tool: 'customTool',
            actions: [
                build: [task: 'build'],
            ],
        ]

        assertEquals('customtool', BuildEnvironmentUtil.getEffectiveTool(buildEnvironment))
    }

    @Test
    void testGetEffectiveToolCaseInsensitive() {
        def buildEnvironment = [
            tool: 'GrAdLe',
            actions: [
                build: [
                    task: 'build',
                    dockerfilepath: 'Dockerfile',
                ],
            ],
        ]

        assertEquals('gradledocker', BuildEnvironmentUtil.getEffectiveTool(buildEnvironment))
    }

    @Test
    void testGetEffectiveToolWithIntegerTool() {
        def buildEnvironment = [
            tool: 123,
            actions: [
                build: [task: 'build'],
            ],
        ]

        assertEquals('123', BuildEnvironmentUtil.getEffectiveTool(buildEnvironment))
    }

    @Test
    void testGetEffectiveToolWithComplexDockerfileAction() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: [
                build: [task: 'build'],
                publish: [
                    task: 'publish',
                    dockerfilepath: 'docker/production/Dockerfile',
                    additionalParam: 'value',
                ],
                test: [task: 'test'],
            ],
        ]

        assertEquals('gradledocker', BuildEnvironmentUtil.getEffectiveTool(buildEnvironment))
    }

    @Test
    void testGetEffectiveToolWithMultipleDockerfileActions() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: [
                build: [
                    task: 'build',
                    dockerfilepath: 'Dockerfile.build',
                ],
                publish: [
                    task: 'publish',
                    dockerfilepath: 'Dockerfile.publish',
                ],
            ],
        ]

        assertEquals('gradledocker', BuildEnvironmentUtil.getEffectiveTool(buildEnvironment))
    }

    @Test
    void testGetEffectiveToolWithDockerfilePathContainingSpaces() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: [
                build: [
                    task: 'build',
                    dockerfilepath: 'docker files/Dockerfile',
                ],
            ],
        ]

        assertEquals('gradledocker', BuildEnvironmentUtil.getEffectiveTool(buildEnvironment))
    }

    @Test
    void testHasDockerfileActionWithNestedActions() {
        def buildEnvironment = [
            tool: 'gradle',
            actions: [
                build: [
                    task: 'build',
                    nested: [
                        dockerfilepath: 'Dockerfile',
                    ],
                ],
            ],
        ]

        // Should not find nested dockerfilepath
        assertFalse(BuildEnvironmentUtil.hasDockerfileAction(buildEnvironment))
    }

    @Test
    void testGetEffectiveToolEdgeCases() {
        // Test with null tool - it converts to string "null"
        def buildEnvironmentWithNullTool = [
            tool: null,
            actions: [
                build: [task: 'build'],
            ],
        ]

        // This should work - null.toString() returns "null"
        assertEquals('null', BuildEnvironmentUtil.getEffectiveTool(buildEnvironmentWithNullTool))
    }
}
