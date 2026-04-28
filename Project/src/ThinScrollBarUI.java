import javax.swing.plaf.basic.BasicScrollBarUI; import javax.swing.*; import java.awt.*;
public class ThinScrollBarUI extends BasicScrollBarUI {
    @Override protected void configureScrollBarColors(){thumbColor=Theme.BORDER;trackColor=Theme.SURFACE_1;thumbHighlightColor=Theme.TEXT_DIM;thumbDarkShadowColor=Theme.BASE;}
    @Override protected JButton createDecreaseButton(int o){return zb();}
    @Override protected JButton createIncreaseButton(int o){return zb();}
    private JButton zb(){JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));return b;}
    @Override protected void paintTrack(Graphics g,JComponent c,Rectangle r){g.setColor(Theme.SURFACE_1);g.fillRect(r.x,r.y,r.width,r.height);}
    @Override protected void paintThumb(Graphics g,JComponent c,Rectangle r){
        if(r.isEmpty())return;
        Graphics2D g2=(Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(isDragging?Theme.TEXT_SECONDARY:Theme.BORDER);
        g2.fillRoundRect(r.x+2,r.y+2,r.width-4,r.height-4,4,4);g2.dispose();
    }
    @Override public Dimension getPreferredSize(JComponent c){return scrollbar.getOrientation()==JScrollBar.VERTICAL?new Dimension(7,0):new Dimension(0,7);}
}
