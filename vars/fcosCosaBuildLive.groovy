// Available parameters:
//     extraArgs:      string  -- extra arguments to pass to `cosa buildextend-live`
//     cosaDir:        string  -- cosa working directory
//     user:           string  -- user account to run cosa commands
def call(params = [:]) {
    stage("Build Live Images") {
        def extraArgs = params.get('extraArgs', "");
        params['args'] =  "buildextend-live ${extraArgs}"
        utils.cosaCmd(params)
    }
}
