pipeline {
    agent { label 'maven' }

    options {
        timestamps()
    }

    stages {
        stage('buildTest') {
	    steps {
	        deleteDir()
		checkout scm
	    }
	}
    }
}
