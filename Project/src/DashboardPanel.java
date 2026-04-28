import javax.swing.*; import javax.swing.table.*; import java.awt.*; import java.awt.event.*; import java.util.*;
public class DashboardPanel extends JPanel {
    private final Map<String,JLabel> regLabels=new HashMap<>();
    private LEDIndicator ledS,ledZ,ledAC,ledP,ledCY;
    private DefaultTableModel memModel; private JTable memTable; private JScrollPane memScroll;
    private JTextArea portsArea; // [AG-FIX 3.5] I/O Ports UI
    private int lastPC=-1;
    private volatile int customStart=-1, customEnd=-1; // [AG-FIX 1.1] volatile for cross-thread read
    // [AG-FIX 2.3] Breakpoint context
    private Set<Integer> breakpoints=Collections.emptySet();
    private java.util.function.IntConsumer bpToggle;

    public DashboardPanel(){
        setLayout(new BorderLayout(0,6)); setBackground(Theme.SURFACE_1); setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        JPanel top=new JPanel(new BorderLayout(0,6)); top.setBackground(Theme.SURFACE_1);
        JPanel mid=new JPanel(new GridLayout(1,2,6,0)); mid.setBackground(Theme.SURFACE_1); // [AG-FIX 3.5] side-by-side
        mid.add(buildFlags()); mid.add(buildPorts());
        top.add(buildRegs(),BorderLayout.NORTH); top.add(mid,BorderLayout.SOUTH);
        add(top,BorderLayout.NORTH); add(buildMem(),BorderLayout.CENTER); add(buildGoTo(),BorderLayout.SOUTH);
    }

    // == Registers ==
    private JPanel buildRegs(){
        JPanel p=card("REGISTERS");
        JPanel grid=new JPanel(new GridLayout(9,2,4,3)); grid.setBackground(Theme.SURFACE_2); grid.setBorder(BorderFactory.createEmptyBorder(4,0,2,0));
        for(String r:new String[]{"A","B","C","D","E","H","L","PC","SP"}){
            JLabel nm=new JLabel(r); nm.setForeground(Theme.TEXT_SECONDARY); nm.setFont(Theme.FONT_UI_B);
            JLabel vl=new JLabel(r.equals("PC")||r.equals("SP")?"0000":"00"); vl.setForeground(Theme.TEXT_PRIMARY); vl.setFont(Theme.FONT_MONO);
            regLabels.put(r,vl); grid.add(nm); grid.add(vl);
        }
        p.add(grid,BorderLayout.CENTER); return p;
    }

    // == Flags as LEDs ==
    private JPanel buildFlags(){
        JPanel p=card("FLAGS");
        JPanel row=new JPanel(new GridLayout(1,5,6,0)); row.setBackground(Theme.SURFACE_2); row.setBorder(BorderFactory.createEmptyBorder(4,0,4,0));
        ledS=new LEDIndicator("S"); ledZ=new LEDIndicator("Z"); ledAC=new LEDIndicator("AC"); ledP=new LEDIndicator("P"); ledCY=new LEDIndicator("CY");
        row.add(ledS); row.add(ledZ); row.add(ledAC); row.add(ledP); row.add(ledCY);
        p.add(row,BorderLayout.CENTER); return p;
    }

    // [AG-FIX 3.5] == I/O Ports ==
    private JPanel buildPorts(){
        JPanel p=card("I/O PORTS");
        portsArea=new JTextArea(3,8); portsArea.setEditable(false);
        portsArea.setFont(Theme.FONT_MONO); portsArea.setBackground(Theme.SURFACE_2); portsArea.setForeground(Theme.TEXT_PRIMARY);
        JScrollPane sp=new JScrollPane(portsArea); sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getVerticalScrollBar().setUI(new ThinScrollBarUI()); sp.getHorizontalScrollBar().setUI(new ThinScrollBarUI());
        p.add(sp,BorderLayout.CENTER); return p;
    }

