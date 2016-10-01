package com.github.tornaia.sync.client.win;

import com.github.tornaia.sync.shared.AddFileRequest;
import com.sun.nio.file.SensitivityWatchEventModifier;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

public class WinClientApp {

    private static final String SERVER_URL = "http://127.0.0.1:8080";
    private static final String HELLO_PATH = "/file/hello";

    private static HttpClient httpClient = HttpClientBuilder.create().build();

    public static void main(String[] args) throws IOException {
        Path tempDirectory = FileSystems.getDefault().getPath("C:\\temp\\");
        System.out.println("PID: " + ManagementFactory.getRuntimeMXBean().getName());
        String serverInfo = getServerInfo();
        System.out.println("Server says: " + serverInfo);

        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            tempDirectory.register(watcher, new WatchEvent.Kind[]{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW}, SensitivityWatchEventModifier.HIGH);
            registerRecursive(watcher, tempDirectory);

            while (true) {
                WatchKey key;
                try {
                    key = watcher.poll(25, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    return;
                }
                if (key == null) {
                    Thread.yield();
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    System.out.println(kind.name() + ", filename: " + filename);

                    if (kind == OVERFLOW) {
                        continue;
                    }
                    if (kind == ENTRY_CREATE) {
                        onFileCreate(filename);
                    } else if (kind == ENTRY_DELETE) {
                        onFileDelete(filename);
                    } else if (kind == ENTRY_MODIFY) {
                        onFileModify(filename);
                    }

                    boolean valid = key.reset();
                    if (!valid) {
                        break;
                    }
                }
                Thread.yield();
            }
        } catch (Throwable e) {
            // Log or rethrow the error
        }
    }

    private static void registerRecursive(WatchService watchService, Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                System.out.println("WatchService registered for dir: " + dir.getFileName());
                dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void onFileCreate(Path filePath) {
        AddFileRequest request = new AddFileRequest();
    }

    private static void onFileDelete(Path filePath) {

    }

    private static void onFileModify(Path filePath) {

    }

    private static String getServerInfo() throws IOException {
        HttpResponse response = httpClient.execute(new HttpGet(SERVER_URL + HELLO_PATH));
        return IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset());
    }
}
