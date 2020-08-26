// Run the passed body in a pod respecting some parameters.
// Available parameters:
//   image: string
//   kvm: bool
//   runAsUser: int
//   memory: amount of RAM to request
//   cpu: amount of CPU to request
//   emptyDirs: []string
//   cmd: []string
//   secrets: []string
def call(params = [:], Closure body) {
    def podJSON = libraryResource 'com/github/coreos/pod.json'
    def podObj = readJSON text: podJSON
    podObj['spec']['containers'][0]['image'] = params['image']

    if (params['runAsUser'] != null) {
        podObj['spec']['containers'][0]['securityContext'] = [runAsUser: params['runAsUser']]
    }

    podObj['spec']['containers'][0]['resources'] = [requests: [:], limits: [:]]
    if (params['memory']) {
        podObj['spec']['containers'][0]['resources']['requests']['memory'] = params['memory'].toString()
    }
    if (params['cpu']) {
        podObj['spec']['containers'][0]['resources']['requests']['cpu'] = params['cpu'].toString()
    }
    if (params['kvm']) {
        podObj['spec']['containers'][0]['resources']['requests']['devices.kubevirt.io/kvm'] = "1"
        podObj['spec']['containers'][0]['resources']['limits']['devices.kubevirt.io/kvm'] = "1"
    }

    if (!params['emptyDirs']) {
        params['emptyDirs'] = []
    }

    if (!params['secrets']) {
        params['secrets'] = []
    }

    if (params['cmd']) {
        podObj['spec']['containers'][0]['command'] = params['cmd']
    }

    // for now, we always mount an emptyDir on /srv
    params['emptyDirs'] += '/srv/'

    podObj['spec']['volumes'] = []
    podObj['spec']['containers'][0]['volumeMounts'] = []
    params['emptyDirs'].eachWithIndex { mountPath, i ->
        podObj['spec']['volumes'] += ['name': "emptydir-${i}".toString(), 'emptyDir': [:]]
        podObj['spec']['containers'][0]['volumeMounts'] += ['name': "emptydir-${i}".toString(), 'mountPath': mountPath]
    }

    params['secrets'].eachWithIndex { secret, i ->
        podObj['spec']['volumes'] += ['name': "secret-${i}".toString(), 'secret': [secretName: secret]]
        podObj['spec']['containers'][0]['volumeMounts'] += ['name': "secret-${i}".toString(), 'mountPath': "/run/kubernetes/secrets/${secret}".toString()]
    }

    // XXX: look into converting to a YAML string instead
    def label = "pod-${UUID.randomUUID().toString()}"
    def podYAML
    node {
        writeYaml(file: "${label}.yaml", data: podObj)
        podYAML = readFile(file: "${label}.yaml")
    }

    podTemplate(cloud: 'openshift', yaml: podYAML, label: label, slaveConnectTimeout: 300) {
        node(label) { container('worker') {
            body()
        }}
    }
}
