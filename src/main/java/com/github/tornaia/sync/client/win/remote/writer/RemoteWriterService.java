package com.github.tornaia.sync.client.win.remote.writer;

import com.github.tornaia.sync.client.win.local.writer.DiskWriterService;
import com.github.tornaia.sync.client.win.remote.RemoteKnownState;
import com.github.tornaia.sync.shared.api.FileMetaInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

@Component
public class RemoteWriterService {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteWriterService.class);

    @Value("${frosch-sync.userid:7247234}")
    private String userid;

    @Value("${client.sync.directory.path:C:\\temp\\client}")
    private String syncDirectoryPath;

    @Autowired
    private RemoteRestCommandService remoteRestCommandService;

    @Autowired
    private DiskWriterService diskWriterService;

    @Autowired
    private RemoteKnownState remoteKnownState;

    private Path syncDirectory;

    @PostConstruct
    public void init() {
        syncDirectory = new File(syncDirectoryPath).toPath();
    }

    public boolean createFile(String relativePath) {
        Path absolutePath = getAbsolutePath(relativePath);
        File file = absolutePath.toFile();

        FileMetaInfo localFileMetaInfo;
        try {
            localFileMetaInfo = FileMetaInfo.createNonSyncedFileMetaInfo(userid, relativePath, file);
        } catch (IOException e) {
            LOG.error("Cannot read file", e);
            return false;
        }

        Optional<FileMetaInfo> optionalRemoteFileMetaInfo = remoteKnownState.get(relativePath);
        if (optionalRemoteFileMetaInfo.isPresent() && Objects.equals(optionalRemoteFileMetaInfo.get(), localFileMetaInfo)) {
            LOG.info("File is already known by server: " + localFileMetaInfo);
            return true;
        }

        FileCreateResponse fileCreateResponse = remoteRestCommandService.onFileCreate(localFileMetaInfo, file);
        boolean ok = Objects.equals(FileCreateResponse.Status.OK, fileCreateResponse.status);
        if (ok) {
            LOG.info("File created on server: " + fileCreateResponse.fileMetaInfo);
            remoteKnownState.add(fileCreateResponse.fileMetaInfo);
            return true;
        }

        boolean conflict = Objects.equals(FileCreateResponse.Status.CONFLICT, fileCreateResponse.status);
        if (conflict) {
            handleConflict(absolutePath, localFileMetaInfo);
            return false;
        }

        return false;
    }

    public boolean modifyFile(String relativePath) {
        Path absolutePath = getAbsolutePath(relativePath);
        File file = absolutePath.toFile();

        FileMetaInfo localFileMetaInfo;
        try {
            localFileMetaInfo = FileMetaInfo.createNonSyncedFileMetaInfo(userid, relativePath, file);
        } catch (IOException e) {
            LOG.error("Cannot read file", e);
            return false;
        }

        Optional<FileMetaInfo> optionalRemoteFileMetaInfo = remoteKnownState.get(relativePath);
        if (!optionalRemoteFileMetaInfo.isPresent()) {
            LOG.warn("Cannot modify a file that is unknown to server. So lets create instead of modification: " + localFileMetaInfo);
            return createFile(relativePath);
        }
        if (optionalRemoteFileMetaInfo.isPresent() && Objects.equals(optionalRemoteFileMetaInfo.get(), localFileMetaInfo)) {
            LOG.info("File is already known by server: " + localFileMetaInfo);
            return true;
        }

        FileMetaInfo requestFileMetaInfo;
        try {
            requestFileMetaInfo = new FileMetaInfo(optionalRemoteFileMetaInfo.get().id, localFileMetaInfo.userid, localFileMetaInfo.relativePath, file);
        } catch (IOException e) {
            LOG.warn("Cannot read file from disk", e);
            return false;
        }

        FileModifyResponse fileModifyResponse = remoteRestCommandService.onFileModify(requestFileMetaInfo, file);
        boolean ok = Objects.equals(FileModifyResponse.Status.OK, fileModifyResponse.status);
        if (ok) {
            LOG.info("File modified on server: " + fileModifyResponse.fileMetaInfo);
            remoteKnownState.add(fileModifyResponse.fileMetaInfo);
            return true;
        }

        boolean conflict = Objects.equals(FileModifyResponse.Status.CONFLICT, fileModifyResponse.status);
        if (conflict) {
            handleConflict(absolutePath, localFileMetaInfo);
            return false;
        }

        boolean notFound = Objects.equals(FileModifyResponse.Status.NOT_FOUND, fileModifyResponse.status);
        if (notFound) {
            LOG.info("Updating file's content on server failed since it was removed. Create now file on server: " + relativePath);
            return createFile(relativePath);
        }

        return false;
    }

    public boolean deleteFile(String relativePath) {
        Optional<FileMetaInfo> optionalRemoteFileMetaInfo = remoteKnownState.get(relativePath);
        if (!optionalRemoteFileMetaInfo.isPresent()) {
            LOG.info("File is already deleted from server: " + relativePath);
            return true;
        }

        FileMetaInfo fileMetaInfo = optionalRemoteFileMetaInfo.get();

        boolean localFileExistWithThisRelativePath = getAbsolutePath(relativePath).toFile().exists();
        if (localFileExistWithThisRelativePath) {
            LOG.info("Do not delete a file on server that exists on disk: " + fileMetaInfo);
            return true;
        }
        FileDeleteResponse fileDeleteResponse = remoteRestCommandService.onFileDelete(fileMetaInfo);

        boolean ok = Objects.equals(FileCreateResponse.Status.OK, fileDeleteResponse.status);
        if (ok) {
            LOG.info("File deleted from server: " + fileMetaInfo);
            remoteKnownState.remove(fileMetaInfo);
            return true;
        }

        return false;
    }

    private void handleConflict(Path absolutePath, FileMetaInfo localFileMetaInfo) {
        // TODO move this conflict file name creation to a separate object
        String originalFileName = absolutePath.toFile().getAbsolutePath();
        boolean hasExtension = originalFileName.indexOf('.') != -1;
        String postFix = "_conflict_" + localFileMetaInfo.length + "_" + localFileMetaInfo.creationDateTime + "_" + localFileMetaInfo.modificationDateTime;
        String conflictFileName = hasExtension ? originalFileName.split("\\.", 2)[0] + postFix + "." + originalFileName.split("\\.", 2)[1] : originalFileName + postFix;
        // TODO what should happen when this renamed/conflictFileName file exists?
        Path renamed = new File(absolutePath.toFile().getParentFile().getAbsolutePath()).toPath().resolve(conflictFileName);
        LOG.warn("File already exists on server. Renaming " + absolutePath + " -> " + renamed);
        diskWriterService.replaceFileAtomically(absolutePath, renamed);
    }

    private Path getAbsolutePath(String relativePath) {
        String relativePathWithoutLeadingSlash = relativePath;
        return syncDirectory.resolve(relativePathWithoutLeadingSlash);
    }
}