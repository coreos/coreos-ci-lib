// Thin wrapper around pod() which auto-selects images and kvm.
// Additional available parameters:
//   image: string
def call(params = [:], Closure body) {
    if (params['image'] == null) {
        params['image'] = 'quay.io/coreos-assembler/coreos-assembler:latest'
    }

    // default to enabling KVM
    if (params['kvm'] == null) {
        params['kvm'] = true
    }

    // we don't like zombies
    params['cmd'] = ["/usr/bin/dumb-init", "/usr/bin/sleep", "infinity"]

    pod(params) {
        shwrap("cat /cosa/coreos-assembler-git.json || :")
        body()
    }
}
