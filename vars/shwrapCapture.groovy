def call(cmds) {
    return sh(returnStdout: true, script: """
        set -euo pipefail
        ${cmds}
    """).trim()
}
