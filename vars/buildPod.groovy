// buildPod is a thin wrapper around pod() which fills in the CoreOS buildroot images.
// Additional available parameters:
//   image: string
//   buildroot: bool
def call(params = [:], Closure body) {
    def stream = params.get('stream', 'testing-devel');
    params['image'] = params.get('image', "registry.ci.openshift.org/coreos/fcos-buildroot:${stream}".toString());

    // we don't like zombies
    params['cmd'] = ["/usr/bin/dumb-init", "/usr/bin/sleep", "infinity"]

    pod(params) {
        body()
    }
}
