// Run the passed body in a pod respecting some parameters.
// Available parameters:
//   image: string
//   privileged: bool
//   kvm: bool
def pod(params, body) {
    def podJSON = libraryResource 'com/github/coreos/pod.json'
    def podObj = readJSON text: podJSON
    podObj['spec']['containers'][1]['image'] = params['image']

    if (params['privileged']) {
        // XXX: tmp hack to get anyuid SCC; need to ask to get jenkins SA added
        podObj['spec']['serviceAccountName'] = "papr"
        podObj['spec']['containers'][1]['securityContext'] = [runAsUser: 0]
    }

    if (params['kvm']) {
        podObj['spec']['nodeSelector'] = [oci_kvm_hook: "allowed"]
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
