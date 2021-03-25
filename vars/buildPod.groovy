// Thin wrapper around pod() which fills in the FCOS buildroot images.
// Additional available parameters:
//   image: string
//   buildroot: bool
def call(params = [:], Closure body) {
    if (params['stream'] == null) {
        params['stream'] = 'testing-devel'
    }

    params['image'] = "quay.io/coreos-assembler/fcos-buildroot:${params['stream']}"

    pod(params) {
        body()
    }
}
