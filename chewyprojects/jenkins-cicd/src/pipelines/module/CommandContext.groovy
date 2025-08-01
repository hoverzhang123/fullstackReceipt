package pipelines.module

import groovy.transform.builder.Builder

@Builder
class CommandContext {
    String action
    ModuleProps props
}
