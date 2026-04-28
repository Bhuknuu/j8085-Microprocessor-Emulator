import java.awt.Color; import java.awt.Font; import javax.swing.BorderFactory; import javax.swing.border.Border;
public final class Theme { private Theme(){}
  // [AG-FIX 1.2] volatile: ThemeManager.apply() writes on caller thread; Swing repaints read on EDT
  public static volatile Color BASE=new Color(0x121212),SURFACE_1=new Color(0x171717),SURFACE_2=new Color(0x1E1E1E),SURFACE_3=new Color(0x252525);
  public static volatile Color TEXT_PRIMARY=new Color(0xE2E2E2),TEXT_SECONDARY=new Color(0x828282),TEXT_DIM=new Color(0x606060);
  public static volatile Color ACCENT=new Color(0x9BF0E1),ACCENT_DIM=new Color(0x2A4A45),SUCCESS=new Color(0x4ADB7A),WARNING=new Color(0xDBA64A),ERROR=new Color(0xDB4A4A),BORDER=new Color(0x333333);
  public static final Font FONT_UI=new Font(Font.SANS_SERIF,Font.PLAIN,12),FONT_UI_B=new Font(Font.SANS_SERIF,Font.BOLD,12),FONT_LABEL=new Font(Font.SANS_SERIF,Font.BOLD,11),FONT_MONO=new Font(Font.MONOSPACED,Font.PLAIN,13),FONT_EDITOR=new Font(Font.MONOSPACED,Font.PLAIN,14);
  public static Border card(){return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),BorderFactory.createEmptyBorder(8,8,8,8));}
  public static Border padded(int t,int l,int b,int r){return BorderFactory.createEmptyBorder(t,l,b,r);}
  public static Border topBorder(){return BorderFactory.createMatteBorder(1,0,0,0,BORDER);}
  public static Border bottomBorder(){return BorderFactory.createMatteBorder(0,0,1,0,BORDER);}
  public static Border leftBorder(){return BorderFactory.createMatteBorder(0,1,0,0,BORDER);}
}
