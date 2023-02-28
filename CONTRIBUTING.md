# Contributing

## Development

Information on developing and contributing to this project.

## System Output

**Send output to stdout**. The primary output for your command should go to stdout. Anything that is machine readable 
should also go to stdout—this is where piping sends things by default.

**Send messaging to stderr**. Log messages, errors, and so on should all be sent to stderr. This means that when commands 
are piped together, these messages are displayed to the user and not fed into the next command.

For more information on basic cli best practices see [clig.dev](https://clig.dev/)

## Building and Testing

Use maven to build and test locally.
```shell
mvn clean install
```

To test changes in a Jenkins Docker image run the following changes.
```shell
# build the plugin to generate cli jar in plugin-management-cli/target
mvn clean install
# build jenkins image with updated cli jar
docker build -t jnks-plugin-tool -f plugin-management-cli/src/test/docker/Dockerfile plugin-management-cli/
# run the image
docker run --rm -it --entrypoint bash jnks-plugin-tool
jenkins@9aa5a8051b4d:~$ jenkins-plugin-cli --help
```
