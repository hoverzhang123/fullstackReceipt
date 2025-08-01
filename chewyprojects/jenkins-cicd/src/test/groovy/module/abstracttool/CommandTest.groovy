package module.abstracttool

import org.junit.jupiter.api.Test
import pipelines.module.ModuleConfig
import pipelines.module.abstracttool.Command

import static org.junit.jupiter.api.Assertions.*

/**
 * Test class for Command interface
 * Verifies the interface structure and contract
 */
class CommandTest {

    @Test
    void testCommandInterfaceStructure() {
        // Test that Command is an interface
        assertTrue(Command.class.isInterface())

        // Test that interface has the expected methods
        def methods = Command.class.getDeclaredMethods()

        // Should have execute method
        assertTrue(methods.any { it.name == 'execute' && it.parameterCount == 2 })

        // Should have getDescription method
        assertTrue(methods.any { it.name == 'getDescription' && it.parameterCount == 0 })
    }

    @Test
    void testCommandInterfaceCanBeImplemented() {
        // Create a simple implementation to test the interface
        def testCommand = new Command() {
                    @Override
                    void execute(ModuleConfig config, String moduleName) {
                        // Test implementation
                    }

                    @Override
                    String getDescription() {
                        return "Test command"
                    }
                }

        assertNotNull(testCommand)
        assertEquals("Test command", testCommand.getDescription())

        // Should be able to call execute without throwing
        try {
            testCommand.execute(null, "test")
            // Test passed if no exception thrown
            assertTrue(true)
        } catch (Exception e) {
            fail("execute method should not throw exception: ${e.message}")
        }
    }
}
