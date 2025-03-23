// Run podman remote context using the
// environment variables CONTAINER_SSHKEY and CONTAINER_HOST
// Available parameters:
//    remoteHost:    string -- Credential ID containing the remote host
//    sshKey:        string -- Credential ID containing the private SSH key
def call(params = [:], Closure body) {
    withCredentials([
        string(credentialsId: params['remoteHost'],
               variable: 'REMOTEHOST'),
        sshUserPrivateKey(credentialsId: params['sshKey'],
                          usernameVariable: 'REMOTEUSER',
                          keyFileVariable: 'CONTAINER_SSHKEY')
    ]) {
        withEnv(["CONTAINER_HOST=ssh://${REMOTEUSER}@${REMOTEHOST}"]) {
            shwrap("""
            # workaround bug: https://github.com/jenkinsci/configuration-as-code-plugin/issues/1646
            sed -i s/^----BEGIN/-----BEGIN/ \$CONTAINER_SSHKEY
            """)
            body()
        }
    }
}
