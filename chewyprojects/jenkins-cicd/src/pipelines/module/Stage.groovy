package pipelines.module

import groovy.transform.builder.Builder

class Stage extends AbstractStage {
    /**
     * Note to self: Do not try use the parameters in an implementation.
     * Call super() without args and use setRunner()/setShouldRun() instead.
     *
     * Trying to use this.& method references in a constructor causes
     *     org.jenkinsci.plugins.workflow.actions.ErrorAction$ErrorId:
     *     java.lang.VerifyError: ... Expecting to find object/array on stack
     *
     * @param runner
     * @param shouldRun
     */
    @Builder
    Stage(Closure<?> runner = null, Closure<Boolean> shouldRun = null) {
        this.runner = runner
        this.shouldRun = shouldRun
    }

    @Override
    protected void initializeStage() {
        // Do nothing since the constructor already applied the Closure parameters
    }
}
