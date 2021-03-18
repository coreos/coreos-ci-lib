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
        return "/srv/fcos"
    }
}

// This is like fileExists, but actually works inside the Kubernetes container.
def pathExists(path) {
    return shwrapRc("test -e ${path}") == 0
}
