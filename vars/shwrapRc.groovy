def call(cmds) {
    withEnv(["HOME=${env.WORKSPACE}"]) {
        return sh(returnStatus: true, script: """
            set -xeuo pipefail
            ${cmds}
        """)
    }
}
