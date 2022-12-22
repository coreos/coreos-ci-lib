import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper
import hudson.model.Cause.UpstreamCause;

def cosaCmd(params = [:]) {
// Available parameters:
//     cosaDir:        string  -- cosa working directory
//     args:           string  -- arguments to pass to `cosa`
//     user:           string  -- user to run the cosa commands 
    def cosaDir = getCosaDir(params)
    def args = params.get('args', "");

    if(!params['user']) {
        shwrap("cd ${cosaDir} && cosa ${args}")
    } else {
        shwrap("cd ${cosaDir} && sudo -u ${params['user']} cosa ${args}")
    }
}

def getCosaDir(params = [:]) {
    if (params['cosaDir']) {
        return params['cosaDir']
    } else {
        return "/srv/coreos"
    }
}

// This is like fileExists, but actually works inside the Kubernetes container.
def pathExists(path) {
    return shwrapRc("test -e ${path}") == 0
}

// Thin wrapper around SimpleTemplateEngine().
//     tmpl_str    string  -- templated string
//     bindings    map     -- variables to fill in
def substituteStr(tmpl_str, bindings) {
    def engine = new groovy.text.SimpleTemplateEngine()
    def tmpl = engine.createTemplate(tmpl_str).make(bindings)
    return tmpl.toString()
}

// Run closures in provided map in parallel. Run at most `max`
// parallel runs at a given time.
def runParallel(parallelruns, max=0) {
    if (max == 0) {
        parallel parallelruns
    } else {
        def runs = [:]
        parallelruns.eachWithIndex { key, value, index ->
            def i = index + 1 // index starts at 0, adjust
            runs[key] = value
            if (i % max == 0 || i == parallelruns.size()) {
                parallel runs
                runs = [:] // empty out map for next iteration
            }
        }
    }
}

// Returns true if a credentials spec exists
def credentialsExist(creds) {
    def exists = false
    tryWithCredentials(creds, { exists = true })
    return exists
}

// Sync secrets at the path described by the given list of environment
// variables to the remote host if we are in a COSA remote session.
def syncCredentialsIfInRemoteSession(envvars) {
    for (envvar in envvars) {
        shwrap("""
        if [ -n "\${COREOS_ASSEMBLER_REMOTE_SESSION:-}" ]; then
            # If the value of the environment variable is a path to
            # a file then we'll create the directory the file is in
            # and then sync the file. If it is a path to a directory
            # then we create the directory and sync it.
            if [ -f \${${envvar}} ]; then
                dir=\$(dirname \${${envvar}})
                file=\$(basename \${${envvar}})
            else
                dir=\${${envvar}}
                file=''
            fi
            cosa shell -- sudo install -d -D -o builder -g builder --mode 777 \${dir}
            cosa remote-session sync \${dir}/\${file} :\${dir}/\${file}
            fi
        """)
    }
}

// Like `build` but allows retrieving information on the triggered build
// without waiting for it. Together with `waitBuild`, this is more flexible
// than a long-running `parallel` branch and doesn't mess up the BlueOcean
// view. Supported parameters:
//   job         string  (required) name of the job to build
//   parameters  map     parameters
def buildAsync(params) {
    def job = Jenkins.instance.getItemByFullName(params.job)
    def actions = [new CauseAction(new UpstreamCause(currentBuild.getRawBuild()))]
    if (params.parameters) {
        def v = params.parameters.collect{key, val -> new StringParameterValue(key, val.toString())}
        actions.add(new ParametersAction(v))
    }
    println("Scheduling project: ${params.job}")
    def queue = job.scheduleBuild2(0, actions.toArray(new Action[0]));
    if (queue == null) {
        error("Failed to schedule build for ${params.job}")
    }
    def r = new RunWrapper(queue.waitForStart(), false)
    println("Started build: ${params.job} #${r.number} (${r.absoluteUrl})")
    return r
}

def waitBuild(build, checkResult=true) {
    def r
    waitUntil(quiet: true, initialRecurrencePeriod: 1000) {
        r = build.getResult()
        return r != null
    }
    if (checkResult) {
        if (r != "SUCCESS") {
            error("Build ${build.projectName} #${build.number} finished with result ${r}")
        }
    }
    return r
}
