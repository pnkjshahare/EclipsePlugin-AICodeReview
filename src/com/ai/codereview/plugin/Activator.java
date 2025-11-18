package com.ai.codereview.plugin;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * Activator
 * ---------
 * Entry point for Eclipse plugin.
 * Login is handled inside the AI Review Panel.
 * Plugin should NOT show login popup here.
 */
public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "com.ai.codereview.plugin";
    private static Activator plugin;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;

        System.out.println("✅ Activator.start() triggered at: " + System.currentTimeMillis());
        System.out.println("🚀 Activator.start() called — AI Code Review Plugin initializing...");

        // Show initial plugin startup messages
        ReviewConsole.show("🚀 CTPL Code Review Plugin started.");
        ReviewConsole.show("👉 Please login using the Login button inside the AI Review Panel.");

        // Start Git watcher immediately
        // But GitChangeListener will block AI review until login
        GitChangeListener.registerListener();

        ReviewConsole.show("🔍 Git listener attached — waiting for commits...");
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
        System.out.println("🛑 AI Code Review Plugin stopped.");
    }

    public static Activator getDefault() {
        return plugin;
    }
}
