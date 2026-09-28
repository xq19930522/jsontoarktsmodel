package com.sincegame.jsontomodel.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.sincegame.jsontomodel.ArkTsModelGenerator;
import com.sincegame.jsontomodel.json.MiniJson;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * 对应 jsonToArkTS.html 的双栏界面：
 * 左侧 JSON 输入（实时校验，非法红色边框/合法绿色边框），
 * 右侧类名输入、Interface/Class 单选、带参构造选项、生成结果与复制按钮。
 */
public class JsonToArkTsDialog extends DialogWrapper {

    private final JTextArea jsonInput;
    private final JTextField classNameInput;
    private final JRadioButton interfaceRadio;
    private final JRadioButton classRadio;
    private final JCheckBox parameterizedConstructor;
    private final JTextArea resultArea;
    private final JButton copyButton;
    private final InsertHandler insertHandler;
    private final JCheckBox autoInsertCheckBox;

    /** 若提供了 handler（即从编辑器中唤起），对话框额外显示 "Insert into Editor" 按钮。 */
    public interface InsertHandler {
        void insert(String code);
    }

    public JsonToArkTsDialog(String initialJson, String initialClassName) {
        this(initialJson, initialClassName, null);
    }

    public JsonToArkTsDialog(String initialJson, String initialClassName, InsertHandler insertHandler) {
        super(true);
        this.insertHandler = insertHandler;
        setTitle("JSON to ArkTS Model");
        setModal(false);

        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 13);

        jsonInput = new JTextArea(initialJson == null ? "" : initialJson);
        jsonInput.setFont(mono);
        jsonInput.setLineWrap(false);
        jsonInput.setBorder(JBUI.Borders.empty(5));

        classNameInput = new JTextField(initialClassName == null ? "" : initialClassName);
        interfaceRadio = new JRadioButton("Interface", true);
        classRadio = new JRadioButton("Class");
        ButtonGroup group = new ButtonGroup();
        group.add(interfaceRadio);
        group.add(classRadio);

        parameterizedConstructor = new JCheckBox("Use parameterized constructor");
        parameterizedConstructor.setEnabled(false); // 默认 interface，不可选

        // 从编辑器中唤起时：生成成功后自动写入编辑器光标处（默认开启）
        autoInsertCheckBox = new JCheckBox("生成后自动写入编辑器光标处", true);
        autoInsertCheckBox.setVisible(insertHandler != null);

        resultArea = new JTextArea();
        resultArea.setFont(mono);
        resultArea.setEditable(false);
        resultArea.setBorder(JBUI.Borders.empty(5));

        copyButton = new JButton("Copy to Clipboard");
        copyButton.setEnabled(false);

        // interface 选中时禁用带参构造选项（与 HTML 行为一致）
        interfaceRadio.addActionListener(e -> parameterizedConstructor.setEnabled(false));
        classRadio.addActionListener(e -> parameterizedConstructor.setEnabled(true));

        // JSON 实时校验
        jsonInput.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validateJson();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validateJson();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                validateJson();
            }
        });
        validateJson();

        init();
    }

    private void validateJson() {
        String text = jsonInput.getText().trim();
        boolean valid;
        if (text.isEmpty()) {
            valid = true;
        } else {
            try {
                MiniJson.parse(text);
                valid = true;
            } catch (MiniJson.JsonParseException ex) {
                valid = false;
            }
        }
        jsonInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(valid ? JBColor.GREEN : JBColor.RED, 2),
                JBUI.Borders.empty(4)));
    }

    private void generate() {
        String json = jsonInput.getText().trim();
        String className = classNameInput.getText().trim();
        if (json.isEmpty() || className.isEmpty()) {
            resultArea.setText("请输入JSON和类名。");
            copyButton.setEnabled(false);
            return;
        }
        try {
            ArkTsModelGenerator.Kind kind = classRadio.isSelected()
                    ? ArkTsModelGenerator.Kind.CLASS
                    : ArkTsModelGenerator.Kind.INTERFACE;
            boolean parameterized = kind == ArkTsModelGenerator.Kind.CLASS
                    && parameterizedConstructor.isSelected();
            String code = ArkTsModelGenerator.generate(json, className, kind, parameterized);
            resultArea.setText(code);
            resultArea.setCaretPosition(0);
            copyButton.setEnabled(true);
            // 生成成功后自动写入编辑器光标处，并关闭对话框
            if (insertHandler != null && autoInsertCheckBox.isSelected()) {
                insertHandler.insert(code);
                close(OK_EXIT_CODE);
            }
        } catch (MiniJson.JsonParseException ex) {
            resultArea.setText("无效的JSON: " + ex.getMessage());
            copyButton.setEnabled(false);
        }
    }

    private void copyToClipboard() {
        String text = resultArea.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
        copyButton.setText("Copied!");
        SwingUtilities.invokeLater(() -> {
            try {
                Thread.sleep(1200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            SwingUtilities.invokeLater(() -> copyButton.setText("Copy to Clipboard"));
        });
    }

    public String getGeneratedCode() {
        return resultArea.getText();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        root.setPreferredSize(JBUI.size(980, 560));
        root.setBorder(JBUI.Borders.empty(10));

        // 顶部：类名 + 类型 + 生成按钮
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        top.add(new JLabel("Class name:"));
        classNameInput.setColumns(20);
        top.add(classNameInput);
        top.add(interfaceRadio);
        top.add(classRadio);
        top.add(parameterizedConstructor);
        top.add(autoInsertCheckBox);
        JButton generateButton = new JButton("Generate");
        generateButton.addActionListener(e -> generate());
        top.add(generateButton);
        root.add(top, BorderLayout.NORTH);

        // 中部：左右分栏
        JScrollPane jsonScroll = new JScrollPane(jsonInput);
        jsonScroll.setBorder(JBUI.Borders.customLine(JBColor.border(), 1));
        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(JBUI.Borders.customLine(JBColor.border(), 1));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true,
                titled(jsonScroll, "JSON Input"), titled(resultScroll, "Generated ArkTS"));
        split.setResizeWeight(0.5);
        root.add(split, BorderLayout.CENTER);

        // 底部：复制按钮 + 插入编辑器按钮
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        copyButton.addActionListener(e -> copyToClipboard());
        bottom.add(copyButton);
        if (insertHandler != null) {
            JButton insertButton = new JButton("Insert into Editor");
            insertButton.addActionListener(e -> {
                String code = resultArea.getText();
                if (code != null && !code.isEmpty()) {
                    insertHandler.insert(code);
                    close(OK_EXIT_CODE);
                }
            });
            bottom.add(insertButton);
        }
        root.add(bottom, BorderLayout.SOUTH);

        return root;
    }

    private static JComponent titled(JComponent component, String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    @Override
    protected void doOKAction() {
        // 非模态对话框，OK 仅关闭
        close(OK_EXIT_CODE);
    }
}
