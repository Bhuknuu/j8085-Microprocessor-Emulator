// [AG-FIX 1.1] Immutable CPU state for thread-safe EDT reads
public final class CPUSnapshot {
    public final int pc,sp,a,b,c,d,e,h,l;
    public final boolean fS,fZ,fAC,fP,fCY,halted;
    public final int[] mem;
    public final int memBase;
    // [AG-FIX 3.5] I/O Ports snapshot
    public final java.util.Map<Integer, Integer> ioPorts;
    public CPUSnapshot(int pc,int sp,int a,int b,int c,int d,int e,int h,int l,
            boolean fS,boolean fZ,boolean fAC,boolean fP,boolean fCY,boolean halted,
            int[] mem,int memBase, java.util.Map<Integer, Integer> ioPorts){
        this.pc=pc;this.sp=sp;this.a=a;this.b=b;this.c=c;this.d=d;this.e=e;this.h=h;this.l=l;
        this.fS=fS;this.fZ=fZ;this.fAC=fAC;this.fP=fP;this.fCY=fCY;this.halted=halted;
        this.mem=mem;this.memBase=memBase;
        this.ioPorts=ioPorts;
    }
}
