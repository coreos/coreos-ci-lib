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
