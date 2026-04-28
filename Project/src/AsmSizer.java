import java.util.HashMap; import java.util.Map;
public final class AsmSizer {
    private AsmSizer(){}
    private static final Map<String,Integer> SZ=new HashMap<>();
    static {
        for(String m:new String[]{"NOP","HLT","RLC","RRC","RAL","RAR","DAA","CMA","STC","CMC","XCHG","PCHL","SPHL","XTHL","RET","RNZ","RZ","RNC","RC","RPO","RPE","RP","RM","EI","DI","RIM","SIM","MOV","ADD","ADC","SUB","SBB","ANA","XRA","ORA","CMP","INR","DCR","INX","DCX","DAD","PUSH","POP","RST","LDAX","STAX"}) SZ.put(m,1); // [AG-FIX 1.4] RST, LDAX, STAX are 1-byte
        for(String m:new String[]{"MVI","ADI","ACI","SUI","SBI","ANI","ORI","XRI","CPI","IN","OUT"}) SZ.put(m,2); // [AG-FIX 1.4] removed LDAX/STAX
        for(String m:new String[]{"LXI","LDA","STA","LHLD","SHLD","JMP","JNZ","JZ","JNC","JC","JPO","JPE","JP","JM","CALL","CNZ","CZ","CNC","CC","CPO","CPE","CP","CM"}) SZ.put(m,3);
    }
    public static int getSize(String raw){
        if(raw==null)return 0;
        String line=strip(raw).trim();
        if(line.isEmpty())return 0;
        if(line.contains(":")){line=line.substring(line.indexOf(':')+1).trim();if(line.isEmpty())return 0;}
        String u=line.toUpperCase();
        if(u.startsWith("ORG")||u.startsWith("END")||u.startsWith("EQU")||u.startsWith("DB")||u.startsWith("DW")||u.startsWith("DS"))return 0;
        return SZ.getOrDefault(line.split("\\s+")[0].toUpperCase(),1);
    }
    private static String strip(String l){int i=l.indexOf(';');return i>=0?l.substring(0,i):l;}
}
