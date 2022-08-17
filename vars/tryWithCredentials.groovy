import org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException

// Runs closure if credentials exist, otherwise gracefully return.
def call(creds, Closure body) {
    try {
        withCredentials(creds) {
            body()
        }
    } catch (CredentialNotFoundException e) {
        echo("${e.getMessage()}: skipping")
    }
}
