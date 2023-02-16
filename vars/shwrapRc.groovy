def call(cmds) {
    return sh(returnStatus: true, script: """
        set -xeuo pipefail
        ${cmds}
    """)
}
