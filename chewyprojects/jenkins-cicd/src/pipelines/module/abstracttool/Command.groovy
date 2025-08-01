package pipelines.module.abstracttool

import pipelines.module.ModuleConfig

/**
 * Build command interface for defining abstractions of different build tools
 * Supports gradle now
 */
interface Command {
    /**
     * Execute build command
     * @param config Module configuration
     * @param moduleName Module name
     */
    void execute(ModuleConfig config, String moduleName)

    /**
     * Get build command description
     * @return Build command description
     */
    String getDescription()
}
