# Abstract Tool Commands

This directory contains abstract command implementations for different build and validation tools.

## Architecture
- `Command.groovy` - Common interface that both build and validate commands implement
- `CommandContext.groovy` - Context object containing action and module properties (missing from current README)

### Build Commands
- `Command.groovy` - Interface for build commands
- `BuildCommandFactory.groovy` - Factory to create appropriate build commands
- `GradleBuildCommand.groovy` - Uses `actions` map if provided (for modules like `SpecModule`), otherwise falls back to default gradlew method.
- `DockerxBuildCommand.groovy` - Docker buildx implementation

### Validate Commands
- `Command.groovy` - Interface for validate commands
- `ValidateCommandFactory.groovy` - Factory to create appropriate validate commands
- `GradleValidateCommand.groovy` - Gradle validation implementation
- `DockerxValidateCommand.groovy` - Docker validation implementation

### Shared Utilities
- `../metadata/BuildEnvironmentUtil.groovy` - Shared utilities to decide which tool to use
- `../metadata/GradleCommandUtil.groovy` - Shared utilities for processing complex "gradlew" commands, like docker builds.


### Build Configuration

#### Recommended: `actions`-based Configuration (New)
This is the modern, recommended way. It's flexible and clearly defines the module's capabilities. It is handled by the `actions` mode in `GradleBuildCommand`.

#### BuildModule.groovy Configuration
```groovy
def pipelines = cicd.init(
    modules: [
        [
            type: 'Build', 
            name: 'my-app',
            ecrrepo: '/my/ecr/repo',
            buildEnvironment: [
                tool: 'gradle',
                actions: [
                    build: [task: 'dockerBuildImage -x check'],
                ]
            ]
        ]
    ]
)
```

```groovy
def pipelines = cicd.init(
    modules: [
        [
            type: 'Spec',
            name: 'spec', //must provide Module name in lowercase
            buildEnvironment: [
                tool: 'gradle',
                actions: [
                    build:   [task: 'build'],
                    publish: [task: 'artifactoryPublish -x check'],
                    // Docker build method
                    // build: [dockerfilepath: 'filename']
                    
                ]
            ]
        ]
    ]
)
```
#### Gradlebuildcommand.groovy implementation.
It intelligently handles configurations (via the 'actions' map)
```
buildEnvironment: [
     tool: 'gradle',
     actions: [ 
        build:   [task: 'build'],
        publish: [task: 'artifactoryPublish -x check']
   ]
]

```


`BuildModule` will use buildEnvironment to determine the build tool and execute the appropriate command.
```groovy
def pipelines = cicd.init(
    specModule: 'spec',
    modules: [
        [
            type: 'Build', 
            name: 'my-app',
            buildEnvironment: [
                tool: 'gradle',
                actions: [
                        build: [task: 'dockerBuildImage -x check'],
                ],
           ]
        ]
    ]
)
```

### Build Strategy
- `tool: 'gradle'` + `actions: [...]` (e.g. `SpecModule`) → `GradleBuildCommand` (`actions` mode)
- `tool: 'gradle'` ( `BuildModule`) → `GradleBuildCommand` 
- `tool: 'python'` → `pythonCommand` (not implemented in this module, but can be added)

#### Validate Strategy
- `tool: 'gradle'` → `GradleValidateCommand`
- `tool: 'gradle'` + actions with 'dockerfilepath' → `DockerxValidateCommand` (docker validation)
- `tool: 'python'` → `pythonValidateCommand` (not implemented in this module, but can be added)

## Benefits

1. **Simplified Logic**: Only supports Map format `build: [dockerfilepath: 'filename']`
2. **Must Provide value**: If not provided, it will error out. Must provide "name:xx" for SpecModule.
   `buildenvironment:[tool:'gradle']`
   `modules: [[type:'Spec',name:'spec']],`


## Execution Order

The pipeline executes stages in this order:
1. **BuildEnvironmentStage** → Uses `BuildCommandFactory` (validates `buildEnvironment`)
2. **ValidateEnvironmentStage** → Uses `ValidateCommandFactory` (assumes pre-validated `buildEnvironment` parameters)

