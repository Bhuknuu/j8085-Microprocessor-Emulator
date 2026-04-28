import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// Architecture — The 8085 CPU core: registers, memory, ALU, and fetch-decode-execute engine
public class Architecture implements Memory, InstrucSet {

    // [AG-FIX 3.7] Register file: int fields replace HashMap for zero-alloc access
    private int regA, regB, regC, regD, regE, regH, regL, regW, regZ;
    private int programCounter;
    private int stackPointer;

    // Memory
    private int[] memory;
    private int memoryStart;
    private int memoryEnd;

    // Subsystems
    private ALU alu;
    private boolean halted;
    private boolean interruptsEnabled;
    private HashMap<Integer, Integer> ioPorts;

    // [AG-FIX 1.10] Interrupt state for RIM/SIM
    private int interruptMask = 0x00;        // bits 2-0: mask for RST7.5, RST6.5, RST5.5
    private boolean rst75Pending = false;
    private boolean rst65Pending = false;
    private boolean rst55Pending = false;

    // [AG-FIX 1.12] T-state accumulator (replaces dead byteSize variable)
    private long totalTStates = 0L;

    // [AG-FIX 1.13] Configurable execution step limit
    private int executionStepLimit = Integer.MAX_VALUE;

    // Default constructor: 64KB
    public Architecture() {
        this.memoryStart = DEFAULT_START_ADDRESS;
        this.memoryEnd = DEFAULT_END_ADDRESS;
        this.memory = new int[MAX_MEMORY_SIZE];
        init();
    }

    // Custom range constructor
    public Architecture(int startAddress, int endAddress) throws SimulatorException {
        if (startAddress < 0 || endAddress > 0xFFFF || startAddress > endAddress)
            throw new SimulatorException("Invalid memory range",
                    SimulatorException.ErrorType.InvalidMemoryAddress);
        this.memoryStart = startAddress;
        this.memoryEnd = endAddress;
        this.memory = new int[MAX_MEMORY_SIZE];
        init();
    }

    // [AG-FIX 3.6] Execution trace: circular buffer of last 64 steps
    private final String[] traceBuffer = new String[64];
    private int traceIdx = 0;

    private void init() {
        alu = new ALU();
        ioPorts = new HashMap<>();
        regA=regB=regC=regD=regE=regH=regL=regW=regZ=0;
        programCounter = 0x0000;
        stackPointer = 0xFFFF;
        halted = false;
        interruptsEnabled = false;
    }

    // ─── Memory Interface ────────────────────────────────────────

    @Override
    public int readMemory(int address) throws SimulatorException {
        validateAddress(address);
        return memory[address] & 0xFF;
    }

    @Override
    public void writeMemory(int address, int data) throws SimulatorException {
        validateAddress(address);
        memory[address] = data & 0xFF;
    }

    @Override
    public void resetMemory() { memory = new int[MAX_MEMORY_SIZE]; }

    @Override
    public int[] getMemoryDump() { return memory.clone(); }

    @Override
    public int getMemoryStart() { return memoryStart; }

    @Override
    public int getMemoryEnd() { return memoryEnd; }

    @Override
    public void loadProgram(int startAddress, int[] data) throws SimulatorException {
        for (int i = 0; i < data.length; i++)
            writeMemory(startAddress + i, data[i]);
    }

    @Override
    public void displayMemoryRange(int from, int to) {
        from = Math.max(from, 0);
        to = Math.min(to, 0xFFFF);
        System.out.printf("      00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F%n");
        int rowStart = from & 0xFFF0;
        for (int addr = rowStart; addr <= to; addr += 16) {
            System.out.printf("%04X: ", addr);
            for (int col = 0; col < 16 && (addr + col) <= 0xFFFF; col++)
                System.out.printf("%02X ", memory[addr + col]);
            System.out.println();
        }
    }

    private void validateAddress(int address) throws SimulatorException {
        // [AG-FIX 1.11] Two-layer check: absolute 16-bit range, then configured range
        if (address < 0 || address > 0xFFFF)
            throw new SimulatorException("Address 0x" + Integer.toHexString(address) + " outside 16-bit space",
                    SimulatorException.ErrorType.InvalidMemoryAddress, address);
        if (address < memoryStart || address > memoryEnd)
            throw new SimulatorException(
                "Address 0x" + String.format("%04X", address) +
                " outside configured range [0x" + String.format("%04X", memoryStart) +
                "\u20130x" + String.format("%04X", memoryEnd) + "]",
                SimulatorException.ErrorType.InvalidMemoryAddress, address);
    }

    // [AG-FIX 3.7] Switch-based register access -- zero allocation

    public int getRegister(String name) throws SimulatorException {
        switch(name.toUpperCase()) {
            case "A": return regA; case "B": return regB; case "C": return regC;
            case "D": return regD; case "E": return regE;
            case "H": return regH; case "L": return regL;
            case "W": return regW; case "Z": return regZ;
            default: throw new SimulatorException("Invalid register: "+name,
                    SimulatorException.ErrorType.InvalidRegister);
        }
    }

