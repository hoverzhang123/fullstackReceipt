@Library('jenkins-cicd') _

pipeline {
    agent any
    stages {
        stage('Test Semver Parsing') {
            steps {
                script {

                    def testCases = [
                        '1.0.0',
                        '2.1.3',
                        '10.20.30',
                        '1.0.0-alpha',
                        '1.0.0-alpha.1',
                        '1.0.0-alpha.beta',
                        '1.0.0-alpha.1+build.1',
                        '2.0.0+build.1848',
                        '1.0.0-rc.1+build.123',
                        '1.2.3-beta'
                    ]
                    
                    testCases.each { version ->
                        def parsed = semver.parse(version)
                        assert parsed != null : "Failed to parse valid semver: ${version}"
                        assert parsed.version == version : "Parsed version does not match original: ${version}"
                    }
                    
                    // Test parseRequired with valid input
                    def requiredParsed = semver.parseRequired('1.2.3')
                    assert requiredParsed != null : "parseRequired failed for valid input"
                    assert requiredParsed.version == '1.2.3' : "parseRequired returned incorrect version"
                    
                    // Test parse with invalid input (should return null)
                    def invalidParsed = semver.parse('invalid.version')
                    assert invalidParsed == null : "parse should return null for invalid input"
                }
            }
        }
        
        stage('Test Semver Comparison') {
            steps {
                script {
                    // Test basic comparisons
                    assert semver.compare('1.0.0', '2.0.0') < 0 : "1.0.0 should be less than 2.0.0"
                    assert semver.compare('2.0.0', '1.0.0') > 0 : "2.0.0 should be greater than 1.0.0"
                    assert semver.compare('1.0.0', '1.0.0') == 0 : "1.0.0 should equal 1.0.0"
                    
                    // Test minor version comparisons
                    assert semver.compare('1.1.0', '1.2.0') < 0 : "1.1.0 should be less than 1.2.0"
                    assert semver.compare('1.2.0', '1.1.0') > 0 : "1.2.0 should be greater than 1.1.0"
                    
                    // Test patch version comparisons
                    assert semver.compare('1.0.1', '1.0.2') < 0 : "1.0.1 should be less than 1.0.2"
                    assert semver.compare('1.0.2', '1.0.1') > 0 : "1.0.2 should be greater than 1.0.1"
                    
                    // Test prerelease comparisons
                    assert semver.compare('1.0.0-alpha', '1.0.0') < 0 : "1.0.0-alpha should be less than 1.0.0"
                    assert semver.compare('1.0.0-alpha.1', '1.0.0-alpha.2') < 0 : "1.0.0-alpha.1 should be less than 1.0.0-alpha.2"
                }
            }
        }
        
        stage('Test Version Bumping') {
            steps {
                script {
                    // Test major version bumping
                    assert semver.bumpMajor('1.2.3') == '2.0.0' : "Major bump of 1.2.3 should be 2.0.0"
                    assert semver.bumpMajor('0.1.0') == '1.0.0' : "Major bump of 0.1.0 should be 1.0.0"
                    
                    // Test minor version bumping
                    assert semver.bumpMinor('1.2.3') == '1.3.0' : "Minor bump of 1.2.3 should be 1.3.0"
                    assert semver.bumpMinor('1.0.0') == '1.1.0' : "Minor bump of 1.0.0 should be 1.1.0"
                    
                    // Test patch version bumping
                    assert semver.bumpPatch('1.2.3') == '1.2.4' : "Patch bump of 1.2.3 should be 1.2.4"
                    assert semver.bumpPatch('1.0.0') == '1.0.1' : "Patch bump of 1.0.0 should be 1.0.1"
                    
                    // Test prerelease version bumping (only for versions with numeric prerelease)
                    assert semver.bumpPreRelease('1.0.0-alpha.1') == '1.0.0-alpha.2' : "PreRelease bump of 1.0.0-alpha.1 should be 1.0.0-alpha.2"
                    assert semver.bumpPreRelease('1.0.0-rc.1') == '1.0.0-rc.2' : "PreRelease bump of 1.0.0-rc.1 should be 1.0.0-rc.2"
                }
            }
        }
        
        stage('Test Version Bumping with Metadata') {
            steps {
                script {
                    
                    // Test bumping versions that have build metadata
                    def versionWithMeta = '1.0.0+build.123'
                    assert semver.bumpMajor(versionWithMeta) == '2.0.0+build.123' : "Major bump should preserve build metadata"
                    assert semver.bumpMinor(versionWithMeta) == '1.1.0+build.123' : "Minor bump should preserve build metadata"
                    assert semver.bumpPatch(versionWithMeta) == '1.0.1+build.123' : "Patch bump should preserve build metadata"
                    
                    // Test bumping versions with prerelease and metadata
                    def prereleaseWithMeta = '1.0.0-alpha.1+build.456'
                    assert semver.bumpPreRelease(prereleaseWithMeta) == '1.0.0-alpha.2+build.456' : "PreRelease bump should preserve build metadata"
                }
            }
        }
    }
}
