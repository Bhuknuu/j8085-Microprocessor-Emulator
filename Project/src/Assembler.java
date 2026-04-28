import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

// Assembler — Two-pass assembly: labels first, then machine code generation
public class Assembler {
    private HashMap<String, Integer> symbolTable;
    private int originAddress;
    private Architecture arch;

    public Assembler(Architecture architecture) {
        this.arch = architecture;
        this.symbolTable = new HashMap<>();
        this.originAddress = 0x2000;
    }

    /**
     * [CRITICAL FIX] assembleToBuffer — generates machine code bytes into a pure
     * int[] buffer WITHOUT writing to Architecture memory.
     * Callers must call arch.loadProgram() only after this succeeds.
     * This prevents partial memory corruption when assembly fails mid-way.
     */
    public int[] assembleToBuffer(String program, int startAddress) throws SimulatorException {
        this.originAddress = startAddress;
        this.symbolTable.clear();
        String[] lines = program.split("\\n");

        // Pass 1: collect labels and handle ORG relocations
        int currentAddress = startAddress;
        for (String line : lines) {
            line = stripComment(line).trim();
            if (line.isEmpty()) continue;
            if (line.contains(":")) {
                int idx = line.indexOf(':');
                symbolTable.put(line.substring(0, idx).trim().toUpperCase(), currentAddress);
                line = line.substring(idx + 1).trim();
                if (line.isEmpty()) continue;
            }
            // [AG-FIX 1.5] ORG: relocate address counter
            String upper = line.toUpperCase();
            if (upper.startsWith("ORG")) {
                int orgAddr = parseImm16(line.substring(3).trim());
                currentAddress = orgAddr;
                continue;
            }
            // [AG-FIX 1.6] DB/DW/DS byte counting for symbol table accuracy
            if (upper.startsWith("DB") || upper.startsWith("DW") || upper.startsWith("DS")) {
                currentAddress += getDirectiveSize(line);
                continue;
            }
            currentAddress += getInstructionSize(line);
        }

        // Pass 2: generate bytes into buffer only — no memory writes
        currentAddress = startAddress;
        List<Integer> buffer = new ArrayList<>();
        for (String line : lines) {
            line = stripComment(line).trim();
            if (line.isEmpty()) continue;
            if (line.contains(":")) {
                line = line.substring(line.indexOf(':') + 1).trim();
                if (line.isEmpty()) continue;
            }
            String upper = line.toUpperCase();
            // [AG-FIX 1.5] ORG: update address, emit no bytes
            if (upper.startsWith("ORG")) {
                currentAddress = parseImm16(line.substring(3).trim());
                continue;
            }
            // [AG-FIX 1.6] DB: emit bytes for each comma-separated value
            if (upper.startsWith("DB")) {
                for (String tok : line.substring(2).split(",")) {
                    tok = tok.trim();
                    if (!tok.isEmpty()) { buffer.add(parseImm8(tok)); currentAddress++; }
                }
                continue;
            }
            // [AG-FIX 1.6] DW: emit 16-bit little-endian words
            if (upper.startsWith("DW")) {
                for (String tok : line.substring(2).split(",")) {
                    tok = tok.trim();
                    if (!tok.isEmpty()) {
                        int w = parseImm16(tok);
                        buffer.add(w & 0xFF); buffer.add((w >> 8) & 0xFF);
                        currentAddress += 2;
                    }
                }
                continue;
            }
            // [AG-FIX 1.6] DS: reserve n zero bytes
            if (upper.startsWith("DS")) {
                int n = parseImm16(line.substring(2).trim());
                for (int i = 0; i < n; i++) { buffer.add(0); currentAddress++; }
                continue;
            }
            int[] bytes = assembleInstruction(line, currentAddress);
            for (int b : bytes) {
                buffer.add(b);
                currentAddress++;
            }
        }
        return buffer.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Legacy entry — assembles AND writes directly to memory. Kept for backward compat. */
    public int[] assemble(String program, int startAddress) throws SimulatorException {
        int[] bytes = assembleToBuffer(program, startAddress);
        arch.loadProgram(startAddress, bytes);
        return bytes;
    }

    // Converts a single assembly line to machine code bytes
    private int[] assembleInstruction(String line, int currentAddress) throws SimulatorException {
        String[] parts = line.trim().split("\\s+", 2);
        String mnemonic = parts[0].toUpperCase();
        String operandStr = (parts.length > 1) ? parts[1].trim() : "";
        String[] ops = operandStr.isEmpty() ? new String[0] : operandStr.split("\\s*,\\s*");

        switch (mnemonic) {
            case "NOP": return new int[]{0x00};
            case "HLT": return new int[]{0x76};
            case "RLC": return new int[]{0x07};
            case "RRC": return new int[]{0x0F};
            case "RAL": return new int[]{0x17};
            case "RAR": return new int[]{0x1F};
            case "DAA": return new int[]{0x27};
            case "CMA": return new int[]{0x2F};
            case "STC": return new int[]{0x37};
            case "CMC": return new int[]{0x3F};
            case "XCHG": return new int[]{0xEB};
            case "PCHL": return new int[]{0xE9};
            case "SPHL": return new int[]{0xF9};
            case "XTHL": return new int[]{0xE3};
            case "RET": return new int[]{0xC9};
            case "RNZ": return new int[]{0xC0};
            case "RZ":  return new int[]{0xC8};
            case "RNC": return new int[]{0xD0};
            case "RC":  return new int[]{0xD8};
            case "RPO": return new int[]{0xE0};
            case "RPE": return new int[]{0xE8};
            case "RP":  return new int[]{0xF0};
            case "RM":  return new int[]{0xF8};
            case "EI":  return new int[]{0xFB};
            case "DI":  return new int[]{0xF3};
            case "RIM": return new int[]{0x20};
            case "SIM": return new int[]{0x30};

            case "MOV": return new int[]{0x40 | (regCode(ops[0]) << 3) | regCode(ops[1])};

            case "MVI": return new int[]{0x06 | (regCode(ops[0]) << 3), parseImm8(ops[1])};

            case "LXI": return i3(0x01 | (rpCode(ops[0]) << 4), parseImm16(ops[1]));
            case "LDA": return i3(0x3A, resolveAddress(ops[0]));
            case "STA": return i3(0x32, resolveAddress(ops[0]));
            case "LHLD": return i3(0x2A, resolveAddress(ops[0]));
            case "SHLD": return i3(0x22, resolveAddress(ops[0]));

            case "STAX": return new int[]{ops[0].trim().toUpperCase().equals("B") ? 0x02 : 0x12};
            case "LDAX": return new int[]{ops[0].trim().toUpperCase().equals("B") ? 0x0A : 0x1A};

            case "ADD": return new int[]{0x80 | regCode(ops[0])};
            case "ADC": return new int[]{0x88 | regCode(ops[0])};
            case "SUB": return new int[]{0x90 | regCode(ops[0])};
            case "SBB": return new int[]{0x98 | regCode(ops[0])};
            case "ANA": return new int[]{0xA0 | regCode(ops[0])};
            case "XRA": return new int[]{0xA8 | regCode(ops[0])};
            case "ORA": return new int[]{0xB0 | regCode(ops[0])};
            case "CMP": return new int[]{0xB8 | regCode(ops[0])};

            case "ADI": return new int[]{0xC6, parseImm8(ops[0])};
            case "ACI": return new int[]{0xCE, parseImm8(ops[0])};
            case "SUI": return new int[]{0xD6, parseImm8(ops[0])};
            case "SBI": return new int[]{0xDE, parseImm8(ops[0])};
            case "ANI": return new int[]{0xE6, parseImm8(ops[0])};
            case "XRI": return new int[]{0xEE, parseImm8(ops[0])};
            case "ORI": return new int[]{0xF6, parseImm8(ops[0])};
            case "CPI": return new int[]{0xFE, parseImm8(ops[0])};

            case "INR": return new int[]{0x04 | (regCode(ops[0]) << 3)};
            case "DCR": return new int[]{0x05 | (regCode(ops[0]) << 3)};
            case "INX": return new int[]{0x03 | (rpCode(ops[0]) << 4)};
            case "DCX": return new int[]{0x0B | (rpCode(ops[0]) << 4)};
            case "DAD": return new int[]{0x09 | (rpCode(ops[0]) << 4)};

            case "PUSH": return new int[]{0xC5 | (rpCodePsw(ops[0]) << 4)};
            case "POP":  return new int[]{0xC1 | (rpCodePsw(ops[0]) << 4)};

            case "JMP":  return i3(0xC3, resolveAddress(ops[0]));
            case "JNZ":  return i3(0xC2, resolveAddress(ops[0]));
            case "JZ":   return i3(0xCA, resolveAddress(ops[0]));
            case "JNC":  return i3(0xD2, resolveAddress(ops[0]));
            case "JC":   return i3(0xDA, resolveAddress(ops[0]));
            case "JPO":  return i3(0xE2, resolveAddress(ops[0]));
            case "JPE":  return i3(0xEA, resolveAddress(ops[0]));
            case "JP":   return i3(0xF2, resolveAddress(ops[0]));
            case "JM":   return i3(0xFA, resolveAddress(ops[0]));

            case "CALL": return i3(0xCD, resolveAddress(ops[0]));
            case "CNZ":  return i3(0xC4, resolveAddress(ops[0]));
            case "CZ":   return i3(0xCC, resolveAddress(ops[0]));
            case "CNC":  return i3(0xD4, resolveAddress(ops[0]));
            case "CC":   return i3(0xDC, resolveAddress(ops[0]));
            case "CPO":  return i3(0xE4, resolveAddress(ops[0]));
            case "CPE":  return i3(0xEC, resolveAddress(ops[0]));
            case "CP":   return i3(0xF4, resolveAddress(ops[0]));
            case "CM":   return i3(0xFC, resolveAddress(ops[0]));

            case "RST": {
                int n = Integer.parseInt(ops[0].trim());
                return new int[]{0xC7 | (n << 3)};
            }

            case "IN":  return new int[]{0xDB, parseImm8(ops[0])};
            case "OUT": return new int[]{0xD3, parseImm8(ops[0])};

            default:
                throw new SimulatorException("Unknown mnemonic: " + mnemonic,
                        SimulatorException.ErrorType.SyntaxError);
        }
    }

    // Helper: build 3-byte instruction {opcode, low, high}
    private int[] i3(int opcode, int addr16) {
        return new int[]{opcode, addr16 & 0xFF, (addr16 >> 8) & 0xFF};
    }

    // Register name to 3-bit code: B=0, C=1, D=2, E=3, H=4, L=5, M=6, A=7
    private int regCode(String name) throws SimulatorException {
        switch (name.trim().toUpperCase()) {
            case "B": return 0; case "C": return 1; case "D": return 2; case "E": return 3;
            case "H": return 4; case "L": return 5; case "M": return 6; case "A": return 7;
            default: throw new SimulatorException("Invalid register: " + name,
                    SimulatorException.ErrorType.InvalidRegister);
        }
    }

    // Register pair to 2-bit code: B=0, D=1, H=2, SP=3
    private int rpCode(String name) throws SimulatorException {
        switch (name.trim().toUpperCase()) {
            case "B": return 0; case "D": return 1; case "H": return 2; case "SP": return 3;
            default: throw new SimulatorException("Invalid register pair: " + name, SimulatorException.ErrorType.InvalidRegister);
        }
    }

    // Register pair code for PUSH/POP: B=0, D=1, H=2, PSW=3
    private int rpCodePsw(String name) throws SimulatorException {
        switch (name.trim().toUpperCase()) {
            case "B": return 0; case "D": return 1; case "H": return 2; case "PSW": return 3;
            default: throw new SimulatorException("Invalid pair for PUSH/POP: " + name,
                    SimulatorException.ErrorType.InvalidRegister);
        }
    }

    // Parse 8-bit immediate: supports 42H, 0x2A, 01000010B, 66 (decimal)
    private int parseImm8(String text) throws SimulatorException {
        int value = parseNumber(text);
        if (value < 0 || value > 0xFF)
            throw new SimulatorException("8-bit value out of range: " + text,
                    SimulatorException.ErrorType.InvalidData);
        return value;
    }

    // Parse 16-bit immediate
    private int parseImm16(String text) throws SimulatorException {
        int value = parseNumber(text);
        if (value < 0 || value > 0xFFFF)
            throw new SimulatorException("16-bit value out of range: " + text,
                    SimulatorException.ErrorType.InvalidData);
        return value;
    }

    // Core number parser for hex/binary/decimal formats
    private int parseNumber(String text) throws SimulatorException {
        text = text.trim().toUpperCase();
        try {
            if (text.endsWith("H"))       return Integer.parseInt(text.substring(0, text.length() - 1), 16);
            if (text.startsWith("0X"))    return Integer.parseInt(text.substring(2), 16);
            if (text.endsWith("B"))       return Integer.parseInt(text.substring(0, text.length() - 1), 2);
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new SimulatorException("Invalid number format: " + text,
                    SimulatorException.ErrorType.SyntaxError);
        }
    }

    // Resolve address: try as number first, then as label
    private int resolveAddress(String ref) throws SimulatorException {
        try {
            return parseImm16(ref);
        } catch (SimulatorException e) {
            String label = ref.trim().toUpperCase();
            if (symbolTable.containsKey(label)) return symbolTable.get(label);
            throw new SimulatorException("Undefined label: " + ref,
                    SimulatorException.ErrorType.UndefinedAddress);
        }
    }

    // Determine byte size of an instruction from its mnemonic
    private int getInstructionSize(String line) {
        String mnemonic = line.trim().split("\\s+")[0].toUpperCase();
        switch (mnemonic) {
            case "LXI": case "LDA": case "STA": case "LHLD": case "SHLD":
            case "JMP": case "JC": case "JNC": case "JZ": case "JNZ":
            case "JP": case "JM": case "JPE": case "JPO":
            case "CALL": case "CC": case "CNC": case "CZ": case "CNZ":
            case "CP": case "CM": case "CPE": case "CPO":
                return 3;
            case "MVI": case "ADI": case "ACI": case "SUI": case "SBI":
            case "ANI": case "ORI": case "XRI": case "CPI":
            case "IN": case "OUT":
                return 2;
            default:
                return 1;
        }
    }

    // [AG-FIX 1.6] Compute byte size of a directive (DB/DW/DS)
    private int getDirectiveSize(String line) throws SimulatorException {
        String upper = line.trim().toUpperCase();
        if (upper.startsWith("DB")) {
            String[] vals = line.substring(2).split(",");
            int count = 0;
            for (String v : vals) if (!v.trim().isEmpty()) count++;
            return count;
        }
        if (upper.startsWith("DW")) {
            String[] vals = line.substring(2).split(",");
            int count = 0;
            for (String v : vals) if (!v.trim().isEmpty()) count++;
            return count * 2;
        }
        if (upper.startsWith("DS")) {
            return parseImm16(line.substring(2).trim());
        }
        return 0;
    }

    // Strip ;comments from a line
    private String stripComment(String line) {
        int idx = line.indexOf(';');
        return (idx >= 0) ? line.substring(0, idx) : line;
    }

    public HashMap<String, Integer> getSymbolTable() { return symbolTable; }
}
