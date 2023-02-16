def call(cmds) {
    // If umask is somehow unset, fix it.
    return sh(returnStatus: true, script: """
        set -xeuo pipefail
        if [ `umask` = 0000 ]; then
          umask 0022
        fi
        ${cmds}
    """)
}
