/* Only keep the 10 most recent builds. */
properties([[$class: 'BuildDiscarderProperty',
                strategy: [$class: 'LogRotator', numToKeepStr: '10']]])

buildPlugin()

/* These platforms correspond to labels in ci.jenkins.io, see:
 *  https://github.com/jenkins-infra/documentation/blob/master/ci.adoc
 */
//List platforms = ['linux', 'windows']
//String jdkVersion = "8"
//
//Map branches = [:]
//
//for (int i = 0; i < platforms.size(); ++i) {
//    String label = platforms[i]
//    branches[label] = {
//        node(label) {
//
//            // Archive artifacts once with pom declared baseline
//            boolean doArchiveArtifacts = !jenkinsVersion && !archivedArtifacts
//            if (doArchiveArtifacts) {
//                archivedArtifacts = true
//            }
//
//
//            stage('Checkout') {
//                checkout scm
//
//                incrementals = fileExists('.mvn/extensions.xml') &&
//                        readFile('.mvn/extensions.xml').contains('git-changelist-maven-extension')
//            }
//
//            stage('Build') {
//                timeout(30) {
//                    m2repo = "${pwd tmp: true}/m2repo"
//                    List<String> mavenOptions = [
//                            '--update-snapshots',
//                            "-Dmaven.repo.local=$m2repo",
//                            '-Dmaven.test.failure.ignore',
//                    ]
//                    if (incrementals) { // set changelist and activate produce-incrementals profile
//                        mavenOptions += '-Dset.changelist'
//                        if (doArchiveArtifacts) { // ask Maven for the value of -rc999.abc123def456
//                            changelistF = "${pwd tmp: true}/changelist"
//                            mavenOptions += "help:evaluate -Dexpression=changelist -Doutput=$changelistF"
//                        }
//                    }
//                    mavenOptions += "-Dfindbugs.failOnError=false"
//                    mavenOptions += "clean install"
//                    mavenOptions += "findbugs:findbugs"
//                    mavenOptions += "checkstyle:checkstyle"
//
//                    infra.runMaven(mavenOptions, jdkVersion)
//                }
//            }
//
//            stage('Archive') {
//                /* Archive the test results */
//                junit '**/target/surefire-reports/TEST-*.xml'
//                findbugs pattern: '**/target/findbugsXml.xml'
//
//                if (doArchiveArtifacts) {
//                    if (incrementals) {
//                        String changelist = readFile(changelistF)
//                        dir(m2repo) {
//                            fingerprint '**/*-rc*.*/*-rc*.*' // includes any incrementals consumed
//                            archiveArtifacts artifacts: "**/*$changelist/*$changelist*",
//                                    excludes: '**/*.lastUpdated',
//                                    allowEmptyArchive: true // in case we forgot to reincrementalify
//                        }
//                        publishingIncrementals = true
//                    } else {
//                        String artifacts = '**/target/*.hpi,**/target/*.jpi'
//
//                        archiveArtifacts artifacts: artifacts, fingerprint: true
//                    }
//                }
//            }
//        }
//    }
//}
//
//parallel branches
//infra.maybePublishIncrementals()
