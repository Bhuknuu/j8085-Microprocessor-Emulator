import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class MemoryConfigDialog extends JDialog {
    private int startAddress = -1;
    private int endAddress = -1;
    private boolean defaultSelected = false;
    private boolean userConfirmed = false; // [FIX] true only if user clicked a button (not OS X close)


    private final Color BG_COLOR = new Color(0x121212);
    private final Color CARD_COLOR = new Color(0x1E1E1E);
    private final Color TEXT_PRIMARY = new Color(0xE2E2E2);
    private final Color ACCENT_CYAN = new Color(0x9BF0E1);
    
    private JTextField startField, endField;

    public MemoryConfigDialog(JFrame parent) {
        super(parent, "Memory Configuration", true);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_COLOR);
        
        JPanel centerPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        centerPanel.setBackground(BG_COLOR);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel startLabel = new JLabel("Start Address (Hex):");
        startLabel.setForeground(TEXT_PRIMARY);
        startField = new JTextField("0000");
        startField.setBackground(CARD_COLOR);
        startField.setForeground(TEXT_PRIMARY);
        startField.setCaretColor(ACCENT_CYAN);
        startField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x333333)),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        JLabel endLabel = new JLabel("End Address (Hex):");
        endLabel.setForeground(TEXT_PRIMARY);
        endField = new JTextField("FFFF");
        endField.setBackground(CARD_COLOR);
        endField.setForeground(TEXT_PRIMARY);
        endField.setCaretColor(ACCENT_CYAN);
        endField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x333333)),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        centerPanel.add(startLabel);
        centerPanel.add(startField);
        centerPanel.add(endLabel);
        centerPanel.add(endField);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(BG_COLOR);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));

        JButton defaultBtn = new JButton("Use Default (64KB)");
        styleButton(defaultBtn, ACCENT_CYAN, new Color(0x121212));
        defaultBtn.addActionListener((ActionEvent e) -> {
            defaultSelected = true;
            userConfirmed = true;
            setVisible(false);
        });

        JButton customBtn = new JButton("Apply Custom");
        styleButton(customBtn, CARD_COLOR, TEXT_PRIMARY);
        customBtn.addActionListener((ActionEvent e) -> {
            try {
                startAddress = Integer.parseInt(startField.getText().trim(), 16);
                endAddress = Integer.parseInt(endField.getText().trim(), 16);
                defaultSelected = false;
                userConfirmed = true;
                setVisible(false);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid Hex Address", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        buttonPanel.add(customBtn);
        buttonPanel.add(defaultBtn);

        add(centerPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        setSize(400, 200);
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    private void styleButton(JButton btn, Color bg, Color fg) {
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bg),
            BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));
    }

    public boolean isDefaultSelected() { return defaultSelected; }
    public boolean isUserConfirmed()    { return userConfirmed; }
    public int getStartAddress()        { return startAddress; }
    public int getEndAddress()          { return endAddress; }
}
