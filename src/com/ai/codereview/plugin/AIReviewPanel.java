package com.ai.codereview.plugin;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.part.ViewPart;

public class AIReviewPanel extends ViewPart {

    public static final String ID = "com.ai.codereview.plugin.aiReviewPanel";

    private Text outputBox;

    private Button loginBtn;
    private Button logoutBtn;
    private Button clearBtn;

    // Test Case Buttons
    private Button generateTCBtn;
    private Button validateTCBtn;
    private Button pushTCBtn;

    private String lastGeneratedTestCase = null;

    private Color bgDark, bgCard, textColor, btnBlue, btnRed;

    @Override
    public void createPartControl(Composite parent) {

        createTheme(parent.getDisplay());
        parent.setBackground(bgDark);
        parent.setLayout(new GridLayout(1, false));

        Composite card = new Composite(parent, SWT.NONE);
        card.setBackground(bgCard);

        GridData cardGD = new GridData(SWT.FILL, SWT.FILL, true, true);
        cardGD.widthHint = 450;
        card.setLayoutData(cardGD);

        GridLayout cardLayout = new GridLayout(1, false);
        cardLayout.marginWidth = 15;
        cardLayout.marginHeight = 15;
        cardLayout.verticalSpacing = 10;
        card.setLayout(cardLayout);

        // ---------- TITLE ----------
        Label title = new Label(card, SWT.NONE);
        title.setText("🤖  AI Review Panel");
        title.setForeground(textColor);
        title.setBackground(bgCard);
        title.setFont(new Font(parent.getDisplay(), "Segoe UI", 13, SWT.BOLD));

        // ---------- BUTTON BAR ----------
        Composite btnBar = new Composite(card, SWT.NONE);
        btnBar.setBackground(bgCard);

        RowLayout row = new RowLayout();
        row.spacing = 8;
        row.marginLeft = 0;
        row.marginRight = 0;
        row.wrap = false;
        btnBar.setLayout(row);

        btnBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // --- LOGIN BUTTON ---
        loginBtn = new Button(btnBar, SWT.PUSH);
        loginBtn.setText("Login");
        loginBtn.setBackground(btnBlue);
        loginBtn.setForeground(textColor);

        loginBtn.addListener(SWT.Selection, e -> {
            LoginPanel.showLoginForm(parent.getShell());
            updateButtonState(btnBar);  // UI refresh after closing login dialog
        });

        // --- LOGOUT BUTTON ---
        logoutBtn = new Button(btnBar, SWT.PUSH);
        logoutBtn.setText("Logout");
        logoutBtn.setBackground(btnRed);
        logoutBtn.setForeground(textColor);
        logoutBtn.setVisible(false);

        logoutBtn.addListener(SWT.Selection, e -> {
            AuthManager.clearToken();
            MessageDialog.openInformation(parent.getShell(), "Logout", "🔓 Logged Out Successfully!");
            refreshLogHistory();
            updateButtonState(btnBar);
        });

        // --- CLEAR LOGS ---
        clearBtn = new Button(btnBar, SWT.PUSH);
        clearBtn.setText("Clear Logs");
        clearBtn.setBackground(new Color(parent.getDisplay(), 80, 80, 80));
        clearBtn.setForeground(textColor);

        clearBtn.addListener(SWT.Selection, e -> {
            ReviewConsole.clearHistory();
            ReviewConsole.clearConsoleView();
            outputBox.setText("");
            MessageDialog.openInformation(parent.getShell(), "Logs Cleared", "🧹 All logs cleared successfully.");
            refreshLogHistory();
        });

        // --- GENERATE TEST ---
        generateTCBtn = new Button(btnBar, SWT.PUSH);
        generateTCBtn.setText("Generate Test");
        generateTCBtn.setBackground(btnBlue);
        generateTCBtn.setForeground(textColor);

        generateTCBtn.addListener(SWT.Selection, e -> {
            String diff = GitDiffProvider.getLastDiff();

            if (diff == null || diff.trim().isEmpty()) {
                MessageDialog.openError(parent.getShell(), "Error", "❌ No git diff found. Commit first!");
                return;
            }

            ReviewConsole.show("🧪 Generating test case from Git diff...");

            lastGeneratedTestCase = TestClient.generateTestCaseFromDiff(diff);

            if (lastGeneratedTestCase == null) {
                ReviewConsole.show("❌ Failed to generate test case.");
                return;
            }

            ReviewConsole.show("🧪 Test Case Generated:\n" + lastGeneratedTestCase);

            validateTCBtn.setVisible(true);
            pushTCBtn.setVisible(false);

            btnBar.layout(true, true);
        });

        // --- VALIDATE TEST ---
        validateTCBtn = new Button(btnBar, SWT.PUSH);
        validateTCBtn.setText("Validate Test");
        validateTCBtn.setBackground(btnBlue);
        validateTCBtn.setForeground(textColor);
        validateTCBtn.setVisible(false);

        validateTCBtn.addListener(SWT.Selection, e -> {
            boolean ok = TestClient.validateTestCase(lastGeneratedTestCase);

            if (!ok) {
                MessageDialog.openError(parent.getShell(), "Validation Failed", "❌ Test case invalid.");
                return;
            }

            ReviewConsole.show("✅ Test Case Validated Successfully!");
            pushTCBtn.setVisible(true);

            btnBar.layout(true, true);
        });

        // --- PUSH TEST ---
        pushTCBtn = new Button(btnBar, SWT.PUSH);
        pushTCBtn.setText("Push Test");
        pushTCBtn.setBackground(btnBlue);
        pushTCBtn.setForeground(textColor);
        pushTCBtn.setVisible(false);

        pushTCBtn.addListener(SWT.Selection, e -> {
            boolean ok = TestFileWriter.pushTestCase(lastGeneratedTestCase);

            if (ok) {
                MessageDialog.openInformation(parent.getShell(), "Success", "📁 Test case saved to /test folder.");
            } else {
                MessageDialog.openError(parent.getShell(), "Error", "❌ Failed to save test case.");
            }
        });

        // ---------- OUTPUT BOX ----------
        outputBox = new Text(card,
                SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER | SWT.READ_ONLY);
        outputBox.setBackground(bgDark);
        outputBox.setForeground(textColor);
        outputBox.setFont(new Font(parent.getDisplay(), "Segoe UI", 10, 0));

        outputBox.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        refreshLogHistory();
        updateButtonState(btnBar);
    }

