package pipelines.helpers

import hudson.model.ParametersAction
import hudson.model.Result
import hudson.model.StringParameterValue

class CurrentBuildHelper {
    static String tag = '1.2.3'
    String result = Result.SUCCESS.toString()
    String currentResult = Result.SUCCESS.toString()
    String displayName
    String number
    def previousBuild
    def actions = [
        new ParametersAction(new StringParameterValue('TAG', tag))
    ]

    CurrentBuildHelper(String newTag = '1.2.3') {
        tag = newTag
        actions = [
            new ParametersAction(new StringParameterValue('TAG', tag))
        ]
    }
    static def getRawBuild() {
        return new CurrentBuildHelper(tag)
    }
    static def getPreviousSuccessfulBuild() {
        return new CurrentBuildHelper(tag)
    }
}