    // == Memory Dump ==
    private JPanel buildMem(){
        JPanel p=card("MEMORY  PC+-16");
        memModel=new DefaultTableModel(new String[]{"Addr","Hex","Chr"},0){public boolean isCellEditable(int r,int c){return false;}};
        memTable=new JTable(memModel); memTable.setFont(Theme.FONT_MONO); memTable.setBackground(Theme.SURFACE_2); memTable.setForeground(Theme.TEXT_PRIMARY);
        memTable.setGridColor(Theme.BORDER); memTable.setRowHeight(20); memTable.setSelectionBackground(Theme.ACCENT_DIM); memTable.setSelectionForeground(Theme.ACCENT);
        memTable.getTableHeader().setBackground(Theme.SURFACE_1); memTable.getTableHeader().setForeground(Theme.TEXT_SECONDARY); memTable.getTableHeader().setFont(Theme.FONT_LABEL);
        memTable.getTableHeader().setReorderingAllowed(false);
        memTable.getColumnModel().getColumn(0).setPreferredWidth(52); memTable.getColumnModel().getColumn(1).setPreferredWidth(38); memTable.getColumnModel().getColumn(2).setPreferredWidth(28);
        // [AG-FIX 2.3] Breakpoint-aware cell renderer
        BPRenderer bpr=new BPRenderer();
        for(int i=0;i<3;i++) memTable.getColumnModel().getColumn(i).setCellRenderer(bpr);
        // [AG-FIX 2.3] Double-click to toggle breakpoint
        memTable.addMouseListener(new java.awt.event.MouseAdapter(){
            public void mouseClicked(java.awt.event.MouseEvent e){
                if(e.getClickCount()==2&&bpToggle!=null){
                    int row=memTable.rowAtPoint(e.getPoint());
                    if(row>=0){
                        try{bpToggle.accept(Integer.parseInt((String)memModel.getValueAt(row,0),16));}
                        catch(NumberFormatException ignored){}
                    }
                }
            }
        });
        memScroll=new JScrollPane(memTable); memScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER)); memScroll.getViewport().setBackground(Theme.SURFACE_2);
        memScroll.getVerticalScrollBar().setUI(new ThinScrollBarUI()); memScroll.getHorizontalScrollBar().setUI(new ThinScrollBarUI());
        p.add(memScroll,BorderLayout.CENTER); return p;
    }

    // [AG-FIX 2.3] Wire breakpoint toggle callback + live set reference
    public void setBreakpointContext(Set<Integer> bps, java.util.function.IntConsumer toggle){
        this.breakpoints=bps; this.bpToggle=toggle;
    }

    // == Custom Memory Range ==
    private JPanel buildGoTo(){
        JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,4,4)); p.setBackground(Theme.SURFACE_1);
        JLabel lbl=new JLabel("Goto:"); lbl.setForeground(Theme.TEXT_SECONDARY); lbl.setFont(Theme.FONT_LABEL);
        JTextField startF=styledField("0000",5); JLabel dash=new JLabel("-"); dash.setForeground(Theme.TEXT_SECONDARY);
        JTextField endF=styledField("001F",5);
        JButton go=new JButton("Go"); go.setFont(Theme.FONT_LABEL); go.setBackground(Theme.ACCENT_DIM); go.setForeground(Theme.ACCENT); go.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER),BorderFactory.createEmptyBorder(3,8,3,8))); go.setFocusPainted(false);
        go.addActionListener(e->{try{customStart=Integer.parseInt(startF.getText().trim(),16);customEnd=Integer.parseInt(endF.getText().trim(),16);lastPC=-1;}catch(NumberFormatException ignored){}});
        p.add(lbl); p.add(startF); p.add(dash); p.add(endF); p.add(go); return p;
    }

    private JTextField styledField(String def,int cols){JTextField f=new JTextField(def,cols);f.setBackground(Theme.SURFACE_2);f.setForeground(Theme.TEXT_PRIMARY);f.setCaretColor(Theme.ACCENT);f.setFont(Theme.FONT_MONO);f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER),BorderFactory.createEmptyBorder(2,4,2,4)));return f;}

    // == Refresh ==
    // [AG-FIX 1.1] Thread-safe refresh from immutable snapshot
    public void refresh(CPUSnapshot snap){
        setReg("A",snap.a,2);
        setReg("B",snap.b,2); setReg("C",snap.c,2);
        setReg("D",snap.d,2); setReg("E",snap.e,2);
        setReg("H",snap.h,2); setReg("L",snap.l,2);
        setReg("PC",snap.pc,4); setReg("SP",snap.sp,4);
        ledS.setOn(snap.fS); ledZ.setOn(snap.fZ); ledAC.setOn(snap.fAC);
        ledP.setOn(snap.fP); ledCY.setOn(snap.fCY);
        // [AG-FIX 3.5] Update I/O ports display
        if(snap.ioPorts.isEmpty()) portsArea.setText("No active ports");
        else {
            StringBuilder sb=new StringBuilder();
            snap.ioPorts.forEach((p,v)->sb.append(String.format("Port %02XH: %02XH\n",p,v)));
            portsArea.setText(sb.toString().trim());
        }
        if(snap.pc!=lastPC){lastPC=snap.pc;rebuildMem(snap);}
    }

    // [AG-FIX 3.4] Theme refresh
    public void updateTheme() {
        setBackground(Theme.SURFACE_1);
        memTable.setBackground(Theme.SURFACE_2); memTable.setForeground(Theme.TEXT_PRIMARY);
        memTable.getTableHeader().setBackground(Theme.SURFACE_1); memTable.getTableHeader().setForeground(Theme.TEXT_SECONDARY);
        portsArea.setBackground(Theme.SURFACE_2); portsArea.setForeground(Theme.TEXT_PRIMARY);
        // Let component tree UI update handle the rest, but force repaint
        repaint();
    }

    // Legacy overload -- builds snapshot internally (less safe but keeps old callers working)
    public void refresh(Architecture arch){
        try{
            int[] range=getMemRange(arch.getProgramCounter());
            refresh(arch.getCPUSnapshot(range[0],range[1]));
        }catch(Exception e){System.err.println("[Dashboard] "+e.getMessage());}
    }

    // [AG-FIX 1.1] Expose memory view range for EmulatorBridge
    public int[] getMemRange(int pc){
        int start=customStart>=0?customStart:Math.max(0,pc-8);
        int end=customEnd>start?customEnd:Math.min(0xFFFF,pc+24);
        return new int[]{start,end};
    }

    private void setReg(String r,int v,int digits){JLabel l=regLabels.get(r);if(l==null)return;String hex=String.format("%0"+digits+"X",v);if(!l.getText().equals(hex)){l.setText(hex);l.setForeground(Theme.ACCENT);}else{l.setForeground(Theme.TEXT_PRIMARY);}}

    private void rebuildMem(CPUSnapshot snap){
        memModel.setRowCount(0);
        int pcRow=-1;
        for(int i=0;i<snap.mem.length;i++){
            int addr=snap.memBase+i;
            int v=snap.mem[i];
            memModel.addRow(new Object[]{String.format("%04X",addr),String.format("%02X",v),(v>=32&&v<=126)?String.valueOf((char)v):".",});
            if(addr==snap.pc)pcRow=i;
        }
        if(pcRow>=0){memTable.setRowSelectionInterval(pcRow,pcRow);memTable.scrollRectToVisible(memTable.getCellRect(pcRow,0,true));}
    }

    private JPanel card(String title){JPanel p=new JPanel(new BorderLayout(0,4));p.setBackground(Theme.SURFACE_2);p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER),BorderFactory.createEmptyBorder(8,8,8,8)));JLabel lbl=new JLabel(title);lbl.setForeground(Theme.TEXT_SECONDARY);lbl.setFont(Theme.FONT_LABEL);p.add(lbl,BorderLayout.NORTH);return p;}

    // == LED Indicator ==
    private static class LEDIndicator extends JPanel {
        private boolean on=false; private final String label;
        LEDIndicator(String label){this.label=label;setBackground(Theme.SURFACE_2);setPreferredSize(new Dimension(36,46));setToolTipText(fullName(label));}
        void setOn(boolean v){if(on!=v){on=v;repaint();}}
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int cx=getWidth()/2,cy=getHeight()/2-6,d=16;
            Color ledColor=on?Theme.ACCENT:new Color(Theme.ACCENT.getRed()/5,Theme.ACCENT.getGreen()/5,Theme.ACCENT.getBlue()/5);
            if(on){g2.setColor(new Color(Theme.ACCENT.getRed(),Theme.ACCENT.getGreen(),Theme.ACCENT.getBlue(),40));g2.fillOval(cx-d/2-4,cy-d/2-4,d+8,d+8);}
            g2.setColor(ledColor); g2.fillOval(cx-d/2,cy-d/2,d,d);
            g2.setColor(Theme.BORDER); g2.drawOval(cx-d/2,cy-d/2,d,d);
            if(on){g2.setColor(new Color(255,255,255,80));g2.fillOval(cx-d/2+3,cy-d/2+2,d/3,d/4);}
            g2.setColor(on?Theme.TEXT_PRIMARY:Theme.TEXT_DIM); g2.setFont(Theme.FONT_LABEL); FontMetrics fm=g2.getFontMetrics(); g2.drawString(label,cx-fm.stringWidth(label)/2,cy+d/2+14);
            g2.dispose();
        }
        private static String fullName(String s){if(s.equals("S"))return "Sign Flag";if(s.equals("Z"))return "Zero Flag";if(s.equals("AC"))return "Auxiliary Carry";if(s.equals("P"))return "Parity Flag";if(s.equals("CY"))return "Carry Flag";return s;}
    }

    // [AG-FIX 2.3] Breakpoint-aware cell renderer
    private class BPRenderer extends DefaultTableCellRenderer {
        private static final Color BP_BG=new Color(0x3A1818);
        public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,boolean focus,int row,int col){
            super.getTableCellRendererComponent(t,v,sel,focus,row,col);
            setHorizontalAlignment(col==0?LEFT:CENTER);
            setFont(Theme.FONT_MONO);
            boolean isBP=false;
            if(row<memModel.getRowCount()){
                try{isBP=breakpoints.contains(Integer.parseInt((String)memModel.getValueAt(row,0),16));}
                catch(Exception ignored){}
            }
            if(sel){setBackground(Theme.ACCENT_DIM);setForeground(Theme.ACCENT);}
            else if(isBP){setBackground(BP_BG);setForeground(Theme.ERROR);}
            else{setBackground(Theme.SURFACE_2);setForeground(Theme.TEXT_PRIMARY);}
            return this;
        }
    }
}
