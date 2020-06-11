def call(cmds) {
    // default is HOME=/ which normally we don't have access to
    withEnv(["HOME=${env.WORKSPACE}"]) {
        sh """
            set -xeuo pipefail
            ${cmds}
        """
    }
}
