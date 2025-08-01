package pipelines.module


import pipelines.PipelineConfig

class ModuleFactory {
    static Module createModule(PipelineConfig pipelineConfig, Map props) {
        if (props.type instanceof Class) {
            return (props.type as Class)
                    .getConstructor(PipelineConfig.class, Object.class)
                    .newInstance(pipelineConfig, props) as Module
        }

        try {
            return ModuleFactory.class.classLoader
                    .loadClass(getBinaryClassName(props.type))
                    .getConstructor(PipelineConfig.class, Object.class)
                    .newInstance(pipelineConfig, props) as Module
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Falling back to finding by name
        }

        String name = props.name

        //TODO: Add compliant constructors to these modules
        switch (props.type) {
            case 'App':
                return new DockerModule(name, 'DockerModule', props.ecrRepo as String)
            case 'AptApp':
                return new DockerModule(name, 'AptDockerModule', props.ecrRepo as String)
            case 'Docker':
                return new DockerModule(name, props.moduleName as String, props.ecrRepo as String)
            case 'ChewyCommons':
                return new ChewyCommonsModule(pipelineConfig, props)
            case 'Flyway':
                return new FlywayModule(name, props.ecrRepo as String)
            case 'Helm':
                return new HelmModule(name)
            case 'Library':
                def names = props.names as List ?: [name]
                return new LibraryModule(names)
            case 'React':
                return new ReactModule(name)
            case 'Spec':
                return new SpecModule(name)
            case 'Terraform':
                return new TerraformModule(name, props.ecrRepo as String)
        }
        throw new IllegalArgumentException("Unable to load module for ${props}")
    }

    static String getBinaryClassName(moduleType) {
        return "pipelines.module.${getQualifiedModuleName(moduleType)}Module"
    }

    static String getQualifiedModuleName(moduleType) {
        switch (moduleType) {
            case 'Datadog':
                return 'DatadogMonitor'
            case 'Dynatrace':
                return 'DynatraceMonitor'
            case 'Make':
            /* fallthrough */
            case 'Makefile':
                return 'MakefileProject'
        }
        return moduleType
    }
}
