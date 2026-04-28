import javax.swing.*; import javax.swing.plaf.metal.MetalLookAndFeel; import java.awt.*; import java.awt.event.*; import java.io.*;
public class MainFrame extends JFrame {
    private DashboardPanel dashboard; private EditorPanel editor; private TerminalPanel terminal; private EmulatorBridge bridge;
    private JSplitPane leftSplit; private JButton playBtn,pauseBtn,stopBtn,resetBtn,assembleBtn;
    private JTextField startAddrField;
    private ThemeManager.ThemePreset currentTheme = ThemeManager.BUILT_IN.get(0);
    private Architecture arch; // [AG-FIX 2.4] stored for memory reconfig
    private Assembler assembler; // [AG-FIX 2.4]

    public static void setupDarkTheme(){
        try{UIManager.setLookAndFeel(new MetalLookAndFeel());}catch(UnsupportedLookAndFeelException e){System.err.println("[Theme] "+e.getMessage());}
        UIManager.put("Panel.background",Theme.BASE); UIManager.put("OptionPane.background",Theme.BASE); UIManager.put("OptionPane.messageForeground",Theme.TEXT_PRIMARY);
        UIManager.put("SplitPane.background",Theme.BASE); UIManager.put("SplitPaneDivider.background",Theme.BORDER);
        UIManager.put("TabbedPane.background",Theme.SURFACE_1); UIManager.put("TabbedPane.foreground",Theme.TEXT_SECONDARY); UIManager.put("TabbedPane.selected",Theme.SURFACE_2); UIManager.put("TabbedPane.selectedForeground",Theme.ACCENT); UIManager.put("TabbedPane.tabAreaBackground",Theme.SURFACE_1); UIManager.put("TabbedPane.contentAreaColor",Theme.SURFACE_2); UIManager.put("TabbedPane.light",Theme.BORDER); UIManager.put("TabbedPane.highlight",Theme.BORDER); UIManager.put("TabbedPane.shadow",Theme.BASE); UIManager.put("TabbedPane.darkShadow",Theme.BASE); UIManager.put("TabbedPane.focus",Theme.ACCENT); UIManager.put("TabbedPane.contentBorderInsets",new Insets(0,0,0,0));
        UIManager.put("Table.background",Theme.SURFACE_2); UIManager.put("Table.foreground",Theme.TEXT_PRIMARY); UIManager.put("Table.gridColor",Theme.BORDER); UIManager.put("Table.selectionBackground",Theme.ACCENT_DIM); UIManager.put("Table.selectionForeground",Theme.ACCENT); UIManager.put("TableHeader.background",Theme.SURFACE_1); UIManager.put("TableHeader.foreground",Theme.TEXT_SECONDARY);
        UIManager.put("ScrollBar.background",Theme.SURFACE_1); UIManager.put("ScrollBar.thumb",Theme.BORDER); UIManager.put("ScrollBar.track",Theme.SURFACE_1); UIManager.put("ScrollPane.border",BorderFactory.createEmptyBorder());
        UIManager.put("ToolTip.background",Theme.SURFACE_3); UIManager.put("ToolTip.foreground",Theme.TEXT_PRIMARY); UIManager.put("ToolTip.border",BorderFactory.createLineBorder(Theme.BORDER));
        UIManager.put("Button.background",Theme.SURFACE_2); UIManager.put("Button.foreground",Theme.TEXT_PRIMARY); UIManager.put("Button.select",Theme.ACCENT_DIM); UIManager.put("Button.focus",Theme.ACCENT_DIM);
        UIManager.put("TextField.background",Theme.SURFACE_2); UIManager.put("TextField.foreground",Theme.TEXT_PRIMARY); UIManager.put("TextField.caretForeground",Theme.ACCENT); UIManager.put("Label.foreground",Theme.TEXT_PRIMARY); UIManager.put("Dialog.background",Theme.BASE);
    }

    public MainFrame(Architecture arch,Assembler assembler){
        super("j8085 Microprocessor Simulator");
        this.arch=arch; this.assembler=assembler; // [AG-FIX 2.4]
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // [AG-FIX 2.6]
        setSize(1380,820); setMinimumSize(new Dimension(900,600));
        setLayout(new BorderLayout()); getContentPane().setBackground(Theme.BASE);
        dashboard=new DashboardPanel(); editor=new EditorPanel(); terminal=new TerminalPanel();
        bridge=new EmulatorBridge(arch,assembler,dashboard,editor,terminal,this::onStart,this::onStop);
        // [AG-FIX 2.3] Wire breakpoint UI
        dashboard.setBreakpointContext(bridge.getBreakpoints(), addr->bridge.toggleBreakpoint(addr));
        setJMenuBar(buildMenuBar()); add(buildToolbar(),BorderLayout.NORTH); add(buildCenter(),BorderLayout.CENTER); add(buildControlStrip(),BorderLayout.EAST); add(buildStatus(),BorderLayout.SOUTH);
        setLocationRelativeTo(null);
        terminal.appendSystem("j8085 ready. F9=Assemble F5=Play F10=Step F2=Reset");
        // [AG-FIX 2.6] Dirty-file warning on close
        addWindowListener(new WindowAdapter(){
            public void windowClosing(WindowEvent e){confirmExit();}
        });
    }

