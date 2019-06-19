Plugin Installation Manager Tool
================================

[![Join the chat at https://gitter.im/jenkinsci/plugin-installation-manager-cli-tool](https://badges.gitter.im/jenkinsci/plugin-installation-manager-cli-tool.svg)](https://gitter.im/jenkinsci/plugin-installation-manager-cli-tool?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

The plugin manager will download plugins and their dependencies into a folder so that they can easily be imported into an instance of Jenkins. This tool will replace the [Docker install-plugins.sh script](https://github.com/jenkinsci/docker/blob/master/install-plugins.sh).

### Usage

#### Getting Started
```
mvn clean install -Dfindbugs.skip=true
java -jar plugin-management-cli/target/plugin-management-cli-1.0-SNAPSHOT-jar-with-dependencies.jar /file/path/jenkins.war -pluginTxtPath /file/path/plugins.txt -plugins delivery-pipeline-plugin:1.3.2 deployit-plugin
```

#### CLI Options
* -pluginTxtPath - path to plugins.txt, which contains a list of plugins to install, default is ./plugins.txt
* -pluginDirPath - path to the directory in which to install plugins, default is ./plugins
* -plugins - list of plugins to install, separated by a space 
* -war - path to Jenkins war file, default is /usr/share/jenkins/jenkins.war
* -viewSecurityWarnings - set to true to show if any of the user specified plugins have security warnings (not yet implemented)
* -viewAllSecurityWarnings - set to true to show all plugins that have security warnings

#### Plugin Input Format
The expected format for plugins is `artifact ID:version:download url`.
Use plugin artifact ID, without -plugin extension. The version and  download url are optional. By default, the latest version of the plugin will be downloaded. Dependencies that are already included in the Jenkins war will only be downloaded if their required version is newer than the one included.

The following custom version specifiers can also be used: 

* `latest` - downloads the latest version from the main update center [https://updates.jenkins.io](https://updates.jenkins.io)
* `experimental` - downloads the latest version from the [experimental update center](https://jenkins.io/doc/developer/publishing/releasing-experimental-updates/), which offers Alpha and Beta versions of plugins. Default value: [https://updates.jenkins.io/experimental](https://updates.jenkins.io/experimental)

There is currently no way to change the update centers from their default values, but this will be changed in future development. 
