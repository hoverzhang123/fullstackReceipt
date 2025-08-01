import org.codehaus.groovy.control.customizers.ImportCustomizer

def importCustomizer = new ImportCustomizer()
importCustomizer.addImports('com.cloudbees.groovy.cps.NonCPS')
configuration.addCompilationCustomizers(importCustomizer)
