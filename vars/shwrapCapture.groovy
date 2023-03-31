def call(cmds) {
    // default is HOME=/ which normally we don't have access to.
    // Also if umask is somehow unset, fix it.
    withEnv(["HOME=${env.WORKSPACE}"]) {
        return sh(returnStdout: true, script: """
            set -euo pipefail
            if [ `umask` = 0000 ]; then
              umask 0022
            fi
            ${cmds}
        """).trim()
    }
}
