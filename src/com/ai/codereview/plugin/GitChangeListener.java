package com.ai.codereview.plugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * GitChangeListener
 * -----------------
 * Watches the current project's Git repository (.git folder)
 * for commit changes triggered via CMD or external Git CLI.
 * Captures the diff and sends it to the Gemini AI review API.
 */
public class GitChangeListener {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean watcherStarted = false;

    /**
     * Register the watcher for the active project’s .git folder.
     */
    public static void registerListener() {
        try {
            GitChangeListener listener = new GitChangeListener();
            listener.startWatchingCurrentProject();
        } catch (Exception e) {
            ReviewConsole.show("❌ Failed to register Git watcher: " + e.getMessage());
        }
    }

    /**
     * Starts watching .git/HEAD for the currently open project only.
     */
    private void startWatchingCurrentProject() {
        if (watcherStarted) {
            ReviewConsole.show("⚠️ Watcher already started, skipping.");
            return;
        }
        watcherStarted = true;

        executor.submit(() -> {
            try {
                IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
                if (projects.length == 0) {
                    ReviewConsole.show("⚠️ No projects found in workspace.");
                    return;
                }

                IProject project = projects[0];
                ReviewConsole.show("📂 Using project: " + project.getName());

                if (!project.isOpen()) {
                    ReviewConsole.show("⚠️ Project is not open: " + project.getName());
                    return;
                }

                File gitDir = new File(project.getLocation().toFile(), ".git");
                if (!gitDir.exists() || !gitDir.isDirectory()) {
                    ReviewConsole.show("⚠️ No .git directory found in project: " + project.getName());
                    return;
                }

                ReviewConsole.show("✅ Watching Git repo for: " + project.getName());
                startWatcher(gitDir.toPath());

            } catch (Exception e) {
                ReviewConsole.show("❌ Failed to start watcher: " + e.getMessage());
            }
        });
    }

    /**
     * Watches HEAD and refs for commit updates in this repo.
     */
    private void startWatcher(Path gitDir) {

        executor.submit(() -> {
            try {
                Path headFile = gitDir.resolve("HEAD");
                if (!Files.exists(headFile)) {
                    ReviewConsole.show("⚠️ HEAD file missing in: " + gitDir);
                    return;
                }

                // Determine which ref file to watch
                String headContent = Files.readString(headFile).trim();
                Path refFile = headContent.startsWith("ref:")
                        ? gitDir.resolve(headContent.substring(5).trim())
                        : headFile;

                if (!Files.exists(refFile)) {
                    ReviewConsole.show("⚠️ No valid ref file found for HEAD in: " + gitDir);
                    return;
                }

                ReviewConsole.show("🌀 Watching HEAD ref file: " + refFile);

                WatchService watchService = FileSystems.getDefault().newWatchService();
                refFile.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

                while (true) {
                    WatchKey key = watchService.take();

                    for (WatchEvent<?> event : key.pollEvents()) {
                        String changedFile = event.context().toString();
                        ReviewConsole.show("📂 Detected change in: " + changedFile);

                        if (changedFile.contains(refFile.getFileName().toString())
                                || changedFile.endsWith(".lock")
                                || changedFile.equals("HEAD")) {

                            ReviewConsole.show("✅ Commit detected in repo: " +
                                    gitDir.getParent().getFileName());

                            Thread.sleep(500); // allow Git to complete writing
                            handleCommit(gitDir.toFile());
                        }
                    }
                    key.reset();
                }

            } catch (Exception e) {
                ReviewConsole.show("❌ Watcher error: " + e.getMessage());
            }
        });
    }

    /**
     * Captures the diff and sends it to the AI API.
     */
    private void handleCommit(File gitDir) {

        // 🚨 **STOP EVERYTHING IF USER IS NOT LOGGED IN**
        if (!AuthManager.isLoggedIn()) {
            ReviewConsole.show("🔒 Please login to enable AI Code Review.");
            return; // ⛔ DO NOT EXPOSE DIFF OR SEND AI REQUEST
        }

        try {
            Repository repo = new FileRepositoryBuilder()
                    .setGitDir(gitDir)
                    .readEnvironment()
                    .findGitDir()
                    .build();

            try (Git git = new Git(repo)) {

                ObjectId head = repo.resolve("HEAD^{tree}");
                ObjectId prevHead = repo.resolve("HEAD~1^{tree}");

                if (head == null || prevHead == null) {
                    ReviewConsole.show("⚠️ Not enough commits to compare (need at least 2).");
                    return;
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                DiffFormatter df = new DiffFormatter(out);
                df.setRepository(repo);
                df.format(prevHead, head);
                df.close();

                String diff = out.toString(StandardCharsets.UTF_8);

                if (diff.isEmpty()) {
                    ReviewConsole.show("📭 No diff found for commit.");
                    return;
                }
                GitDiffProvider.setLastDiff(diff);
                ReviewConsole.show("📜 Diff captured and sent for AI to Review:\n");

                String aiResponse = AIClient.sendReview(diff);
                ReviewConsole.show("🤖 Gemini Review Result:\n" + aiResponse);
            }

        } catch (Exception e) {
            ReviewConsole.show("❌ Commit handling failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
