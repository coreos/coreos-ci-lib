def call(cmds) {
    // If umask is somehow unset, fix it.
    return sh(returnStdout: true, script: """
        set -euo pipefail
        if [ `umask` = 0000 ]; then
          umask 0022
        fi
        ${cmds}
    """).trim()
}
