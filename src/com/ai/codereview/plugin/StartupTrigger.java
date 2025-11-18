package com.ai.codereview.plugin;

import org.eclipse.ui.IStartup;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

public class StartupTrigger implements IStartup {
    @Override
    public void earlyStartup() {
        System.out.println("💡 StartupTrigger.earlyStartup() called at: " + System.currentTimeMillis());
        try {
            Bundle bundle = Platform.getBundle("com.ai.codereview.plugin");
            if (bundle != null && bundle.getState() != Bundle.ACTIVE) {
                System.out.println("💡 Starting bundle: " + bundle.getSymbolicName());
                bundle.start(); // forces Activator.start()
            } else {
                System.out.println("💡 Bundle already active or not found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
