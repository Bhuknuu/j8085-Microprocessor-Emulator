import javax.swing.SwingUtilities;
public class Main {
    public static void main(String[] args){
        MainFrame.setupDarkTheme();
        SwingUtilities.invokeLater(()->{
            MemoryConfigDialog dlg=new MemoryConfigDialog(null); dlg.setVisible(true);
            if(!dlg.isUserConfirmed()){System.exit(0);return;}
            try{
                Architecture arch=dlg.isDefaultSelected()?new Architecture():new Architecture(dlg.getStartAddress(),dlg.getEndAddress());
                Assembler asm=new Assembler(arch);
                MainFrame f=new MainFrame(arch,asm); f.setVisible(true);
            }catch(SimulatorException e){System.err.println("[Main] "+e.getMessage());System.exit(1);}
        });
    }
}