    public void setRegister(String name, int value) throws SimulatorException {
        value &= 0xFF;
        switch(name.toUpperCase()) {
            case "A": regA=value; break; case "B": regB=value; break; case "C": regC=value; break;
            case "D": regD=value; break; case "E": regE=value; break;
            case "H": regH=value; break; case "L": regL=value; break;
            case "W": regW=value; break; case "Z": regZ=value; break;
            default: throw new SimulatorException("Invalid register: "+name,
                    SimulatorException.ErrorType.InvalidRegister);
        }
    }

    // Get 16-bit register pair value
    private int getRegisterPair(String pair) throws SimulatorException {
        switch (pair.toUpperCase()) {
            case "B": return (getRegister("B") << 8) | getRegister("C");
            case "D": return (getRegister("D") << 8) | getRegister("E");
            case "H": return (getRegister("H") << 8) | getRegister("L");
            case "SP": return stackPointer;
            default: throw new SimulatorException("Invalid pair: " + pair,
                    SimulatorException.ErrorType.InvalidRegister);
        }
    }

    // Set 16-bit register pair value
    private void setRegisterPair(String pair, int value) throws SimulatorException {
        value &= 0xFFFF;
        switch (pair.toUpperCase()) {
            case "B": setRegister("B", (value >> 8) & 0xFF); setRegister("C", value & 0xFF); break;
            case "D": setRegister("D", (value >> 8) & 0xFF); setRegister("E", value & 0xFF); break;
            case "H": setRegister("H", (value >> 8) & 0xFF); setRegister("L", value & 0xFF); break;
            case "SP": stackPointer = value; break;
            default: throw new SimulatorException("Invalid pair: " + pair,
                    SimulatorException.ErrorType.InvalidRegister);
        }
    }

    // HL shortcut
    private int getHL() throws SimulatorException { return getRegisterPair("H"); }

    // Get operand value: register or memory[HL]
    private int getOperandValue(String operand) throws SimulatorException {
        if (operand.equalsIgnoreCase("M")) return readMemory(getHL());
        return getRegister(operand);
    }

    // Set operand value: register or memory[HL]
    private void setOperandValue(String operand, int value) throws SimulatorException {
        if (operand.equalsIgnoreCase("M")) writeMemory(getHL(), value);
        else setRegister(operand, value);
    }

    // ─── Execution Engine ────────────────────────────────────────

    // Fetch next byte from memory at PC, advance PC
    private int fetchByte() throws SimulatorException {
        int data = readMemory(programCounter);
        programCounter = (programCounter + 1) & 0xFFFF;
        return data;
    }

    // Fetch next 16-bit word (low byte first, little-endian)
    private int fetchWord() throws SimulatorException {
        int low = fetchByte();
        int high = fetchByte();
        return (high << 8) | low;
    }

    // [AG-FIX 1.13] Configurable step limit — default Integer.MAX_VALUE (no practical limit)
    public void setExecutionStepLimit(int limit) { this.executionStepLimit = limit; }
    public int getExecutionStepLimit()           { return executionStepLimit; }
    public long getTotalTStates()                { return totalTStates; } // [AG-FIX 1.12]

    // [AG-FIX 1.1] Thread-safe snapshot for EDT consumption
    public synchronized CPUSnapshot getCPUSnapshot(int memFrom, int memTo) {
        memFrom = Math.max(memFrom, memoryStart);
        memTo = Math.min(memTo, memoryEnd);
        int len = Math.max(0, memTo - memFrom + 1);
        int[] snap = new int[len];
        System.arraycopy(memory, memFrom, snap, 0, len);
        try {
            return new CPUSnapshot(
                programCounter, stackPointer,
                getRegister("A"), getRegister("B"), getRegister("C"),
                getRegister("D"), getRegister("E"), getRegister("H"), getRegister("L"),
                alu.isSignFlag(), alu.isZeroFlag(), alu.isAuxCarryFlag(),
                alu.isParityFlag(), alu.isCarryFlag(), halted,
                snap, memFrom, new java.util.HashMap<>(ioPorts));
        } catch (SimulatorException e) { throw new RuntimeException(e); }
    }

    // [AG-FIX 1.1] Default snapshot: 48 bytes around PC
    public synchronized CPUSnapshot getCPUSnapshot() {
        int from = Math.max(memoryStart, programCounter - 8);
        int to = Math.min(memoryEnd, programCounter + 39);
        return getCPUSnapshot(from, to);
    }