    // == Menu Bar ==
    private JMenuBar buildMenuBar(){
        JMenuBar mb=new JMenuBar(); mb.setBackground(Theme.SURFACE_1); mb.setBorder(Theme.bottomBorder());
        mb.add(fileMenu()); mb.add(editMenu()); mb.add(viewMenu()); mb.add(toolsMenu()); mb.add(themeMenu()); mb.add(helpMenu());
        return mb;
    }
    private JMenu mk(String t){JMenu m=new JMenu(t);m.setForeground(Theme.TEXT_SECONDARY);m.setFont(Theme.FONT_UI);return m;}
    private JMenuItem mi(String t,Runnable r){JMenuItem i=new JMenuItem(t);i.setBackground(Theme.SURFACE_2);i.setForeground(Theme.TEXT_PRIMARY);i.setFont(Theme.FONT_UI);i.addActionListener(e->r.run());return i;}
    private JMenu fileMenu(){JMenu m=mk("File");
        m.add(mi("New File",()->editor.addNewTab(null,null)));
        m.add(mi("Open...",()->openFile()));
        m.addSeparator();
        m.add(mi("Save   Ctrl+S",()->saveFile(false)));
        m.add(mi("Save As...",()->saveFile(true)));
        m.add(mi("Export Intel HEX...",()->exportHex()));
        m.addSeparator();
        m.add(mi("Exit",()->System.exit(0)));
        return m;}
    // [AG-FIX 2.2] Edit menu wired to EditorPanel operations
    private JMenu editMenu(){JMenu m=mk("Edit");m.add(mi("Undo  Ctrl+Z",()->editor.undo()));m.add(mi("Redo  Ctrl+Y",()->editor.redo()));m.addSeparator();m.add(mi("Cut",()->editor.cut()));m.add(mi("Copy",()->editor.copy()));m.add(mi("Paste",()->editor.paste()));return m;}
    private JMenu viewMenu(){JMenu m=mk("View");m.add(mi("Toggle Dashboard",()->toggleDash()));m.add(mi("Toggle Terminal",()->toggleTerm()));return m;}
    // [AG-FIX 2.4] Memory Config wired to MemoryConfigDialog
    private JMenu toolsMenu(){JMenu m=mk("Tools");
        m.add(mi("Assemble  F9",()->bridge.assembleAndLoad()));
        m.add(mi("Memory Config...",()->showMemConfig()));
        m.add(mi("Dump Execution Trace",()->dumpTrace()));
        return m;}
    private void dumpTrace() {
        terminal.appendSystem("CPU Execution Trace (Last 64 instructions):");
        for(String s : arch.getTrace()) {
            if(s != null) terminal.appendMessage(s);
        }
    }
    private JMenu themeMenu(){
        JMenu m=mk("Theme");
        for(ThemeManager.ThemePreset p:ThemeManager.BUILT_IN){m.add(mi(p.name(),()->applyTheme(p)));}
        m.addSeparator();
        m.add(mi("Import Theme...",()->importTheme()));
        m.add(mi("Export Current Theme...",()->exportTheme()));
        return m;}
    private JMenu helpMenu(){JMenu m=mk("Help");m.add(mi("Keyboard Shortcuts",()->showShortcuts()));m.add(mi("About",()->JOptionPane.showMessageDialog(this,"j8085 Microprocessor Simulator\nPure Java Swing - Backstage Dark Theme","About",JOptionPane.INFORMATION_MESSAGE)));return m;}

