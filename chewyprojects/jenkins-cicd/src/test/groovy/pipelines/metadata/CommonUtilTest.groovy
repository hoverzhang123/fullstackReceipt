package pipelines.metadata

import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.assertEquals

class CommonUtilTest {

    def mockJenkins = [
        ACCOUNTS: [
            'shd': '123456789012'
        ]
    ]

    @Test
    void testGetImageBaseUrlWithEcrRepo() {
        def ecrRepo = "/my-repo"
        def vertical = "my-vertical"
        def projectName = "my-project"

        def result = CommonUtil.getImageBaseUrl(mockJenkins, ecrRepo, vertical, projectName)
        assertEquals("123456789012.dkr.ecr.us-east-1.amazonaws.com/my-repo", result)
    }

    @Test
    void testGetImageBaseUrlWithEcrRepoNoSlash() {
        def ecrRepo = "my-repo"
        def vertical = "my-vertical"
        def projectName = "my-project"

        def result = CommonUtil.getImageBaseUrl(mockJenkins, ecrRepo, vertical, projectName)
        assertEquals("123456789012.dkr.ecr.us-east-1.amazonaws.com/my-repo", result)
    }

    @Test
    void testGetImageBaseUrlWithoutEcrRepo() {
        def vertical = "my-vertical"
        def projectName = "my-project"

        def result = CommonUtil.getImageBaseUrl(mockJenkins, null, vertical, projectName)
        assertEquals("123456789012.dkr.ecr.us-east-1.amazonaws.com/my-vertical/my-project", result)
    }

    @Test
    void testGetImageBaseUrlWithDifferentRegion() {
        def vertical = "my-vertical"
        def projectName = "my-project"
        def region = "us-west-2"

        def result = CommonUtil.getImageBaseUrl(mockJenkins, null, vertical, projectName, region)
        assertEquals("123456789012.dkr.ecr.us-west-2.amazonaws.com/my-vertical/my-project", result)
    }

    @Test
    void testGetImageBaseUrlWithEcrRepoAndDifferentRegion() {
        def ecrRepo = "my-repo"
        def vertical = "my-vertical"
        def projectName = "my-project"
        def region = "us-west-2"

        def result = CommonUtil.getImageBaseUrl(mockJenkins, ecrRepo, vertical, projectName, region)
        assertEquals("123456789012.dkr.ecr.us-west-2.amazonaws.com/my-repo", result)
    }
}