    // Run from a specific address until HLT
    public void runFrom(int address) throws SimulatorException {
        programCounter = address;
        halted = false;
        int steps = 0;
        while (!halted && steps < executionStepLimit) {
            executeInstruction();
            steps++;
        }
        if (steps >= executionStepLimit && !halted) // [AG-FIX 1.13]
            throw new SimulatorException(
                "Step limit (" + executionStepLimit + ") exceeded — possible infinite loop",
                SimulatorException.ErrorType.StepLimitExceeded);
    }

    // Execute a single instruction (step mode)
    public void step() throws SimulatorException {
        if (!halted) executeInstruction();
    }

    // Disassemble an opcode for display
    public String disassemble(int opcode) {
        return OpcodeTable.getMnemonic(opcode);
    }

    // Core fetch-decode-execute
    private void executeInstruction() throws SimulatorException {
        int pc0 = programCounter; // save for trace
        int opcode = fetchByte();
        totalTStates += OpcodeTable.getByteSize(opcode); // [AG-FIX 1.12]
        // [AG-FIX 3.6] Record trace entry
        traceBuffer[traceIdx] = String.format("%04X:%02X", pc0, opcode);
        traceIdx = (traceIdx + 1) & 63;

        switch (opcode) {
            // NOP
            case 0x00: nop(); break;

            // LXI
            case 0x01: lxi("B", fetchWord()); break;
            case 0x11: lxi("D", fetchWord()); break;
            case 0x21: lxi("H", fetchWord()); break;
            case 0x31: lxi("SP", fetchWord()); break;

            // STAX/LDAX
            case 0x02: stax("B"); break;
            case 0x12: stax("D"); break;
            case 0x0A: ldax("B"); break;
            case 0x1A: ldax("D"); break;

            // INX/DCX/DAD
            case 0x03: inx("B"); break; case 0x13: inx("D"); break;
            case 0x23: inx("H"); break; case 0x33: inx("SP"); break;
            case 0x0B: dcx("B"); break; case 0x1B: dcx("D"); break;
            case 0x2B: dcx("H"); break; case 0x3B: dcx("SP"); break;
            case 0x09: dad("B"); break; case 0x19: dad("D"); break;
            case 0x29: dad("H"); break; case 0x39: dad("SP"); break;

            // INR
            case 0x04: inr("B"); break; case 0x0C: inr("C"); break;
            case 0x14: inr("D"); break; case 0x1C: inr("E"); break;
            case 0x24: inr("H"); break; case 0x2C: inr("L"); break;
            case 0x34: inr("M"); break; case 0x3C: inr("A"); break;

            // DCR
            case 0x05: dcr("B"); break; case 0x0D: dcr("C"); break;
            case 0x15: dcr("D"); break; case 0x1D: dcr("E"); break;
            case 0x25: dcr("H"); break; case 0x2D: dcr("L"); break;
            case 0x35: dcr("M"); break; case 0x3D: dcr("A"); break;

            // MVI
            case 0x06: mvi("B", fetchByte()); break; case 0x0E: mvi("C", fetchByte()); break;
            case 0x16: mvi("D", fetchByte()); break; case 0x1E: mvi("E", fetchByte()); break;
            case 0x26: mvi("H", fetchByte()); break; case 0x2E: mvi("L", fetchByte()); break;
            case 0x36: mvi("M", fetchByte()); break; case 0x3E: mvi("A", fetchByte()); break;

            // Rotate/DAA/CMA/STC/CMC
            case 0x07: rlc(); break; case 0x0F: rrc(); break;
            case 0x17: ral(); break; case 0x1F: rar(); break;
            case 0x27: daa(); break; case 0x2F: cma(); break;
            case 0x37: stc(); break; case 0x3F: cmc(); break;

            // RIM/SIM
            case 0x20: rim(); break;
            case 0x30: sim(); break;

            // Direct addressing
            case 0x22: shld(fetchWord()); break;
            case 0x2A: lhld(fetchWord()); break;
            case 0x32: sta(fetchWord()); break;
            case 0x3A: lda(fetchWord()); break;

            // HLT
            case 0x76: hlt(); break;

            // XCHG
            case 0xEB: xchg(); break;

            // ADD 0x80-0x87
            case 0x80: add("B"); break; case 0x81: add("C"); break;
            case 0x82: add("D"); break; case 0x83: add("E"); break;
            case 0x84: add("H"); break; case 0x85: add("L"); break;
            case 0x86: add("M"); break; case 0x87: add("A"); break;

            // ADC 0x88-0x8F
            case 0x88: adc("B"); break; case 0x89: adc("C"); break;
            case 0x8A: adc("D"); break; case 0x8B: adc("E"); break;
            case 0x8C: adc("H"); break; case 0x8D: adc("L"); break;
            case 0x8E: adc("M"); break; case 0x8F: adc("A"); break;

            // SUB 0x90-0x97
            case 0x90: sub("B"); break; case 0x91: sub("C"); break;
            case 0x92: sub("D"); break; case 0x93: sub("E"); break;
            case 0x94: sub("H"); break; case 0x95: sub("L"); break;
            case 0x96: sub("M"); break; case 0x97: sub("A"); break;

            // SBB 0x98-0x9F
            case 0x98: sbb("B"); break; case 0x99: sbb("C"); break;
            case 0x9A: sbb("D"); break; case 0x9B: sbb("E"); break;
            case 0x9C: sbb("H"); break; case 0x9D: sbb("L"); break;
            case 0x9E: sbb("M"); break; case 0x9F: sbb("A"); break;

            // ANA 0xA0-0xA7
            case 0xA0: ana("B"); break; case 0xA1: ana("C"); break;
            case 0xA2: ana("D"); break; case 0xA3: ana("E"); break;
            case 0xA4: ana("H"); break; case 0xA5: ana("L"); break;
            case 0xA6: ana("M"); break; case 0xA7: ana("A"); break;

            // XRA 0xA8-0xAF
            case 0xA8: xra("B"); break; case 0xA9: xra("C"); break;
            case 0xAA: xra("D"); break; case 0xAB: xra("E"); break;
            case 0xAC: xra("H"); break; case 0xAD: xra("L"); break;
            case 0xAE: xra("M"); break; case 0xAF: xra("A"); break;

            // ORA 0xB0-0xB7
            case 0xB0: ora("B"); break; case 0xB1: ora("C"); break;
            case 0xB2: ora("D"); break; case 0xB3: ora("E"); break;
            case 0xB4: ora("H"); break; case 0xB5: ora("L"); break;
            case 0xB6: ora("M"); break; case 0xB7: ora("A"); break;

            // CMP 0xB8-0xBF
            case 0xB8: cmp("B"); break; case 0xB9: cmp("C"); break;
            case 0xBA: cmp("D"); break; case 0xBB: cmp("E"); break;
            case 0xBC: cmp("H"); break; case 0xBD: cmp("L"); break;
            case 0xBE: cmp("M"); break; case 0xBF: cmp("A"); break;

            // Immediate arithmetic/logical
            case 0xC6: adi(fetchByte()); break;
            case 0xCE: aci(fetchByte()); break;
            case 0xD6: sui(fetchByte()); break;
            case 0xDE: sbi(fetchByte()); break;
            case 0xE6: ani(fetchByte()); break;
            case 0xEE: xri(fetchByte()); break;
            case 0xF6: ori(fetchByte()); break;
            case 0xFE: cpi(fetchByte()); break;

            // Branching — JMP/Jcc
            case 0xC3: jmp(fetchWord()); break;
            case 0xC2: jnz(fetchWord()); break;
            case 0xCA: jz(fetchWord()); break;
            case 0xD2: jnc(fetchWord()); break;
            case 0xDA: jc(fetchWord()); break;
            case 0xE2: jpo(fetchWord()); break;
            case 0xEA: jpe(fetchWord()); break;
            case 0xF2: jp(fetchWord()); break;
            case 0xFA: jm(fetchWord()); break;

            // CALL/Ccc
            case 0xCD: call(fetchWord()); break;
            case 0xC4: cnz(fetchWord()); break;
            case 0xCC: cz(fetchWord()); break;
            case 0xD4: cnc(fetchWord()); break;
            case 0xDC: cc(fetchWord()); break;
            case 0xE4: cpo(fetchWord()); break;
            case 0xEC: cpe(fetchWord()); break;
            case 0xF4: cp(fetchWord()); break;
            case 0xFC: cm(fetchWord()); break;

            // RET/Rcc
            case 0xC9: ret(); break;
            case 0xC0: rnz(); break;
            case 0xC8: rz(); break;
            case 0xD0: rnc(); break;
            case 0xD8: rc(); break;
            case 0xE0: rpo(); break;
            case 0xE8: rpe(); break;
            case 0xF0: rp(); break;
            case 0xF8: rm(); break;

            // RST
            case 0xC7: rst(0); break; case 0xCF: rst(1); break;
            case 0xD7: rst(2); break; case 0xDF: rst(3); break;
            case 0xE7: rst(4); break; case 0xEF: rst(5); break;
            case 0xF7: rst(6); break; case 0xFF: rst(7); break;

            // PCHL
            case 0xE9: pchl(); break;

            // Stack
            case 0xC5: push("B"); break; case 0xD5: push("D"); break;
            case 0xE5: push("H"); break; case 0xF5: push("PSW"); break;
            case 0xC1: pop("B"); break;  case 0xD1: pop("D"); break;
            case 0xE1: pop("H"); break;  case 0xF1: pop("PSW"); break;
            case 0xE3: xthl(); break;
            case 0xF9: sphl(); break;

            // I/O
            case 0xDB: in(fetchByte()); break;
            case 0xD3: out(fetchByte()); break;

            // EI/DI
            case 0xFB: ei(); break;
            case 0xF3: di(); break;

            // MOV block: 0x40-0x7F (except 0x76)
            default:
                if (opcode >= 0x40 && opcode <= 0x7F) {
                    String[] regNames = {"B", "C", "D", "E", "H", "L", "M", "A"};
                    int dest = (opcode >> 3) & 0x07;
                    int src = opcode & 0x07;
                    mov(regNames[dest], regNames[src]);
                } else {
                    throw new SimulatorException("Unknown opcode: 0x" + Integer.toHexString(opcode),
                            SimulatorException.ErrorType.InvalidOpcode, programCounter - 1);
                }
        }
    }

