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
