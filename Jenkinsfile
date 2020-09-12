// While this isn't a plugin, it is much simpler to reuse the pipeline code for CI
// allowing easy windows / linux testing and producing incrementals
// the only feature that buildPlugin has that relates to plugins is allowing you to test against multiple jenkins versions
buildPlugin(configurations: [
        [ platform: "linux", jdk: "8", jenkins: null ],
        [ platform: "linux", jdk: "11", jenkins: null, javaLevel: "8" ],
        [ platform: "windows", jdk: "11", jenkins: null, javaLevel: "8" ]
    ])