    // ─── Data Transfer Instructions ──────────────────────────────

    @Override
    public void mov(String dest, String src) throws SimulatorException {
        if (dest.equalsIgnoreCase("M") && src.equalsIgnoreCase("M"))
            throw new SimulatorException("MOV M,M is invalid (HLT opcode)",
                    SimulatorException.ErrorType.InvalidOpcode);
        int value = getOperandValue(src);
        setOperandValue(dest, value);
    }

    @Override
    public void mvi(String dest, int data) throws SimulatorException {
        setOperandValue(dest, data & 0xFF);
    }

    @Override
    public void lxi(String regPair, int data16) throws SimulatorException {
        setRegisterPair(regPair, data16);
    }

    @Override
    public void lda(int address) throws SimulatorException {
        setRegister("A", readMemory(address));
    }

    @Override
    public void sta(int address) throws SimulatorException {
        writeMemory(address, getRegister("A"));
    }

    @Override
    public void lhld(int address) throws SimulatorException {
        setRegister("L", readMemory(address));
        setRegister("H", readMemory(address + 1));
    }

    @Override
    public void shld(int address) throws SimulatorException {
        writeMemory(address, getRegister("L"));
        writeMemory(address + 1, getRegister("H"));
    }

    @Override
    public void ldax(String regPair) throws SimulatorException {
        setRegister("A", readMemory(getRegisterPair(regPair)));
    }

