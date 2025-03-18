package io.jenkins.tools.pluginmanager.config;

public enum HashFunction {
    SHA1("sha1"),
    SHA256("sha256"),
    SHA512("sha512");

    private final String hashFunctionName;

    HashFunction(String hashFunctionName) {
        this.hashFunctionName = hashFunctionName;
    }

    @Override
    public String toString() {
        return hashFunctionName;
    }
}
