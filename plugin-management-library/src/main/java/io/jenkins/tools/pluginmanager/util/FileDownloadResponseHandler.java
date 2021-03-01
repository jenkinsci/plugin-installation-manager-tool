package io.jenkins.tools.pluginmanager.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.impl.client.AbstractResponseHandler;

public class FileDownloadResponseHandler extends AbstractResponseHandler<File> {

    private final File target;

    public FileDownloadResponseHandler(File target) {
        this.target = target;
    }

    @Override
    public File handleEntity(HttpEntity entity) throws IOException {
        try (InputStream source = entity.getContent()) {
            FileUtils.copyInputStreamToFile(source, this.target);
        }
        return this.target;
    }

}
