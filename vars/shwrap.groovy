def call(cmds) {
    envs = []
    // override default of HOME=/ which normally we don't have access to
    if (env.HOME == null || env.HOME == "/") {
        envs += "HOME=${env.WORKSPACE}"
    }
    withEnv(envs) {
        sh """
            set -xeuo pipefail
            ${cmds}
        """
    }
}