    @Override
    public void stax(String regPair) throws SimulatorException {
        writeMemory(getRegisterPair(regPair), getRegister("A"));
    }

    @Override
    public void xchg() {
        try {
            int d = getRegister("D"); int e = getRegister("E");
            int h = getRegister("H"); int l = getRegister("L");
            setRegister("D", h); setRegister("E", l);
            setRegister("H", d); setRegister("L", e);
        } catch (SimulatorException ex) { throw new RuntimeException(ex); }
    }

    // ─── Arithmetic Instructions ─────────────────────────────────

    @Override
    public void add(String src) throws SimulatorException {
        int a = getRegister("A");
        int operand = getOperandValue(src);
        setRegister("A", alu.add(a, operand, 0));
    }

    @Override
    public void adc(String src) throws SimulatorException {
        int a = getRegister("A");
        int operand = getOperandValue(src);
        setRegister("A", alu.add(a, operand, alu.isCarryFlag() ? 1 : 0));
    }

    @Override
    public void adi(int data) throws SimulatorException {
        int a = getRegister("A");
        setRegister("A", alu.add(a, data, 0));
    }

    @Override
    public void aci(int data) throws SimulatorException {
        int a = getRegister("A");
        setRegister("A", alu.add(a, data, alu.isCarryFlag() ? 1 : 0));
    }

    @Override
    public void sub(String src) throws SimulatorException {
        int a = getRegister("A");
        int operand = getOperandValue(src);
        setRegister("A", alu.subtract(a, operand, 0));
    }

    @Override
    public void sbb(String src) throws SimulatorException {
        int a = getRegister("A");
        int operand = getOperandValue(src);
        setRegister("A", alu.subtract(a, operand, alu.isCarryFlag() ? 1 : 0));
    }

    @Override
    public void sui(int data) throws SimulatorException {
        int a = getRegister("A");
        setRegister("A", alu.subtract(a, data, 0));
    }

    @Override
    public void sbi(int data) throws SimulatorException {
        int a = getRegister("A");
        setRegister("A", alu.subtract(a, data, alu.isCarryFlag() ? 1 : 0));
    }

    @Override
    public void inr(String dest) throws SimulatorException {
        int value = getOperandValue(dest);
        setOperandValue(dest, alu.increment(value));
    }

    @Override
    public void dcr(String dest) throws SimulatorException {
        int value = getOperandValue(dest);
        setOperandValue(dest, alu.decrement(value));
    }

    @Override
    public void inx(String regPair) throws SimulatorException {
        int value = getRegisterPair(regPair);
        setRegisterPair(regPair, (value + 1) & 0xFFFF);
    }

    @Override
    public void dcx(String regPair) throws SimulatorException {
        int value = getRegisterPair(regPair);
        setRegisterPair(regPair, (value - 1) & 0xFFFF);
    }

    @Override
    public void dad(String regPair) throws SimulatorException {
        int hl = getRegisterPair("H");
        int rp = getRegisterPair(regPair);
        int result = hl + rp;
        alu.setCarryFlag(result > 0xFFFF);
        setRegisterPair("H", result & 0xFFFF);
    }

