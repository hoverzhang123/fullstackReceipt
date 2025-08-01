package pipelines.module

/**
 * Note to self: Do not try to add Stages as constructor params.
 *
 * Trying to use this.& method references in a constructor causes
 *     org.jenkinsci.plugins.workflow.actions.ErrorAction$ErrorId:
 *     java.lang.VerifyError: ... Expecting to find object/array on stack
 *
 */
abstract class Module {
    protected Boolean needsInitialization = true
    protected ModuleProps props
    protected AbstractStage build = new EmptyStage()
    protected AbstractStage validate = new EmptyStage()
    protected AbstractStage publish = new EmptyStage()
    protected AbstractStage preCheck = new EmptyStage()
    protected AbstractStage globalDeploy = new EmptyStage()
    protected AbstractStage regionDeploy = new EmptyStage()
    protected AbstractStage postCheck = new EmptyStage()
    protected AbstractStage regionPostDeploy = new EmptyStage()
    protected AbstractStage globalRollback = new EmptyStage()
    protected AbstractStage regionRollback = new EmptyStage()

    Module() {
        // No-arg constructor: required by Groovy CPS sandbox
    }

    Module(props) {
        // To avoid CPS issues do not call any methods in a constructor
        this.props = new ModuleProps(props)
    }

    AbstractStage getBuild() {
        checkInitialization()
        return build
    }

    AbstractStage getValidate() {
        checkInitialization()
        return validate
    }

    AbstractStage getPublish() {
        checkInitialization()
        return publish
    }

    AbstractStage getPreCheck() {
        checkInitialization()
        return preCheck
    }

    AbstractStage getGlobalDeploy() {
        checkInitialization()
        return globalDeploy
    }

    AbstractStage getRegionDeploy() {
        checkInitialization()
        return regionDeploy
    }

    AbstractStage getPostCheck() {
        checkInitialization()
        return postCheck
    }

    AbstractStage getRegionPostDeploy() {
        checkInitialization()
        return regionPostDeploy
    }

    AbstractStage getGlobalRollback() {
        checkInitialization()
        return globalRollback
    }

    AbstractStage getRegionRollback() {
        checkInitialization()
        return regionRollback
    }

    @Override
    String toString() {
        return name ? moduleName ? "${name} (${moduleName})" : name : moduleName
    }

    String getName() {
        return props.getName()
    }

    String getModuleName() {
        return props.getModuleName()
    }

    protected abstract void initializeStages()

    protected void checkInitialization() {
        if (needsInitialization) {
            initializeStages()
            needsInitialization = false
            initModulePropsOnStages()
        }
    }

    @SuppressWarnings('CatchRuntimeException')
    protected void initModulePropsOnStages() {
        for (def prop : this.getMetaPropertyValues()) {
            try {
                if (prop.getValue() in AbstractStage) {
                    def abstractStage = prop.getValue() as AbstractStage
                    abstractStage.setModuleProps(props)
                }
            } catch (RuntimeException ignored) {
                // Ignore runtime exception: Cannot read write-only property
            }
        }
    }

    protected class EmptyStage extends AbstractStage {
        @Override
        protected void initializeStage() {
        }
    }
}
