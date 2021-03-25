// buildPod is a thin wrapper around pod() which fills in the FCOS buildroot images.
// Additional available parameters:
//   image: string
//   buildroot: bool
def call(params = [:], Closure body) {
    def stream = params.get('stream', 'testing-devel');
    params['image'] = "quay.io/coreos-assembler/fcos-buildroot:${stream}".toString()

    pod(params) {
        body()
    }
}