    // [AG-FIX 1.9] DAA — Intel 8085 Hardware Reference Manual p.2-20
    // Must NOT route through alu.add() — that recalculates ALL flags incorrectly.
    // DAA only updates Z, S, P, CY. AC is undefined after DAA per Intel spec.
    @Override
    public void daa() {
        try {
            int a = getRegister("A");
            int correction = 0;
            boolean newCarry = alu.isCarryFlag();

            // Check lower nibble: borrow from BCD units place
            if ((a & 0x0F) > 9 || alu.isAuxCarryFlag()) {
                correction += 0x06;
            }
            // Check ORIGINAL upper nibble (before any correction) for BCD tens overflow
            if (a > 0x99 || alu.isCarryFlag()) { // [AG-FIX 1.9] was ((a+correction)>>4)>9 — wrong
                correction += 0x60;
                newCarry = true;
            }
            // Compute result inline — do NOT call alu.add() (it corrupts AC, P, S, Z)
            int result = (a + correction) & 0xFF;
            // Manually update only the flags DAA is permitted to change
            alu.setZeroFlag(result == 0);
            alu.setSignFlag((result & 0x80) != 0);
            alu.setParityFlag(Integer.bitCount(result) % 2 == 0);
            alu.setCarryFlag(newCarry);
            // AC is undefined post-DAA per spec — leave it as-is
            setRegister("A", result);
        } catch (SimulatorException e) { throw new RuntimeException(e); }
    }

    // ─── Logical Instructions ────────────────────────────────────

    @Override
    public void ana(String src) throws SimulatorException {
        setRegister("A", alu.and(getRegister("A"), getOperandValue(src)));
    }

    @Override
    public void ani(int data) throws SimulatorException {
        setRegister("A", alu.and(getRegister("A"), data));
    }

    @Override
    public void ora(String src) throws SimulatorException {
        setRegister("A", alu.or(getRegister("A"), getOperandValue(src)));
    }

    @Override
    public void ori(int data) throws SimulatorException {
        setRegister("A", alu.or(getRegister("A"), data));
    }

    @Override
    public void xra(String src) throws SimulatorException {
        setRegister("A", alu.xor(getRegister("A"), getOperandValue(src)));
    }

    @Override
    public void xri(int data) throws SimulatorException {
        setRegister("A", alu.xor(getRegister("A"), data));
    }

    @Override
    public void cmp(String src) throws SimulatorException {
        alu.compare(getRegister("A"), getOperandValue(src));
    }

    @Override
    public void cpi(int data) throws SimulatorException {
        alu.compare(getRegister("A"), data);
    }

    @Override
    public void rlc() {
        try {
            int a = getRegister("A");
            int bit7 = (a >> 7) & 1;
            setRegister("A", ((a << 1) | bit7) & 0xFF);
            alu.setCarryFlag(bit7 == 1);
        } catch (SimulatorException e) { throw new RuntimeException(e); }
    }

    @Override
    public void rrc() {
        try {
            int a = getRegister("A");
            int bit0 = a & 1;
            setRegister("A", ((bit0 << 7) | (a >> 1)) & 0xFF);
            alu.setCarryFlag(bit0 == 1);
        } catch (SimulatorException e) { throw new RuntimeException(e); }
    }

    @Override
    public void ral() {
        try {
            int a = getRegister("A");
            int oldCy = alu.isCarryFlag() ? 1 : 0;
            alu.setCarryFlag(((a >> 7) & 1) == 1);
            setRegister("A", ((a << 1) | oldCy) & 0xFF);
        } catch (SimulatorException e) { throw new RuntimeException(e); }
    }

    @Override
    public void rar() {
        try {
            int a = getRegister("A");
            int oldCy = alu.isCarryFlag() ? 1 : 0;
            alu.setCarryFlag((a & 1) == 1);
            setRegister("A", ((oldCy << 7) | (a >> 1)) & 0xFF);
        } catch (SimulatorException e) { throw new RuntimeException(e); }
    }

    @Override
    public void cma() {
        try { setRegister("A", (~getRegister("A")) & 0xFF); }
        catch (SimulatorException e) { throw new RuntimeException(e); }
    }

    @Override
    public void cmc() { alu.setCarryFlag(!alu.isCarryFlag()); }

    @Override
    public void stc() { alu.setCarryFlag(true); }

    // ─── Branching Instructions ──────────────────────────────────

    @Override public void jmp(int address) { programCounter = address & 0xFFFF; }

    @Override public void jc(int address) { if (alu.isCarryFlag()) programCounter = address; }

    @Override public void jnc(int address) { if (!alu.isCarryFlag()) programCounter = address; }

    @Override public void jz(int address) { if (alu.isZeroFlag()) programCounter = address; }