    // ---------------------- REFRESH AFTER LOGIN ----------------------
    public void refreshAfterLogin() {
        Display.getDefault().asyncExec(() -> {
            refreshLogHistory();

            loginBtn.setVisible(!AuthManager.isLoggedIn());
            logoutBtn.setVisible(AuthManager.isLoggedIn());

            Composite parent = outputBox.getParent();
            if (parent != null && !parent.isDisposed())
                parent.layout(true, true);
        });
    }

    // ---------------------- UPDATE BUTTON STATES ----------------------
    private void updateButtonState(Composite btnBar) {
        Display.getDefault().asyncExec(() -> {
            boolean loggedIn = AuthManager.isLoggedIn();
            loginBtn.setVisible(!loggedIn);
            logoutBtn.setVisible(loggedIn);

            if (btnBar != null && !btnBar.isDisposed()) {
                btnBar.layout(true, true);
            }
        });
    }

    // ---------------------- LOG HISTORY ----------------------
    private void refreshLogHistory() {
        if (outputBox != null && !outputBox.isDisposed()) {
            String history = ReviewConsole.getLogHistory();
            outputBox.setText((history == null || history.isBlank())
                    ? "Waiting for commit or manual trigger..."
                    : history);
        }
    }

    public void addMessage(String msg) {
        Display.getDefault().asyncExec(() -> {
            if (outputBox != null && !outputBox.isDisposed()) {
                outputBox.append(msg + "\n");
                outputBox.setTopIndex(outputBox.getLineCount() - 1);
            }
        });
    }

    @Override
    public void setFocus() {
        if (outputBox != null && !outputBox.isDisposed()) {
            outputBox.setFocus();
        }
    }

    private void createTheme(Display display) {
        bgDark = new Color(display, 28, 28, 30);
        bgCard = new Color(display, 40, 40, 43);
        textColor = new Color(display, 235, 235, 235);
        btnBlue = new Color(display, 0, 120, 215);
        btnRed = new Color(display, 200, 50, 50);
    }
}
