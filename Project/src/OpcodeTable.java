import java.util.HashMap;
import java.util.Map;

// OpcodeTable: Complete 8085 opcode lookup (all 256 entries)
public class OpcodeTable {
    public static class OpcodeEntry {
        public final int opcode;
        public final String mnemonic;
        public final int byteSize;
        public final String category;

        public OpcodeEntry(int opcode, String mnemonic, int byteSize, String category) {
            this.opcode = opcode;
            this.mnemonic = mnemonic;
            this.byteSize = byteSize;
            this.category = category;
        }

        @Override
        public String toString() {
            return String.format("0x%02X: %-12s (%d byte%s) [%s]",
                    opcode, mnemonic, byteSize, byteSize > 1 ? "s" : "", category);
        }
    }

    private static final HashMap<Integer, OpcodeEntry> table = new HashMap<>();
    private static final HashMap<String, Integer> mnemonicMap = new HashMap<>();
    private static final String[] REG_NAMES = {"B", "C", "D", "E", "H", "L", "M", "A"};

    static {
        // Machine Control
        addEntry(0x00, "NOP", 1, "MACHINE_CONTROL");
        addEntry(0x76, "HLT", 1, "MACHINE_CONTROL");
        addEntry(0xFB, "EI", 1, "MACHINE_CONTROL");
        addEntry(0xF3, "DI", 1, "MACHINE_CONTROL");
        addEntry(0x20, "RIM", 1, "MACHINE_CONTROL");
        addEntry(0x30, "SIM", 1, "MACHINE_CONTROL");

        // Data Transfer — LXI
        addEntry(0x01, "LXI B", 3, "DATA_TRANSFER");
        addEntry(0x11, "LXI D", 3, "DATA_TRANSFER");
        addEntry(0x21, "LXI H", 3, "DATA_TRANSFER");
        addEntry(0x31, "LXI SP", 3, "DATA_TRANSFER");

        // Data Transfer — STAX/LDAX
        addEntry(0x02, "STAX B", 1, "DATA_TRANSFER");
        addEntry(0x12, "STAX D", 1, "DATA_TRANSFER");
        addEntry(0x0A, "LDAX B", 1, "DATA_TRANSFER");
        addEntry(0x1A, "LDAX D", 1, "DATA_TRANSFER");

        // Data Transfer — MVI (8 variants)
        for (int d = 0; d < 8; d++)
            addEntry(0x06 | (d << 3), "MVI " + REG_NAMES[d], 2, "DATA_TRANSFER");

        // Data Transfer — Direct addressing
        addEntry(0x32, "STA", 3, "DATA_TRANSFER");
        addEntry(0x3A, "LDA", 3, "DATA_TRANSFER");
        addEntry(0x22, "SHLD", 3, "DATA_TRANSFER");
        addEntry(0x2A, "LHLD", 3, "DATA_TRANSFER");
        addEntry(0xEB, "XCHG", 1, "DATA_TRANSFER");

        // Data Transfer — MOV (63 variants, skip 0x76 = HLT)
        for (int d = 0; d < 8; d++)
            for (int s = 0; s < 8; s++) {
                int opcode = 0x40 | (d << 3) | s;
                if (opcode == 0x76) continue;
                addEntry(opcode, "MOV " + REG_NAMES[d] + "," + REG_NAMES[s], 1, "DATA_TRANSFER");
            }

        // Arithmetic — INR/DCR (8 variants each)
        for (int d = 0; d < 8; d++) {
            addEntry(0x04 | (d << 3), "INR " + REG_NAMES[d], 1, "ARITHMETIC");
            addEntry(0x05 | (d << 3), "DCR " + REG_NAMES[d], 1, "ARITHMETIC");
        }

        // Arithmetic — INX/DCX/DAD (4 pairs: B, D, H, SP)
        String[] pairNames = {"B", "D", "H", "SP"};
        for (int rp = 0; rp < 4; rp++) {
            addEntry(0x03 | (rp << 4), "INX " + pairNames[rp], 1, "ARITHMETIC");
            addEntry(0x0B | (rp << 4), "DCX " + pairNames[rp], 1, "ARITHMETIC");
            addEntry(0x09 | (rp << 4), "DAD " + pairNames[rp], 1, "ARITHMETIC");
        }

        // Arithmetic — ADD/ADC/SUB/SBB (8 variants each)
        for (int s = 0; s < 8; s++) {
            addEntry(0x80 | s, "ADD " + REG_NAMES[s], 1, "ARITHMETIC");
            addEntry(0x88 | s, "ADC " + REG_NAMES[s], 1, "ARITHMETIC");
            addEntry(0x90 | s, "SUB " + REG_NAMES[s], 1, "ARITHMETIC");
            addEntry(0x98 | s, "SBB " + REG_NAMES[s], 1, "ARITHMETIC");
        }

        // Arithmetic — Immediate + DAA
        addEntry(0x27, "DAA", 1, "ARITHMETIC");
        addEntry(0xC6, "ADI", 2, "ARITHMETIC");
        addEntry(0xCE, "ACI", 2, "ARITHMETIC");
        addEntry(0xD6, "SUI", 2, "ARITHMETIC");
        addEntry(0xDE, "SBI", 2, "ARITHMETIC");

        // Logical — ANA/XRA/ORA/CMP (8 variants each)
        for (int s = 0; s < 8; s++) {
            addEntry(0xA0 | s, "ANA " + REG_NAMES[s], 1, "LOGICAL");
            addEntry(0xA8 | s, "XRA " + REG_NAMES[s], 1, "LOGICAL");
            addEntry(0xB0 | s, "ORA " + REG_NAMES[s], 1, "LOGICAL");
            addEntry(0xB8 | s, "CMP " + REG_NAMES[s], 1, "LOGICAL");
        }

        // Logical — Immediate
        addEntry(0xE6, "ANI", 2, "LOGICAL");
        addEntry(0xEE, "XRI", 2, "LOGICAL");
        addEntry(0xF6, "ORI", 2, "LOGICAL");
        addEntry(0xFE, "CPI", 2, "LOGICAL");

        // Logical — Rotate/Complement
        addEntry(0x07, "RLC", 1, "LOGICAL");
        addEntry(0x0F, "RRC", 1, "LOGICAL");
        addEntry(0x17, "RAL", 1, "LOGICAL");
        addEntry(0x1F, "RAR", 1, "LOGICAL");
        addEntry(0x2F, "CMA", 1, "LOGICAL");
        addEntry(0x37, "STC", 1, "LOGICAL");
        addEntry(0x3F, "CMC", 1, "LOGICAL");

        // Branching — JMP/Jcc
        addEntry(0xC3, "JMP", 3, "BRANCHING");
        addEntry(0xC2, "JNZ", 3, "BRANCHING");
        addEntry(0xCA, "JZ", 3, "BRANCHING");
        addEntry(0xD2, "JNC", 3, "BRANCHING");
        addEntry(0xDA, "JC", 3, "BRANCHING");
        addEntry(0xE2, "JPO", 3, "BRANCHING");
        addEntry(0xEA, "JPE", 3, "BRANCHING");
        addEntry(0xF2, "JP", 3, "BRANCHING");
        addEntry(0xFA, "JM", 3, "BRANCHING");

        // Branching — CALL/Ccc
        addEntry(0xCD, "CALL", 3, "BRANCHING");
        addEntry(0xC4, "CNZ", 3, "BRANCHING");
        addEntry(0xCC, "CZ", 3, "BRANCHING");
        addEntry(0xD4, "CNC", 3, "BRANCHING");
        addEntry(0xDC, "CC", 3, "BRANCHING");
        addEntry(0xE4, "CPO", 3, "BRANCHING");
        addEntry(0xEC, "CPE", 3, "BRANCHING");
        addEntry(0xF4, "CP", 3, "BRANCHING");
        addEntry(0xFC, "CM", 3, "BRANCHING");

        // Branching — RET/Rcc
        addEntry(0xC9, "RET", 1, "BRANCHING");
        addEntry(0xC0, "RNZ", 1, "BRANCHING");
        addEntry(0xC8, "RZ", 1, "BRANCHING");
        addEntry(0xD0, "RNC", 1, "BRANCHING");
        addEntry(0xD8, "RC", 1, "BRANCHING");
        addEntry(0xE0, "RPO", 1, "BRANCHING");
        addEntry(0xE8, "RPE", 1, "BRANCHING");
        addEntry(0xF0, "RP", 1, "BRANCHING");
        addEntry(0xF8, "RM", 1, "BRANCHING");
        addEntry(0xE9, "PCHL", 1, "BRANCHING");

        // Branching — RST
        for (int n = 0; n < 8; n++)
            addEntry(0xC7 | (n << 3), "RST " + n, 1, "BRANCHING");

        // Stack/IO
        String[] pushPopPairs = {"B", "D", "H", "PSW"};
        for (int rp = 0; rp < 4; rp++) {
            addEntry(0xC5 | (rp << 4), "PUSH " + pushPopPairs[rp], 1, "STACK_IO");
            addEntry(0xC1 | (rp << 4), "POP " + pushPopPairs[rp], 1, "STACK_IO");
        }
        addEntry(0xE3, "XTHL", 1, "STACK_IO");
        addEntry(0xF9, "SPHL", 1, "STACK_IO");
        addEntry(0xDB, "IN", 2, "STACK_IO");
        addEntry(0xD3, "OUT", 2, "STACK_IO");
    }

    private static void addEntry(int opcode, String mnemonic, int byteSize, String category) {
        table.put(opcode, new OpcodeEntry(opcode, mnemonic, byteSize, category));
        mnemonicMap.put(mnemonic.toUpperCase(), opcode);
    }

    public static String getMnemonic(int opcode) {
        OpcodeEntry entry = table.get(opcode);
        return (entry != null) ? entry.mnemonic : "???";
    }

    public static int getOpcode(String mnemonic) {
        Integer opcode = mnemonicMap.get(mnemonic.toUpperCase());
        return (opcode != null) ? opcode : -1;
    }

    public static int getByteSize(int opcode) {
        OpcodeEntry entry = table.get(opcode);
        return (entry != null) ? entry.byteSize : 1;
    }

    public static OpcodeEntry getEntry(int opcode) { return table.get(opcode); }

    public static void printTable() {
        table.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.println(e.getValue()));
    }
}
