// pod runs the passed body in a Kubernetes pod respecting some parameters.
// Available parameters:
//   image: string
//   kvm: bool
//   runAsUser: int
//   memory: amount of RAM to request
//   cpu: amount of CPU to request
//   emptyDirs: []string
//   cmd: []string
//   serviceAccount: string
//   secrets: []string
//   configMaps: []string
def call(params = [:], Closure body) {
    def podJSON = libraryResource 'com/github/coreos/pod.json'
    def podObj = readJSON text: podJSON
    podObj['spec']['containers'][0]['image'] = params['image']

    if (params['runAsUser'] != null) {
        podObj['spec']['containers'][0]['securityContext'] = [runAsUser: params['runAsUser']]
        // https://pagure.io/fedora-infra/ansible/blob/9244b4c12256/f/roles/openshift-apps/coreos-ci/defaults/main.yaml#_3
        podObj['spec']['serviceAccount'] = 'coreos-ci-sa'
    }

    if (params['serviceAccount'] != null) {
        podObj['spec']['serviceAccount'] = params['serviceAccount']
    }

    // initialize some maps/lists to make it easier to populate later on
    podObj['spec']['containers'][0]['env'] = []
    podObj['spec']['containers'][0]['resources'] = [requests: [:], limits: [:]]

    if (params['memory']) {
        podObj['spec']['containers'][0]['resources']['requests']['memory'] = params['memory'].toString()
    }
    if (params['cpu']) {
        podObj['spec']['containers'][0]['resources']['requests']['cpu'] = params['cpu'].toString()
        // Also propagate CPU count to NCPUS, because it can be hard in a Kubernetes environment
        // to determine how much CPU one should really use.
        podObj['spec']['containers'][0]['env'] += ['name': 'NCPUS', 'value': params['cpu'].toString()]
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

    if (!params['configMaps']) {
        params['configMaps'] = []
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

        def envName = secret.replace("-", "_").toUpperCase()
        podObj['spec']['containers'][0]['env'] += ['name': envName, 'value': "/run/kubernetes/secrets/${secret}".toString()]
    }

    params['configMaps'].eachWithIndex { configMap, i ->
        podObj['spec']['volumes'] += ['name': "config-${i}".toString(), 'configMap': [name: configMap]]
        podObj['spec']['containers'][0]['volumeMounts'] += ['name': "config-${i}".toString(), 'mountPath': "/run/kubernetes/configMaps/${configMap}".toString()]

        def envName = configMap.replace("-", "_").toUpperCase()
        podObj['spec']['containers'][0]['env'] += ['name': envName, 'value': "/run/kubernetes/configMaps/${configMap}".toString()]
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
