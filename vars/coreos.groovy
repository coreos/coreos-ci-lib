// Run the passed body in a pod respecting some parameters.
// Available parameters:
//   image: string
//   kvm: bool
//   runAsUser: int
//   privileged: bool (deprecated, equivalent to `runAsUser: 0`)
//   memory: amount of RAM to request
def pod(params, body) {
    def podJSON = libraryResource 'com/github/coreos/pod.json'
    def podObj = readJSON text: podJSON
    podObj['spec']['containers'][1]['image'] = params['image']

    if (params['privileged']) {
        // Backwards compat, see https://github.com/projectatomic/rpm-ostree/pull/1899/commits/9c1709c363e94760f0e9461719b92a7a4aca6c63#r323256575
        params['runAsUser'] = 0
    }
    if (params['runAsUser'] != null) {
        // XXX: tmp hack to get anyuid SCC; need to ask to get jenkins SA added
        podObj['spec']['serviceAccountName'] = "papr"
        podObj['spec']['containers'][1]['securityContext'] = [runAsUser: params['runAsUser']]
    }

    if (params['kvm']) {
        podObj['spec']['nodeSelector'] = [oci_kvm_hook: "allowed"]
    }

    if (params['memory']) {
        podObj['spec']['containers'][1]['resources'] = [requests: [memory: params['memory']]]
    }

    // XXX: look into converting to a YAML string instead
    def label = "pod-${UUID.randomUUID().toString()}"
    def podYAML
    node {
        writeYaml(file: "${label}.yaml", data: podObj)
        podYAML = readFile(file: "${label}.yaml")
    }

    podTemplate(cloud: 'openshift', yaml: podYAML, label: label, defaultContainer: 'jnlp') {
        node(label) { container('worker') {
            body.call()
        }}
    }
}

def shwrap(cmds) {
    sh """
        set -xeuo pipefail
        ${cmds}
    """
}
