// Thin wrapper around pod() which auto-selects images and kvm.
// Additional available parameters:
//   image: string
//   buildroot: bool
def call(params = [:], Closure body) {
    if (params['image'] == null) {
        if (params['buildroot']) {
            params['image'] = 'registry.svc.ci.openshift.org/coreos/cosa-buildroot:latest'
        } else {
            params['image'] = 'quay.io/coreos-assembler/coreos-assembler:latest'
        }
    }

    // default to enabling KVM
    if (params['kvm'] == null) {
        params['kvm'] = true
    }

    pod(params) {
        shwrap("cat /cosa/coreos-assembler-git.json || :")
        body()
    }
}
