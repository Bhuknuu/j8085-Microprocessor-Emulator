import javax.swing.*; import javax.swing.text.*; import java.awt.*;
public class TerminalPanel extends JPanel {
    private static final int MAX_CHARS=40_000;
    private JTextPane textPane; private StyledDocument doc;
    private Style normalStyle,errorStyle,systemStyle;
    public TerminalPanel(){
        setLayout(new BorderLayout()); setBackground(Theme.SURFACE_1);
        JLabel hdr=new JLabel("  OUTPUT"); hdr.setForeground(Theme.TEXT_SECONDARY); hdr.setFont(Theme.FONT_LABEL); hdr.setBackground(Theme.SURFACE_1); hdr.setOpaque(true); hdr.setBorder(BorderFactory.createCompoundBorder(Theme.bottomBorder(),BorderFactory.createEmptyBorder(5,6,5,0)));
        textPane=new JTextPane(); textPane.setEditable(false); textPane.setBackground(Theme.SURFACE_1); textPane.setFont(Theme.FONT_MONO); textPane.setMargin(new Insets(6,8,6,8));
        doc=textPane.getStyledDocument();
        normalStyle=textPane.addStyle("n",null); StyleConstants.setForeground(normalStyle,Theme.TEXT_PRIMARY); StyleConstants.setFontFamily(normalStyle,Font.MONOSPACED); StyleConstants.setFontSize(normalStyle,13);
        errorStyle=textPane.addStyle("e",null); StyleConstants.setForeground(errorStyle,Theme.ERROR); StyleConstants.setFontFamily(errorStyle,Font.MONOSPACED); StyleConstants.setFontSize(errorStyle,13); StyleConstants.setBold(errorStyle,true);
        systemStyle=textPane.addStyle("s",null); StyleConstants.setForeground(systemStyle,Theme.ACCENT); StyleConstants.setFontFamily(systemStyle,Font.MONOSPACED); StyleConstants.setFontSize(systemStyle,13);
        JScrollPane sp=new JScrollPane(textPane); sp.setBorder(BorderFactory.createEmptyBorder()); sp.getViewport().setBackground(Theme.SURFACE_1); sp.getVerticalScrollBar().setUI(new ThinScrollBarUI()); sp.getHorizontalScrollBar().setUI(new ThinScrollBarUI());
        add(hdr,BorderLayout.NORTH); add(sp,BorderLayout.CENTER);
    }
    public void appendMessage(String m){append("  "+m+"\n",normalStyle);}
    public void appendError(String m){append("  [ERR] "+m+"\n",errorStyle);}
    public void appendSystem(String m){append("  > "+m+"\n",systemStyle);}
    public void clear(){try{doc.remove(0,doc.getLength());}catch(BadLocationException ignored){}}
    public void trimIfNeeded(int max){if(doc.getLength()>max){try{doc.remove(0,max/4);append("  [older output trimmed]\n",systemStyle);}catch(BadLocationException ignored){}}}
    private void append(String text,Style style){SwingUtilities.invokeLater(()->{try{doc.insertString(doc.getLength(),text,style);textPane.setCaretPosition(doc.getLength());}catch(BadLocationException e){System.err.println("[Terminal] "+e.getMessage());}});}
    // [AG-FIX 3.4] Theme refresh
    public void updateTheme(){
        setBackground(Theme.SURFACE_1); textPane.setBackground(Theme.SURFACE_1);
        StyleConstants.setForeground(normalStyle,Theme.TEXT_PRIMARY);
        StyleConstants.setForeground(errorStyle,Theme.ERROR);
        StyleConstants.setForeground(systemStyle,Theme.ACCENT);
        repaint();
    }
}