    // == Toolbar ==
    private JPanel buildToolbar(){
        JPanel tb=new JPanel(new FlowLayout(FlowLayout.LEFT,6,4)); tb.setBackground(Theme.SURFACE_1); tb.setBorder(Theme.bottomBorder());
        assembleBtn=mkBtn("Assemble  F9",Theme.ACCENT,Theme.BASE); assembleBtn.addActionListener(e->bridge.assembleAndLoad()); tb.add(assembleBtn);
        tb.add(sep());
        JLabel lbl=new JLabel("Origin:"); lbl.setForeground(Theme.TEXT_DIM); lbl.setFont(Theme.FONT_LABEL); tb.add(lbl);
        startAddrField=new JTextField("2000",6); startAddrField.setBackground(Theme.SURFACE_2); startAddrField.setForeground(Theme.TEXT_PRIMARY); startAddrField.setCaretColor(Theme.ACCENT); startAddrField.setFont(Theme.FONT_MONO); startAddrField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER),BorderFactory.createEmptyBorder(3,6,3,6)));
        startAddrField.addActionListener(e->updateOrigin()); tb.add(startAddrField);
        return tb;}
    private void updateOrigin(){try{int a=Integer.parseInt(startAddrField.getText().trim(),16);editor.setStartAddress(a);}catch(NumberFormatException ignored){}}

    // == 3-pane Center ==
    private JSplitPane buildCenter(){
        leftSplit=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,dashboard,editor);
        leftSplit.setDividerLocation(270); leftSplit.setBorder(null); leftSplit.setDividerSize(4); leftSplit.setBackground(Theme.BORDER);
        JSplitPane main=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,leftSplit,terminal);
        main.setDividerLocation(1000); main.setBorder(null); main.setDividerSize(4); main.setBackground(Theme.BORDER);
        return main;}
    private void toggleDash(){leftSplit.setDividerLocation(leftSplit.getDividerLocation()>20?0:270);}
    private void toggleTerm(){/* handled by parent split */}

    // == Control Strip (far right) ==
    private JPanel buildControlStrip(){
        JPanel cs=new JPanel(); cs.setLayout(new BoxLayout(cs,BoxLayout.Y_AXIS)); cs.setBackground(Theme.SURFACE_1); cs.setBorder(Theme.leftBorder()); cs.setPreferredSize(new Dimension(46,0));
        playBtn =mkSymBtn("\u25B6",Theme.SUCCESS,"Play (F5)");  playBtn.addActionListener(e->bridge.play());
        pauseBtn=mkSymBtn("\u23F8",Theme.WARNING,"Pause");      pauseBtn.addActionListener(e->bridge.pause());
        stopBtn =mkSymBtn("\u25A0",Theme.ERROR,  "Stop");       stopBtn.addActionListener(e->bridge.stop());
        resetBtn=mkSymBtn("\u21BA",Theme.TEXT_SECONDARY,"Reset (F2)"); resetBtn.addActionListener(e->bridge.reset());
        pauseBtn.setEnabled(false); stopBtn.setEnabled(false);
        cs.add(Box.createVerticalStrut(20)); cs.add(playBtn); cs.add(Box.createVerticalStrut(4)); cs.add(pauseBtn); cs.add(Box.createVerticalStrut(4)); cs.add(stopBtn); cs.add(Box.createVerticalStrut(12)); cs.add(resetBtn); cs.add(Box.createVerticalGlue());
        getRootPane().registerKeyboardAction(e->bridge.assembleAndLoad(),KeyStroke.getKeyStroke("F9"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e->bridge.play(),KeyStroke.getKeyStroke("F5"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e->bridge.step(),KeyStroke.getKeyStroke("F10"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e->bridge.reset(),KeyStroke.getKeyStroke("F2"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        // [AG-FIX 2.2] Ctrl+S save shortcut
        getRootPane().registerKeyboardAction(e->saveFile(false),KeyStroke.getKeyStroke("control S"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        return cs;}

    private void onStart(){assembleBtn.setEnabled(false);playBtn.setEnabled(false);pauseBtn.setEnabled(true);stopBtn.setEnabled(true);}
    private void onStop(){assembleBtn.setEnabled(true);playBtn.setEnabled(true);pauseBtn.setEnabled(false);stopBtn.setEnabled(false);}

    // == Status Bar ==
    private JPanel buildStatus(){JPanel s=new JPanel(new FlowLayout(FlowLayout.LEFT,10,3));s.setBackground(Theme.SURFACE_1);s.setBorder(Theme.topBorder());JLabel l=new JLabel("j8085 Simulator  |  F9 Assemble  |  F5 Play  |  F10 Step  |  F2 Reset");l.setForeground(Theme.TEXT_DIM);l.setFont(Theme.FONT_LABEL);s.add(l);return s;}

    // == File Operations ==
    private void openFile(){JFileChooser fc=fc(".asm");if(fc.showOpenDialog(this)==JFileChooser.APPROVE_OPTION){File f=fc.getSelectedFile();editor.addNewTab(f.getName(),f);editor.loadIntoActive(f);}}
    // [AG-FIX 1.7] saveFile — only shows dialog for new/unsaved files or explicit Save As
    private void saveFile(boolean saveAs){
        if (!saveAs && editor.getActiveFile() != null) {
            editor.saveActive(editor.getActiveFile());
        } else {
            JFileChooser fc=fc(".asm");
            if (fc.showSaveDialog(this)==JFileChooser.APPROVE_OPTION)
                editor.saveActive(fc.getSelectedFile());
        }
    }
    // [AG-FIX 2.5] Intel HEX export
    private void exportHex(){
        String code=editor.getActiveCode();
        if(code.trim().isEmpty()){terminal.appendError("Nothing to export.");return;}
        try{
            int start=arch.getMemoryStart();
            int[] bytes=assembler.assembleToBuffer(code,start);
            JFileChooser fc=fc(".hex");
            if(fc.showSaveDialog(this)==JFileChooser.APPROVE_OPTION){
                File f=fc.getSelectedFile();
                if(!f.getName().contains("."))f=new File(f.getAbsolutePath()+".hex");
                try(java.io.FileWriter fw=new java.io.FileWriter(f)){
                    fw.write(IntelHexWriter.convert(bytes,start));
                }
                terminal.appendSystem("Exported "+bytes.length+" bytes to "+f.getName());
            }
        }catch(SimulatorException e){terminal.appendError("Export: "+e.getMessage());}
        catch(IOException e){terminal.appendError("File write: "+e.getMessage());}
    }
    private JFileChooser fc(String ext){JFileChooser fc=new JFileChooser();fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Assembly Files (*"+ext+")",ext.replace(".",""),"asm","bin","j8085theme"));return fc;}

    // == Theme Operations ==
    private void applyTheme(ThemeManager.ThemePreset p){
        currentTheme=p;ThemeManager.apply(p);setupDarkTheme();
        SwingUtilities.updateComponentTreeUI(this);
        dashboard.updateTheme(); editor.updateTheme(); terminal.updateTheme();
        terminal.appendSystem("Theme: "+p.name());
    } // [AG-FIX 1.8]
    private void importTheme(){JFileChooser fc=fc(".j8085theme");if(fc.showOpenDialog(this)==JFileChooser.APPROVE_OPTION){try{ThemeManager.ThemePreset p=ThemeManager.importFromFile(fc.getSelectedFile());applyTheme(p);}catch(IOException e){terminal.appendError("Theme import failed: "+e.getMessage());}}}
    private void exportTheme(){JFileChooser fc=fc(".j8085theme");if(fc.showSaveDialog(this)==JFileChooser.APPROVE_OPTION){try{ThemeManager.exportToFile(currentTheme,fc.getSelectedFile());terminal.appendSystem("Theme exported.");}catch(IOException e){terminal.appendError("Export failed: "+e.getMessage());}}} // [AG-FIX 1.8]
    private void showShortcuts(){JOptionPane.showMessageDialog(this,"F9  - Assemble\nF5  - Play/Run\nF10 - Step\nF2  - Reset\nCtrl+S - Save","Shortcuts",JOptionPane.INFORMATION_MESSAGE);}

    // == Helpers ==
    private JButton mkBtn(String t,Color bg,Color fg){JButton b=new JButton(t);b.setBackground(bg);b.setForeground(fg);b.setFont(Theme.FONT_UI_B);b.setFocusPainted(false);b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER),BorderFactory.createEmptyBorder(5,10,5,10)));return b;}
    private JButton mkSymBtn(String sym,Color fg,String tip){JButton b=new JButton(sym);b.setForeground(fg);b.setBackground(Theme.SURFACE_1);b.setFont(new Font(Font.SANS_SERIF,Font.BOLD,16));b.setFocusPainted(false);b.setBorderPainted(false);b.setContentAreaFilled(false);b.setToolTipText(tip);b.setAlignmentX(Component.CENTER_ALIGNMENT);b.setMaximumSize(new Dimension(40,40));return b;}
    private JSeparator sep(){JSeparator s=new JSeparator(JSeparator.VERTICAL);s.setPreferredSize(new Dimension(1,22));s.setForeground(Theme.BORDER);return s;}
    public void setArchitecture(Architecture arch){this.arch=arch;bridge.setArchitecture(arch);}

    // [AG-FIX 2.4] Memory reconfiguration
    private void showMemConfig(){
        MemoryConfigDialog dlg=new MemoryConfigDialog(this);
        dlg.setVisible(true);
        if(!dlg.isUserConfirmed())return;
        Architecture newArch;
        if(dlg.isDefaultSelected()){
            newArch=new Architecture();
        }else{
            try{newArch=new Architecture(dlg.getStartAddress(),dlg.getEndAddress());}
            catch(SimulatorException ex){terminal.appendError("Invalid range: "+ex.getMessage());return;}
        }
        this.arch=newArch;
        bridge.setArchitecture(newArch);
        bridge.reset();
        terminal.appendSystem("Memory: 0x"+String.format("%04X",newArch.getMemoryStart())+"-0x"+String.format("%04X",newArch.getMemoryEnd()));
    }

    // [AG-FIX 2.6] Dirty-file exit guard
    private void confirmExit(){
        if(editor.hasDirtyTabs()){
            int r=JOptionPane.showConfirmDialog(this,"Unsaved changes. Exit anyway?","Confirm Exit",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);
            if(r!=JOptionPane.YES_OPTION)return;
        }
        dispose(); System.exit(0);
    }
}
