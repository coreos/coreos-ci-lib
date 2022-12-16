// Emits a warning and sets the build status as unstable. This is better than
// just manually echoing and setting the build status, because the caught error
// will better stand out in the BlueOcean view and logs.
def call(msg) {
    warnError(msg) {
        error(msg)
    }
}
