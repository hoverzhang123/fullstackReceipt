package module

class TestUtil {

    /**
     * Creates a mock Jenkins object for testing
     * @return Mock Jenkins object with common methods and properties
     */
    static def createMockJenkins() {
        def echoMessages = []
        return [
            echoMessages: echoMessages,
            lock: { String lockName, Closure closure ->
                closure()
            },
            steps: [
                lock: { Map args, Closure closure ->
                    closure()
                }
            ],
            ACCOUNTS: ['dev': '123456789012', 'qat': '234567890123', 'stg': '345678901234', 'prd': '456789012345'],
            REGIONS: ['us-east-1': 'use1', 'us-east-2': 'use2'],
            k8s: [
                generateNamespaceName: { return 'test-namespace' },
            ],
            env: [
                HOME: '/home/jenkins',
                ENVIRONMENT: 'dev',
                DEPLOY_STEP: '',
            ],
            sh: { args ->
                if (args instanceof Map) {
                    if (args.script.contains('which docker')) {
                        return '/usr/bin/docker'
                    }
                    if (args.script.contains('which docker-compose')) {
                        return '/usr/local/bin/docker-compose'
                    }
                    if (args.script.contains('stat -c %g')) {
                        return '999'
                    }
                    if (args.returnStdout) {
                        return args.script
                    }
                    return null
                }
                if (args instanceof String) {
                    if (args.contains('which docker')) {
                        return '/usr/bin/docker'
                    }
                    if (args.contains('which docker-compose')) {
                        return '/usr/local/bin/docker-compose'
                    }
                    if (args.contains('stat -c %g')) {
                        return '999'
                    }
                    return ''
                }
                return null
            },
            error: { message -> throw new Exception(message) },
            echo: { message -> echoMessages.add(message) },
            configFile: { Map args -> return args },
            configFileProvider: { args, closure -> closure() },
            withAWS: { args, closure -> closure() },
            withEnv: { env, closure -> closure() },
            ecrLogin: { return 'aws ecr get-login-password' },
            docker: [
                image: { image ->
                    return [
                        inside: { args, closure -> closure() },
                    ]
                },
            ],
            secretsmanager: [
                getKVSecret: { secretName, key, region -> return "secret-value-${key}" },
            ],
            withSonarQubeEnv: { env, closure ->
                closure()
            },
            junit: { pattern ->
                // Mock junit call
            },
        ]
    }
}
