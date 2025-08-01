package pipelines.helpers


class PipelineTestHelperInterceptor implements Interceptor {
    boolean invokeMethod = true

    boolean doInvoke() {
        invokeMethod
    }

    Object beforeInvoke(Object obj, String name, Object[] args) {
        // We don't want the shout() method to be executed.
        if (name == 'cloneArgs') {
            invokeMethod = false
        }
    }

    Object afterInvoke(Object obj, String name, Object[] args, Object result) {
        if (name == 'cloneArgs') {
            invokeMethod = true

            List argsCloned = []
            args[0].each {
                switch(it.getClass().getName()) {
                    case "Jenkinsfile":
                    case "com.lesfurets.jenkins.unit.LibClassLoader":
                        argsCloned << it.getClass().getName()
                        break
                    default:
                        try {
                            // Try the clone
                            argsCloned << it?.clone()
                        }
                        catch(e) {
                            // Cannot clone it, get a string representation at this point.
                            argsCloned << it.toString()
                        }
                }
            }

            return argsCloned as Object[]
        }
        result
    }
}
