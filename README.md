Plugin Installation Manager Tool for Jenkins
================================

[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/jenkinsci/plugin-installation-manager-tool?label=changelog)](https://github.com/jenkinsci/plugin-installation-manager-tool/releases)
[![Downloads](https://img.shields.io/github/downloads/jenkinsci/plugin-installation-manager-tool/total)](https://github.com/jenkinsci/plugin-installation-manager-tool/releases)
[![Join the chat at https://gitter.im/jenkinsci/plugin-installation-manager-cli-tool](https://badges.gitter.im/jenkinsci/plugin-installation-manager-cli-tool.svg)](https://gitter.im/jenkinsci/plugin-installation-manager-cli-tool?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

The plugin manager downloads plugins and their dependencies into a folder so that they can be easily imported into an instance of Jenkins. The goal of this tool is to replace the [Docker install-plugins.sh script](https://github.com/jenkinsci/docker/blob/master/install-plugins.sh) and the many other implementations of plugin management that have been recreated across Jenkins. The tool also allows users to see more information about the plugins they are downloading, such as available updates and security warnings. By default, plugins will be downloaded; the user can specify not to download plugins using the --no-download option.

### Usage

- [Getting Started](#getting-started)
- [CLI Options](#cli-options)
- [Advanced configuration](#advanced-configuration)
- [Plugin Input Format](#plugin-input-format)
- [Updating plugins](#updating-plugins)
- [Examples](#examples)
- [Proxy Support](#proxy-support)
- [Other Information](#other-information)

#### Getting Started

Download the latest jenkins-plugin-manager jar [from here](https://github.com/jenkinsci/plugin-installation-manager-tool/releases/latest) and run it as shown below.

```bash
java -jar jenkins-plugin-manager-*.jar --war /your/path/to/jenkins.war --plugin-file /your/path/to/plugins.txt --plugins delivery-pipeline-plugin:1.3.2 deployit-plugin
```

Alternatively, build and run the plugin manager yourself from source:

```bash
mvn clean install 
java -jar plugin-management-cli/target/jenkins-plugin-manager-*.jar --war /file/path/jenkins.war --plugin-file /file/path/plugins.txt --plugins delivery-pipeline-plugin:1.3.2 deployit-plugin
```

If you use a [Jenkins docker image](https://hub.docker.com/r/jenkins/jenkins), the plugin manager can be invoked inside the container via the bundled `jenkins-plugin-cli` shell script:

```
jenkins-plugin-cli --plugin-file /your/path/to/plugins.txt --plugins delivery-pipeline-plugin:1.3.2 deployit-plugin
```

#### CLI Options
* `--plugin-file` or `-f`: (optional) Path to the plugins.txt, or plugins.yaml file, which contains a list of plugins to install. If this file does not exist, or if the file exists, but does not have a .txt or .yaml/.yml extension, then an error will be thrown. 
* `--plugin-download-directory` or `-d`: (optional) Directory in which to install plugins. This configuration can also be made via the PLUGIN_DIR environment variable. The directory will be first deleted, then recreated. If no directory configuration is provided, the defaults are C:\ProgramData\Jenkins\Reference\Plugins if the detected operating system is Microsoft Windows, or /usr/share/jenkins/ref/plugins otherwise.
* `--plugins` or `-p`: (optional) List of plugins to install (see plugin format below), separated by a space.
* `--jenkins-version`: (optional) Version of Jenkins to be used.
  If not specified, the plugin manager will try to extract it from the WAR file or other sources.
  The argument can be also set using the `JENKINS_VERSION` environment variable.
* `--war` or `-w`: (optional) Path to Jenkins war file. If no war file is entered, will default to /usr/share/jenkins/jenkins.war or C:\ProgramData\Jenkins\jenkins.war, depending on the user's OS. Plugins that are already included in the Jenkins war will only be downloaded if their required version is newer than the one included.
* `--list` or `-l`: (optional) Lists plugin names and versions of: installed plugins (plugins that already exist in the plugin directory), bundled plugins (non-detached plugins that exist in the war file), plugins that will be downloaded (highest required versions of the requested plugins and dependencies that are not already installed), and the effective plugin set (the highest versions of all plugins that are already installed or will be installed)
* `--verbose`: (optional) Set to true to show additional information about plugin dependencies and the download process
* `--view-security-warnings`: (optional) Set to true to show if any of the user specified plugins have security warnings
* `--view-all-security-warnings`: (optional) Set to true to show all plugins that have security warnings.
* `--available-updates`: (optional) Set to true to show if any requested plugins have newer versions available. If a Jenkins version-specific update center is available, the latest plugin version will be determined based on that update center's data.
* `--output {stdout,yaml,txt}`: (optional) Format to output plugin updates file in, stdout is the default.
* `--latest false`: (optional) Set to false to download the minimum required version of all dependencies.
* `--latest-specified`: (optional) (advanced) Set to true to download latest dependencies of any plugin that is requested to have the latest version. All other plugin dependency versions are determined by the update center metadata or the plugin MANIFEST.MF.
* `--jenkins-update-center`: (optional) Sets the main update center filename, which can also be set via the JENKINS_UC environment variable. If a CLI option is entered, it will override what is set in the environment variable. If not set via CLI option or environment variable, will default to https://updates.jenkins.io/update-center.actual.json
* `--jenkins-experimental-update-center`: (optional) Sets the experimental update center, which can also be set via the JENKINS_UC_EXPERIMENTAL environment variable. If a CLI option is entered, it will override what is set in the environment variable. If not set via CLI option or environment variable, will default to https://updates.jenkins.io/experimental/update-center.actual.json
* `--jenkins-incrementals-repo-mirror`: (optional) Sets the incrementals repository mirror, which can also be set via the JENKINS_INCREMENTALS_REPO_MIRROR environment variable. If a CLI option is entered, it will override what is set in the environment variable. If not set via CLI option or environment variable, will default to https://repo.jenkins-ci.org/incrementals.
* `--jenkins-plugin-info`: (optional) Sets the location of plugin information, which can also be set via the JENKINS_PLUGIN_INFO environment variable. If a CLI option is provided, it will override what is set in the environment variable. If not set via CLI option or environment variable, will default to https://updates.jenkins.io/current/plugin-versions.json.
* `--version` or `-v`: (optional) Displays the plugin management tool version and exits.
* `--no-download`: (optional) Set to true to not download plugins. By default it is set to false and plugins will be downloaded.
* `--skip-failed-plugins`: (optional) Adds the option to skip plugins that fail to download - CAUTION should be used when passing this flag as it could leave
Jenkins in a broken state.
* `--credentials`: (optional) Comma-separated list of credentials to use for Basic Authentication for specific hosts (and optionally ports). Each value must adhere to format `<host>[:port]:<username>:<password>`. The password must not contain a `,`! The credentials are not used preemptively.

#### Advanced configuration

* `CACHE_DIR`: used to configure the directory where the plugins update center cache is located. By default it will be in `~/.cache/jenkins-plugin-management-cli`,
if the user doesn't have a home directory when it will go to: `$(pwd)/.cache/jenkins-plugin-management-cli`.

* `JENKINS_UC_DOWNLOAD`: *DEPRECATED* use `JENKINS_UC_DOWNLOAD_URL` instead.

* `JENKINS_UC_DOWNLOAD_URL`: used to configure a custom URL from where plugins will be downloaded from. When this value is set, it replaces the plugin download URL found in the `update-center.json` file with `${JENKINS_UC_DOWNLOAD_URL}`. Often used to cache or to proxy the Jenkins plugin download site.
If set then all plugins will be downloaded through that URL. 

* `JENKINS_UC_HASH_FUNCTION`: used to configure the hash function which checks content from UCs. Currently `SHA1` (deprecated), `SHA256` (default), and `SHA512` can be specified.

#### Plugin Input Format
The expected format for plugins in the .txt file or entered through the `--plugins` CLI option is `artifact ID:version` or `artifact ID:url` or `artifact:version:url`

Use plugin artifact ID, without -plugin extension. If a plugin cannot be downloaded, -plugin will be appended to the name and download will be retried. This is for cases in which plugins don't follow the rules about artifact ID (i.e. docker plugin).

The version and download url are optional. By default, the latest version of the plugin will be downloaded. If both a version and a url are supplied, the version will not be used to determine the plugin download location and the library will attempt to download the plugin from the given url.

The following custom version specifiers can also be used:

* `latest` - downloads the latest version from a version specific update center if one exists for the version in the Jenkins war file. If no version specific update center exists, will use the main update center [https://updates.jenkins.io](https://updates.jenkins.io)
* `experimental` - downloads the latest version from the [experimental update center](https://jenkins.io/doc/developer/publishing/releasing-experimental-updates/), which offers Alpha and Beta versions of plugins. Default value: [https://updates.jenkins.io/experimental](https://updates.jenkins.io/experimental)
* `incrementals;org.jenkins-ci.plugins.workflow;2.19-rc289.d09828a05a74` - downloads the plugin from the [incrementals repo](https://jenkins.io/blog/2018/05/15/incremental-deployment/). For this option you need to specify groupId of the plugin. Note that this value may change between plugin versions without notice. More information on incrementals and their use for Docker images can be found [here](https://github.com/jenkinsci/incrementals-tools#updating-versions-for-jenkins-docker-images).  

A set of plugins can also be provided through a YAML file, using the following format:

```yaml
plugins:
  - artifactId: git
    source:
      version: latest
  - artifactId: job-import-plugin
    source:
      version: 2.1
  - artifactId: docker
  - artifactId: cloudbees-bitbucket-branch-source
    source:
      version: 2.4.4
  - artifactId: script-security
    source:
      url: http://ftp-chi.osuosl.org/pub/jenkins/plugins/script-security/1.56/script-security.hpi
  - artifactId: workflow-step-api
    groupId: org.jenkins-ci.plugins.workflow
    source:
      version: 2.19-rc289.d09828a05a74
  ...
```

As with the plugins.txt file, version and URL are optional. If no version is provided, the latest version is used by default. If a groupId is provided, the tool will try to download the plugin from the Jenkins incrementals repository.

#### Updating plugins

The CLI can output a new file with a list of updated plugin references.

Text format:

```bash
$ java -jar jenkins-plugin-manager-*.jar --available-updates --output txt --plugins mailer:1.31
```
Result:
```
mailer:1.32
```

YAML format:
```bash
$ java -jar jenkins-plugin-manager-*.jar --available-updates --output yaml --plugins mailer:1.31
```
Result:
```yaml
plugins:
- artifactId: "mailer"
  source:
    version: "1.32"
```

Human readable:

```bash
$ java -jar jenkins-plugin-manager-*.jar --available-updates --plugins mailer:1.31
```
Result:
```
Available updates:
mailer (1.31) has an available update: 1.32
```

#### Examples
If a URL is included, then a placeholder should be included for the version. Examples of plugin inputs:

* `github-branch-source` - will download the latest version
* `github-branch-source:latest` - will download the latest version
* `github-branch-source:2.5.3` - will download version 2.5.3
* `github-branch-source:experimental` - will download the latest version from the experimental update center
* `github-branch-source:2.5.2:https://updates.jenkins.io/2.121/latest/github-branch-source.hpi` - will download version of plugin at url regardless of requested version
* `github-branch-source:https://updates.jenkins.io/2.121/latest/github-branch-source.hpi` - will treat the url like the version, which is not likely the behavior you want
* `github-branch-source::https://updates.jenkins.io/2.121/latest/github-branch-source.hpi` - will download plugin from url

If a plugin to be downloaded from the incrementals repository is requested using the -plugins option from the CLI, the plugin name should be enclosed in quotes, since the semi-colon is otherwise interpreted as the end of the command.

```
java -jar jenkins-plugin-manager-*.jar -p "workflow-support:incrementals;org.jenkins-ci.plugins.workflow;2.19-rc289.d09828a05a74"
```

#### Proxy Support
Proxy support is available using standard [Java networking system properties](https://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html) `http.proxyHost` and `http.proxyPort`. Note that this provides only basic NTLM support and you may need to use an authentication proxy like [CNTLM](https://sourceforge.net/projects/cntlm/) to cover more advanced authentication use cases.

```bash
# Example using proxy system properties
java -Dhttp.proxyPort=3128 -Dhttp.proxyHost=myproxy.example.com -jar jenkins-plugin-manager-*.jar
```


#### Other Information
The plugin manager tries to use update center data to get the latest information about a plugin's dependencies. If this information is unavailable, it will use the dependency information from the downloaded plugin's MANIFEST.MF file. By default, the versions of the plugin dependencies are determined by the update center metadata or the plugin MANIFEST.MF file, but the user can specify other behavior using the `latest` or `latest-specified` options.

For plugins listed in a .txt file, each plugin must be listed on a new line. Comments beginning with `#` will be filtered out.

Support for downloading plugins from maven is not currently supported. [JENKINS-58217](https://issues.jenkins-ci.org/browse/JENKINS-58217)

When using `--latest` you may run into a scenario where the jenkins update mirror contains the directory of the newer version of a plugin(release in progress), regardless of if there is a jpi to download, which results in a download failure. It's recommended that you pin your plugin requirement versions until the mirror has been updated to more accurately represent what is available. More information on this challenge can be found [here](https://groups.google.com/forum/#!topic/jenkins-infra/R7QqpgoSkbI), and [here](https://github.com/jenkinsci/plugin-installation-manager-tool/issues/87).

The version-pinning behavior of this plugin installer has changed compared to the previous Jenkins plugin installer. By default, `--latest` option defaults to `true`, which means that even if you pass a list of pinned versions, these may fail to be installed correctly if they or some other dependency has a newer latest version available. In order to use *only* pinned versions of plugins, you must pass `--latest=false`. **NOTE:** When a new dependency is added to a plugin, it won’t get updated until you notice that it’s missing from your plugin list. (Details here: https://github.com/jenkinsci/plugin-installation-manager-tool/issues/250)
