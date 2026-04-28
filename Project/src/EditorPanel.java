import javax.swing.*; import javax.swing.event.*; import javax.swing.text.*; import javax.swing.undo.*; import java.awt.*; import java.awt.event.*; import java.awt.geom.*; import java.io.*; import java.util.*;
public class EditorPanel extends JPanel {
    private JTabbedPane tabs; private int untitledCount=1;
    private int startAddress=0x2000;
    private final Map<Integer,UndoManager> undoMap=new HashMap<>(); // [AG-FIX 2.2] per-tab undo
    public EditorPanel(){
        setLayout(new BorderLayout()); setBackground(Theme.SURFACE_2);
        tabs=new JTabbedPane(); tabs.setBackground(Theme.SURFACE_1); tabs.setForeground(Theme.TEXT_SECONDARY); tabs.setBorder(BorderFactory.createEmptyBorder());
        addNewTab(null,null); // first tab "Untitled"
        add(tabs,BorderLayout.CENTER);
    }
    public void setStartAddress(int addr){startAddress=addr;repaintAllGutters();}
    private void repaintAllGutters(){for(int i=0;i<tabs.getTabCount();i++){Component c=tabs.getComponentAt(i);if(c instanceof JScrollPane sp&&sp.getRowHeader()!=null)sp.getRowHeader().getView().repaint();}}
    public void addNewTab(String name,File file){
        String tabName=(name!=null?name:"Untitled"+(untitledCount>1?untitledCount:""));
        untitledCount++;
        JTextArea ta=new JTextArea(); ta.setBackground(Theme.SURFACE_2); ta.setForeground(Theme.TEXT_PRIMARY); ta.setFont(Theme.FONT_EDITOR); ta.setCaretColor(Theme.ACCENT); ta.setSelectionColor(Theme.ACCENT_DIM); ta.setSelectedTextColor(Theme.TEXT_PRIMARY); ta.setMargin(new Insets(4,8,4,8)); ta.setTabSize(4);
        JScrollPane sp=new JScrollPane(ta); sp.setBorder(BorderFactory.createEmptyBorder()); sp.getViewport().setBackground(Theme.SURFACE_2); sp.getVerticalScrollBar().setUI(new ThinScrollBarUI()); sp.getHorizontalScrollBar().setUI(new ThinScrollBarUI());
        AddrGutter g=new AddrGutter(ta); sp.setRowHeaderView(g);
        int idx=tabs.getTabCount();
        tabs.addTab(tabName,sp);
        TabHeader th=new TabHeader(tabName,idx,file);
        tabs.setTabComponentAt(idx,th);
        ta.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e){dirty(th,ta,g);}
            public void removeUpdate(DocumentEvent e){dirty(th,ta,g);}
            public void changedUpdate(DocumentEvent e){g.repaint();}
        });
        tabs.setSelectedIndex(idx);
        // [AG-FIX 2.2] Attach UndoManager
        UndoManager um=new UndoManager(); um.setLimit(200);
        ta.getDocument().addUndoableEditListener(um);
        undoMap.put(System.identityHashCode(ta),um);
    }
    private void dirty(TabHeader th,JTextArea ta,AddrGutter g){th.setDirty(true);g.repaint();}
    // [AG-FIX 3.4] Theme refresh
    public void updateTheme() {
        setBackground(Theme.SURFACE_2);
        tabs.setBackground(Theme.SURFACE_1); tabs.setForeground(Theme.TEXT_SECONDARY);
        for(int i=0;i<tabs.getTabCount();i++){
            Component c=tabs.getComponentAt(i);
            if(c instanceof JScrollPane sp && sp.getViewport().getView() instanceof JTextArea ta) {
                ta.setBackground(Theme.SURFACE_2); ta.setForeground(Theme.TEXT_PRIMARY); 
                ta.setCaretColor(Theme.ACCENT); ta.setSelectionColor(Theme.ACCENT_DIM); ta.setSelectedTextColor(Theme.TEXT_PRIMARY);
                sp.getViewport().setBackground(Theme.SURFACE_2);
            }
        }
        repaintAllGutters();
        repaint();
    }
    public String getActiveCode(){Component c=tabs.getSelectedComponent();if(c instanceof JScrollPane sp&&sp.getViewport().getView() instanceof JTextArea ta)return ta.getText();return "";}
    // [AG-FIX 2.2] Edit operations
    private JTextArea getTA(){Component c=tabs.getSelectedComponent();if(c instanceof JScrollPane sp&&sp.getViewport().getView() instanceof JTextArea ta)return ta;return null;}
    private UndoManager getUM(){JTextArea ta=getTA();return ta!=null?undoMap.get(System.identityHashCode(ta)):null;}
    public void undo(){UndoManager um=getUM();if(um!=null&&um.canUndo())um.undo();}
    public void redo(){UndoManager um=getUM();if(um!=null&&um.canRedo())um.redo();}
    public void cut(){JTextArea ta=getTA();if(ta!=null)ta.cut();}
    public void copy(){JTextArea ta=getTA();if(ta!=null)ta.copy();}
    public void paste(){JTextArea ta=getTA();if(ta!=null)ta.paste();}
    // [AG-FIX 1.7] Expose the file backing the active tab (null if unsaved)
    public File getActiveFile(){
        int idx=tabs.getSelectedIndex();
        if(idx<0)return null;
        Component tc=tabs.getTabComponentAt(idx);
        if(tc instanceof TabHeader th)return th.getFile();
        return null;
    }
    public boolean saveActive(File f){Component c=tabs.getSelectedComponent();if(c instanceof JScrollPane sp&&sp.getViewport().getView() instanceof JTextArea ta){try(FileWriter fw=new FileWriter(f)){fw.write(ta.getText());TabHeader th=(TabHeader)tabs.getTabComponentAt(tabs.getSelectedIndex());th.setTabName(f.getName());th.setFile(f);th.setDirty(false);return true;}catch(IOException e){System.err.println("[Editor] Save failed: "+e.getMessage());}}return false;}
    public String loadIntoActive(File f){try(BufferedReader br=new BufferedReader(new FileReader(f))){StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line).append("\n");Component c=tabs.getSelectedComponent();if(c instanceof JScrollPane sp&&sp.getViewport().getView() instanceof JTextArea ta){ta.setText(sb.toString());TabHeader th=(TabHeader)tabs.getTabComponentAt(tabs.getSelectedIndex());th.setTabName(f.getName());th.setDirty(false);}return sb.toString();}catch(IOException e){System.err.println("[Editor] Load failed: "+e.getMessage());return "";}}

    // == Tab Header ==
    private class TabHeader extends JPanel {
        private final JLabel lbl; private final JButton close; private boolean dirty=false; private File file; private String baseName;
        TabHeader(String name,int idx,File f){
            setLayout(new FlowLayout(FlowLayout.LEFT,3,0)); setOpaque(false);
            baseName=name; file=f;
            lbl=new JLabel(name); lbl.setForeground(Theme.TEXT_SECONDARY); lbl.setFont(Theme.FONT_UI);
            close=new JButton("x"); close.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10)); close.setForeground(Theme.TEXT_DIM); close.setBackground(Theme.SURFACE_1); close.setBorder(BorderFactory.createEmptyBorder(0,4,0,0)); close.setFocusPainted(false); close.setContentAreaFilled(false);
            close.addActionListener(e->closeTab(idx));
            add(lbl); add(close);
        }
        void setDirty(boolean d){dirty=d;lbl.setText(dirty?"*"+baseName:baseName);lbl.setForeground(dirty?Theme.WARNING:Theme.TEXT_SECONDARY);}
        void setTabName(String n){baseName=n;lbl.setText(n);lbl.setForeground(Theme.TEXT_SECONDARY);}
        File getFile(){return file;} // [AG-FIX 1.7]
        void setFile(File f){file=f;} // [AG-FIX 1.7] update after first save
        boolean isDirty(){return dirty;}
    }
    private void closeTab(int idx){
        // [AG-FIX 2.2] Clean up UndoManager
        Component c=tabs.getComponentAt(idx);
        if(c instanceof JScrollPane sp&&sp.getViewport().getView() instanceof JTextArea ta)
            undoMap.remove(System.identityHashCode(ta));
        tabs.removeTabAt(idx);
        if(tabs.getTabCount()==0) addNewTab(null,null);
    }
    // [AG-FIX 2.6] Check if any tab has unsaved changes
    public boolean hasDirtyTabs(){
        for(int i=0;i<tabs.getTabCount();i++){
            Component tc=tabs.getTabComponentAt(i);
            if(tc instanceof TabHeader th&&th.isDirty())return true;
        }
        return false;
    }

    // == Address Gutter ==
    private class AddrGutter extends JComponent {
        private final JTextArea ta;
        AddrGutter(JTextArea ta){this.ta=ta;setBackground(Theme.SURFACE_1);setPreferredSize(new Dimension(52,0));}
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setColor(Theme.SURFACE_1); g2.fillRect(0,0,getWidth(),getHeight());
            g2.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12)); g2.setColor(Theme.TEXT_DIM);
            FontMetrics fm=g2.getFontMetrics();
            Element root=ta.getDocument().getDefaultRootElement();
            int lines=root.getElementCount();
            Rectangle clip=g2.getClipBounds();
            int startY=clip!=null?clip.y:0,endY=clip!=null?clip.y+clip.height:getHeight();
            int startLine=root.getElementIndex(ta.viewToModel2D(new Point(0,startY)));
            int endLine=Math.min(root.getElementIndex(ta.viewToModel2D(new Point(0,endY)))+1,lines-1);
            int addr=startAddress;
            // compute addr at startLine by summing sizes 0..startLine-1
            for(int i=0;i<startLine;i++){try{addr+=AsmSizer.getSize(ta.getText(root.getElement(i).getStartOffset(),root.getElement(i).getEndOffset()-root.getElement(i).getStartOffset()));}catch(BadLocationException ignored){}}
            for(int i=startLine;i<=endLine;i++){
                try{
                    String rawLine=ta.getText(root.getElement(i).getStartOffset(),root.getElement(i).getEndOffset()-root.getElement(i).getStartOffset());
                    int sz=AsmSizer.getSize(rawLine);
                    Rectangle2D r=ta.modelToView2D(root.getElement(i).getStartOffset());
                    if(sz>0){String addrStr=String.format("%04X",addr&0xFFFF);int x=getWidth()-fm.stringWidth(addrStr)-6;g2.setColor(Theme.TEXT_DIM);g2.drawString(addrStr,x,(int)(r.getY()+fm.getAscent()));addr+=sz;}
                }catch(Exception ignored){}
            }
            g2.setColor(Theme.BORDER); g2.drawLine(getWidth()-1,0,getWidth()-1,getHeight());
            g2.dispose();
        }
    }
}
