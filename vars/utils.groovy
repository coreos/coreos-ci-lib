def cosaCmd(params = [:]) {
// Available parameters:
//     args:           string  -- arguments to pass to `cosa`
//     cosaDir:        string  -- cosa working directory
//     stage:          string  -- CI stage name
//     user:           string  -- user to run the cosa commands 
    def cosaDir = getCosaDir(params)
    def args = params.get('args', "");

    if (params['stage']) {
        stage(params['stage'])
    }

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