    @Override public void jnz(int address) { if (!alu.isZeroFlag()) programCounter = address; }

    @Override public void jp(int address) { if (!alu.isSignFlag()) programCounter = address; }

    @Override public void jm(int address) { if (alu.isSignFlag()) programCounter = address; }

    @Override public void jpe(int address) { if (alu.isParityFlag()) programCounter = address; }

    @Override public void jpo(int address) { if (!alu.isParityFlag()) programCounter = address; }

    @Override
    public void call(int address) throws SimulatorException {
        // PC already advanced past the 3-byte CALL, so current PC is the return address
        int returnAddr = programCounter;
        stackPointer = (stackPointer - 1) & 0xFFFF;
        writeMemory(stackPointer, (returnAddr >> 8) & 0xFF);
        stackPointer = (stackPointer - 1) & 0xFFFF;
        writeMemory(stackPointer, returnAddr & 0xFF);
        programCounter = address & 0xFFFF;
    }

    @Override public void cc(int address) throws SimulatorException  { if (alu.isCarryFlag()) call(address); }
    @Override public void cnc(int address) throws SimulatorException { if (!alu.isCarryFlag()) call(address); }
    @Override public void cz(int address) throws SimulatorException  { if (alu.isZeroFlag()) call(address); }
    @Override public void cnz(int address) throws SimulatorException { if (!alu.isZeroFlag()) call(address); }
    @Override public void cp(int address) throws SimulatorException  { if (!alu.isSignFlag()) call(address); }
    @Override public void cm(int address) throws SimulatorException  { if (alu.isSignFlag()) call(address); }
    @Override public void cpe(int address) throws SimulatorException { if (alu.isParityFlag()) call(address); }
    @Override public void cpo(int address) throws SimulatorException { if (!alu.isParityFlag()) call(address); }

    @Override
    public void ret() throws SimulatorException {
        int low = readMemory(stackPointer);
        stackPointer = (stackPointer + 1) & 0xFFFF;
        int high = readMemory(stackPointer);
        stackPointer = (stackPointer + 1) & 0xFFFF;
        programCounter = (high << 8) | low;
    }

    @Override public void rc() throws SimulatorException  { if (alu.isCarryFlag()) ret(); }
    @Override public void rnc() throws SimulatorException { if (!alu.isCarryFlag()) ret(); }
    @Override public void rz() throws SimulatorException  { if (alu.isZeroFlag()) ret(); }
    @Override public void rnz() throws SimulatorException { if (!alu.isZeroFlag()) ret(); }
    @Override public void rp() throws SimulatorException  { if (!alu.isSignFlag()) ret(); }
    @Override public void rm() throws SimulatorException  { if (alu.isSignFlag()) ret(); }
    @Override public void rpe() throws SimulatorException { if (alu.isParityFlag()) ret(); }
    @Override public void rpo() throws SimulatorException { if (!alu.isParityFlag()) ret(); }

    @Override
    public void rst(int n) throws SimulatorException {
        if (n < 0 || n > 7)
            throw new SimulatorException("Invalid RST number: " + n,
                    SimulatorException.ErrorType.InvalidData);
        call(n * 8);
    }

    @Override
    public void pchl() {
        try { programCounter = getHL(); }
        catch (SimulatorException e) { throw new RuntimeException(e); }
    }

    // ─── Stack, I/O, Machine Control ─────────────────────────────

    @Override
    public void push(String regPair) throws SimulatorException {
        int high, low;
        if (regPair.equalsIgnoreCase("PSW")) {
            high = getRegister("A");
            low = alu.getFlagsByte();
        } else {
            int value = getRegisterPair(regPair);
            high = (value >> 8) & 0xFF;
            low = value & 0xFF;
        }
        stackPointer = (stackPointer - 1) & 0xFFFF;
        writeMemory(stackPointer, high);
        stackPointer = (stackPointer - 1) & 0xFFFF;
        writeMemory(stackPointer, low);
    }

    @Override
    public void pop(String regPair) throws SimulatorException {
        int low = readMemory(stackPointer);
        stackPointer = (stackPointer + 1) & 0xFFFF;
        int high = readMemory(stackPointer);
        stackPointer = (stackPointer + 1) & 0xFFFF;
        if (regPair.equalsIgnoreCase("PSW")) {
            setRegister("A", high);
            alu.setFlagsByte(low);
        } else {
            setRegisterPair(regPair, (high << 8) | low);
        }
    }

    @Override
    public void xthl() throws SimulatorException {
        int memLow = readMemory(stackPointer);
        int memHigh = readMemory((stackPointer + 1) & 0xFFFF);
        writeMemory(stackPointer, getRegister("L"));
        writeMemory((stackPointer + 1) & 0xFFFF, getRegister("H"));
        setRegister("L", memLow);
        setRegister("H", memHigh);
    }

    @Override
    public void sphl() {
        try { stackPointer = getHL(); }
        catch (SimulatorException e) { throw new RuntimeException(e); }
    }

