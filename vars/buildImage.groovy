// Builds a container image and returns its name.
// Available parameters:
//    dockerFile    string -- DockerFile used for the buildconfig
//    workspace     string -- Path for local source dir used for `--from-dir=`
//    env           dict -- Additional environment variables to set during build
def call(params = [:]) {
    def imageName
    def bcObj

    if (!params['env']) {
        params['env'] = []
    }

    openshift.withCluster() {
        openshift.withProject() {
            stage('CleanUp') {
            // Once we have the TTL controller enabled we can remove this stage
            // More: https://kubernetes.io/docs/concepts/workloads/controllers/ttlafterfinished/
                def jobs = openshift.selector("jobs").names()
                for (job in jobs) {
                    if (job.startsWith('job/coreos-ci')) {
                        def obj = openshift.selector(job).object()
                        if (obj.status.completionTime) {
                            openshift.selector(job).delete()
                        }
                    }
                }
            }
            stage('Prepare Env') {
                // TODO: Change buidlconfig to yaml and to use openshift.process
                // Issue described in:
                // https://github.com/coreos/coreos-ci-lib/pull/61#discussion_r569401940
                def bcJSON  = libraryResource 'com/github/coreos/buildconfig.json'
                def jobYaml = libraryResource 'com/github/coreos/job.yaml'
                def jobObj  = readYaml text: jobYaml
                bcObj       = readJSON text: bcJSON

                def UUID    = UUID.randomUUID()
                def scmVars = checkout scm
                def repo    = (scmVars.GIT_URL.substring(scmVars.GIT_URL.lastIndexOf('/') + 1)).replace('.git','')

                jobObj      = openshift.process(jobObj, "-p", "NAME=coreos-ci-$repo-${UUID}")
                def app     = openshift.create(jobObj)
                def job     = app.narrow('job')
                def jobMap  = job.object()
                def uid     = jobMap.metadata.get('uid')

                imageName = "coreos-ci-$repo-${UUID}"

                //Create and unique name for image and bc
                bcObj['parameters'][0] +=['name': 'NAME', 'value': "coreos-ci-$repo-${UUID}".toString()]

                //Add OwnerReference for cascading deletion with the Job timeout
                bcObj['parameters'][3] +=['name': 'UID', 'value': "${uid}".toString()]
                bcObj['parameters'][2] +=['name': 'OWNERNAME', 'value': "coreos-ci-$repo-${UUID}".toString()]
                if (params['dockerFile']) {
                    bcObj['parameters'][1] +=['name': 'DOCKERFILE', 'value': "${params['dockerFile']}".toString()]
                }
                params['env'].each{ name, val ->
                    // should check 'kind' instead
                    bcObj['objects'][1]['spec']['strategy']['dockerStrategy']['env'] += ['name': name, 'value': val]
                }
            }
            stage('Create BuildConfig') {
                openshift.create(openshift.process(bcObj))
            }
            stage('Build') {
                def workspace = params.get('workspace', ".");
                shwrap("tar --exclude=./pod* -zcf /tmp/dir.tar ${workspace}")
                def build = openshift.selector('bc', imageName).startBuild("--from-archive=/tmp/dir.tar")

                // Showing logs in Jenkins is also a way
                // to wait for the build to finsih
                build.logs('-f')
                // Wait up to 2h for the build to finish
                timeout(60*2) {
                    build.untilEach(1) {
                        def phase = it.object().status.phase
                        if (phase in ["New", "Pending", "Running"]) {
                            return false
                        }
                        if (phase != "Complete") {
                            error("Error build image: status phase ${phase}")
                        }
                        return true
                    }
                }
           }
        }
    }
    return imageName.toString()
}
