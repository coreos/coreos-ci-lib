def call(cmds) {
    sh """
        set -xeuo pipefail
        ${cmds}
    """
}