    @Override
    public void in(int port) throws SimulatorException {
        setRegister("A", ioPorts.getOrDefault(port & 0xFF, 0x00));
    }

    @Override
    public void out(int port) throws SimulatorException {
        ioPorts.put(port & 0xFF, getRegister("A"));
    }

    @Override public void hlt() { halted = true; }
    @Override public void nop() { /* do nothing */ }
    @Override public void ei() { interruptsEnabled = true; }
    @Override public void di() { interruptsEnabled = false; }

    // [AG-FIX 1.10] RIM — Read Interrupt Masks into A
    // Format: [SID | RST7.5p | RST6.5p | RST5.5p | IE | M7.5 | M6.5 | M5.5]
    @Override public void rim() {
        try {
            int sod = 0; // TODO: SID (serial input) — requires virtual serial port
            int pending = (rst75Pending ? 0x40 : 0)
                        | (rst65Pending ? 0x20 : 0)
                        | (rst55Pending ? 0x10 : 0);
            int ie = interruptsEnabled ? 0x08 : 0;
            setRegister("A", sod | pending | ie | (interruptMask & 0x07));
        } catch (SimulatorException e) { throw new RuntimeException(e); }
    }

    // [AG-FIX 1.10] SIM — Set Interrupt Masks from A
    // Format: [SOD | SOE | X | RST7.5r | MSE | M7.5 | M6.5 | M5.5]
    @Override public void sim() {
        try {
            int a = getRegister("A");
            if ((a & 0x08) != 0) {            // MSE bit: mask set enable
                interruptMask = a & 0x07;
            }
            if ((a & 0x10) != 0) {            // RST7.5 reset bit
                rst75Pending = false;
            }
            // TODO: SOD/SOE serial output — requires virtual serial port
        } catch (SimulatorException e) { throw new RuntimeException(e); }
    }

    // ─── State Inspection ────────────────────────────────────────

    public String getCPUState() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("╔══════════════════════════════════════╗\n");
            sb.append("║         8085 CPU STATE               ║\n");
            sb.append("╠══════════════════════════════════════╣\n");
            sb.append(String.format("║  A  = %02XH                          ║%n", getRegister("A")));
            sb.append(String.format("║  B  = %02XH   C  = %02XH              ║%n", getRegister("B"), getRegister("C")));
            sb.append(String.format("║  D  = %02XH   E  = %02XH              ║%n", getRegister("D"), getRegister("E")));
            sb.append(String.format("║  H  = %02XH   L  = %02XH              ║%n", getRegister("H"), getRegister("L")));
            sb.append(String.format("║  SP = %04XH  PC = %04XH            ║%n", stackPointer, programCounter));
            sb.append("║  Flags: ").append(alu.flagsToString()).append("  ║\n");
            sb.append("╚══════════════════════════════════════╝\n");
            return sb.toString();
        } catch (SimulatorException e) { return "Error reading CPU state: " + e.getMessage(); }
    }

    public void reset() {
        regA=regB=regC=regD=regE=regH=regL=regW=regZ=0;
        programCounter = 0x0000;
        stackPointer = 0xFFFF;
        alu.resetFlags();
        resetMemory();
        halted = false;
        interruptsEnabled = false;
        ioPorts.clear();
        // [AG-FIX 1.10] Reset interrupt state
        interruptMask = 0x00;
        rst75Pending = false; rst65Pending = false; rst55Pending = false;
        // [AG-FIX 1.12] Reset T-state counter
        totalTStates = 0L;
    }

    // ─── Getters/Setters for UI ──────────────────────────────────

    public int getProgramCounter() { return programCounter; }
    public void setProgramCounter(int pc) { this.programCounter = pc & 0xFFFF; }
    public int getStackPointer() { return stackPointer; }
    public void setStackPointer(int sp) { this.stackPointer = sp & 0xFFFF; }
    public boolean isHalted() { return halted; }
    public ALU getALU() { return alu; }
    // [AG-FIX 3.7+3.9] Build immutable map views on demand (no internal HashMap)
    public Map<String, Integer> getGeneralRegisters() {
        Map<String,Integer> m = new HashMap<>(6);
        m.put("B",regB);m.put("C",regC);m.put("D",regD);m.put("E",regE);m.put("H",regH);m.put("L",regL);
        return Collections.unmodifiableMap(m);
    }
    public Map<String, Integer> getSpecialRegisters() {
        Map<String,Integer> m = new HashMap<>(3);
        m.put("A",regA);m.put("W",regW);m.put("Z",regZ);
        return Collections.unmodifiableMap(m);
    }
    // [AG-FIX 3.6] Execution trace access
    public String[] getTrace() {
        String[] out = new String[64];
        for(int i=0;i<64;i++) out[i]=traceBuffer[(traceIdx+i)&63];
        return out;
    }
}
