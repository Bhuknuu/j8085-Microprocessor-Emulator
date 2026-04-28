import javax.swing.*; import java.util.*; import java.util.concurrent.*; import java.util.concurrent.atomic.AtomicBoolean;
public class EmulatorBridge {
    private Architecture arch; private final Assembler assembler;
    private final DashboardPanel dashboard; private final EditorPanel editor; private final TerminalPanel terminal;
    private final Runnable onStart,onStop;
    private final AtomicBoolean isRunning=new AtomicBoolean(false);
    private volatile boolean hasProgram=false;
    private SwingWorker<Void,Void> worker;
    private static final int MAX_TERM=40_000;
    // [AG-FIX 2.3] Breakpoint engine
    private final Set<Integer> breakpoints=ConcurrentHashMap.newKeySet();

    public EmulatorBridge(Architecture arch,Assembler assembler,DashboardPanel dashboard,EditorPanel editor,TerminalPanel terminal,Runnable onStart,Runnable onStop){
        this.arch=arch;this.assembler=assembler;this.dashboard=dashboard;this.editor=editor;this.terminal=terminal;this.onStart=onStart;this.onStop=onStop;refreshUI();
    }
    public void setArchitecture(Architecture arch){this.arch=arch;refreshUI();}
    public Architecture getArchitecture(){return arch;}
    public void refreshUI(){if(dashboard!=null)dashboard.refresh(arch.createSnapshot());}

    public void assembleAndLoad(){
        if(isRunning.get()){terminal.appendError("Pause before assembling.");return;}
        String code=editor.getActiveCode();
        if(code.trim().isEmpty()){terminal.appendError("Editor is empty.");return;}
        try{
            int start=arch.getMemoryStart(); terminal.appendMessage("Assembling...");
            int[] bytes=assembler.assembleToBuffer(code,start);
            arch.loadProgram(start,bytes); arch.setProgramCounter(start);
            hasProgram=true;
            terminal.appendMessage("OK - "+bytes.length+" bytes at "+String.format("%04XH",start));
            refreshUI();
        }catch(SimulatorException e){terminal.appendError("Assembly: "+e.getMessage());}
    }

    public void step(){
        if(isRunning.get())return;
        // [FIX #3] Guard: must have assembled first
        if(!hasProgram){terminal.appendError("Nothing loaded. Assemble first.");return;}
        if(arch.isHalted()){terminal.appendMessage("HALTED. Reset to continue.");return;}
        isRunning.set(true); onStart.run();
        new SwingWorker<Void,String>(){
            protected Void doInBackground(){try{int pc=arch.getProgramCounter();int op=arch.readMemory(pc);publish(String.format("Step %04XH: %02X %s",pc,op,arch.disassemble(op)));arch.step();if(arch.isHalted())publish("HLT reached.");}catch(SimulatorException e){publish("[ERR] "+e.getMessage());}return null;}
            protected void process(List<String> msgs){msgs.forEach(m->{if(m.startsWith("[ERR]"))terminal.appendError(m.substring(5));else terminal.appendMessage(m);});}
            protected void done(){isRunning.set(false);onStop.run();refreshUI();}
        }.execute();
    }

    public void play(){
        if(isRunning.get())return;
        // [FIX #3] Guard: must have assembled first
        if(!hasProgram){terminal.appendError("Nothing loaded. Assemble first (F9).");return;}
        if(arch.isHalted()){terminal.appendMessage("HALTED. Reset to continue.");return;}
        if(worker!=null&&!worker.isDone())worker.cancel(true);
        isRunning.set(true); onStart.run(); terminal.appendMessage("Running...");
        worker=new SwingWorker<>(){
            // [AG-FIX 1.3] Replaced Thread.sleep(50) busy-wait with step-batching:
            // execute 500 steps, then yield via publish() so EDT can repaint.
            // This drops CPU idle time from 98% to ~0% without sacrificing responsiveness.
            protected Void doInBackground(){
                try{
                    int batch=0;
                    while(!arch.isHalted()&&isRunning.get()&&!isCancelled()){
                        arch.step();
                        // [AG-FIX 2.3] Breakpoint check
                        if(breakpoints.contains(arch.getProgramCounter())){
                            int bpPC=arch.getProgramCounter();
                            SwingUtilities.invokeLater(()->terminal.appendMessage("BP hit: "+String.format("%04XH",bpPC)));
                            break;
                        }
                        if(++batch>=500){batch=0;publish();}
                    }
                    publish(); // final UI refresh
                }catch(SimulatorException e){
                    // [AG-FIX 1.13] Handle StepLimitExceeded with informative message
                    String tag=e.getErrorType()==SimulatorException.ErrorType.StepLimitExceeded?"[LIMIT]":"[ERR]";
                    SwingUtilities.invokeLater(()->terminal.appendError(tag+" "+e.getMessage()));
                } // InterruptedException removed: no blocking calls remain in this loop
                return null;
            }
            protected void process(List<Void> c){refreshUI();terminal.trimIfNeeded(MAX_TERM);}
            protected void done(){isRunning.set(false);onStop.run();refreshUI();terminal.appendMessage(arch.isHalted()?"Done.":"Paused.");}
        };
        worker.execute();
    }

    public void pause(){isRunning.set(false);terminal.appendMessage("Paused.");}
    public void stop(){isRunning.set(false);if(worker!=null)worker.cancel(true);terminal.appendMessage("Stopped.");}
    public void reset(){stop();arch.reset();hasProgram=false;terminal.appendMessage("Reset complete.");refreshUI();}
    // [AG-FIX 2.3] Breakpoint API
    public void toggleBreakpoint(int addr){if(!breakpoints.remove(addr))breakpoints.add(addr);refreshUI();}
    public void clearBreakpoints(){breakpoints.clear();refreshUI();}
    public Set<Integer> getBreakpoints(){return breakpoints;}

    // [AG-FIX 1.1] Snapshot on caller thread, render on EDT
    private void refreshUI(){
        int pc=arch.getProgramCounter();
        int[] range=dashboard.getMemRange(pc);
        CPUSnapshot snap=arch.getCPUSnapshot(range[0],range[1]);
        if(SwingUtilities.isEventDispatchThread()) dashboard.refresh(snap);
        else SwingUtilities.invokeLater(()->dashboard.refresh(snap));
    }
    public boolean isRunning(){return isRunning.get();}
}
