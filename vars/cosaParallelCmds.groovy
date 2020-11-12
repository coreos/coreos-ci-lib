// Run cosa commands in parallel
// Available parameters:
//    cosaDir:        string    -- cosa working directory
//    commands:     []string    -- list of commands to run
//    user:           string    -- user account to run cosa commands

def call(params = [:]) {
    def commands = params['commands'];

    parallel commands.inject([:]) { d, i -> d[i] = {
        utils.cosaCmd(cosaDir: params['cosaDir'], user: params['user'], args: "buildextend-${i.toLowerCase()}")
    }; d }
}
