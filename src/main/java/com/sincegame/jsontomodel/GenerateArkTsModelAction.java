package com.sincegame.jsontomodel;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.sincegame.jsontomodel.ui.JsonToArkTsDialog;
import org.jetbrains.annotations.NotNull;

/**
 * "JSON to ArkTS Model..." 入口：
 * Tools 菜单 / 编辑器右键菜单 / Alt+Shift+J。
 *
 * 从编辑器唤起时：有选区则以选区 JSON 预填，否则若是 .json 文件则预填整个文件内容，
 * 并在对话框中提供 "Insert into Editor" 把生成结果写回编辑器（替换选区或插入光标处）。
 */
public class GenerateArkTsModelAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
        PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(e.getDataContext());

        String initialJson = null;
        String initialClassName = "RootModel";
        if (editor != null) {
            String selected = editor.getSelectionModel().getSelectedText();
            if (selected != null && !selected.trim().isEmpty()) {
                initialJson = selected;
            } else if (psiFile != null && psiFile.getName().endsWith(".json")) {
                initialJson = editor.getDocument().getText();
            }
        }
        if (psiFile != null) {
            String baseName = psiFile.getName().replaceAll("\\.[^.]+$", "");
            String pascal = ArkTsModelGenerator.toPascalCase(baseName);
            if (!pascal.isEmpty()) {
                initialClassName = pascal;
            }
        }

        JsonToArkTsDialog.InsertHandler insertHandler = null;
        if (project != null && editor != null) {
            Editor target = editor;
            insertHandler = code -> WriteCommandAction.runWriteCommandAction(project, () -> {
                if (target.getSelectionModel().hasSelection()) {
                    target.getDocument().replaceString(
                            target.getSelectionModel().getSelectionStart(),
                            target.getSelectionModel().getSelectionEnd(),
                            code);
                } else {
                    int offset = target.getCaretModel().getOffset();
                    target.getDocument().insertString(offset, code);
                }
            });
        }

        JsonToArkTsDialog dialog = new JsonToArkTsDialog(initialJson, initialClassName, insertHandler);
        dialog.show();
    }
}
