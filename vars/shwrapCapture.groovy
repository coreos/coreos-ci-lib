def call(cmds) {
    withEnv(["HOME=${env.WORKSPACE}"]) {
        return sh(returnStdout: true, script: """
            set -euo pipefail
            ${cmds}
        """).trim()
    }
}
