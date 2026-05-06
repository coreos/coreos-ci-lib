// Thin wrapper around pod() which auto-selects images and kvm.
// Additional available parameters:
//   image: string
//   timeout: int (hours, default: 24, 0 to disable)
def call(params = [:], Closure body) {
    if (params['image'] == null) {
        params['image'] = 'quay.io/coreos-assembler/coreos-assembler:main'
    }

    // default to enabling KVM
    if (params['kvm'] == null) {
        params['kvm'] = true
    }

    def podTimeout = params.containsKey('timeout') ? params.remove('timeout') : 24

    // we don't like zombies
    params['cmd'] = ["/usr/bin/dumb-init", "/usr/bin/sleep", "infinity"]

    pod(params) {
        def runBody = {
            shwrap("(cat /cosa/coreos-assembler-git.json || :) | tee coreos-assembler-git.json")
            archiveArtifacts artifacts: "coreos-assembler-git.json"
            shwrap("rpm -qa | sort > coreos-assembler-rpmdb.txt")
            archiveArtifacts artifacts: "coreos-assembler-rpmdb.txt"
            shwrap("rm coreos-assembler-git.json coreos-assembler-rpmdb.txt")
            body()
        }
        if (podTimeout > 0) {
            timeout(time: podTimeout, unit: 'HOURS') {
                runBody()
            }
        } else {
            runBody()
        }
    }
}
