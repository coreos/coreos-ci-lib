// Run command with none root
// See https://github.com/coreos/rpm-ostree/pull/4585
def call(username, cmds) {
    if (username == null) {
        error("Error: username should not be null")
    }
    // default is HOME=/ which normally we don't have access to.
    // Also if umask is somehow unset, fix it.
    withEnv(["HOME=${env.WORKSPACE}"]) {
        sh """
            set -xeuo pipefail
            if [ `umask` = 0000 ]; then
              umask 0022
            fi
            sudo -u ${username} ${cmds}
        """
    }
}
