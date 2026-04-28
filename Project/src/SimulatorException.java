
//  Separate exception class for custom exceptions throughout the Project

public class SimulatorException extends Exception {
    public enum ErrorType {
        InvalidMemoryAddress,                   // Address outside 0x0000-0xFFFF or allocated range
        InvalidRegister,                        // Unrecognized register name
        InvalidOpcode,                          // Unrecognized instruction opcode
        InvalidData,                            // Data value outside valid range (e.g., > 0xFF for 8-bit)
        StackOverflow,                          // Stack pointer went below 0x0000
        StackUnderflow,                         // Stack pointer went above initial SP value
        MemoryOutOfBound,                       // Program tried to access memory outside range
        HaltEncounter,                          // HLT instruction executed (not really an "error")
        SyntaxError,                            // Assembly syntax is invalid
        UndefinedAddress,                       // Jump/call to a label that doesn't exist
        StepLimitExceeded                       // [AG-FIX 1.13] Execution step limit hit (probable infinite loop)
    }
    private final ErrorType errorType;
    private final int address;

    public SimulatorException(String message) {
        super(message);
        this.errorType = ErrorType.InvalidData; // default
        this.address = -1;
    }

    public SimulatorException(String message, ErrorType errorType) {
        super(message);
        this.errorType = errorType;
        this.address = -1;
    }

    public SimulatorException(String message, ErrorType errorType, int address) {
        super(message);
        this.errorType = errorType;
        this.address = address;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public int getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return String.format("[%s]%s: %s",errorType,(address != -1) ? String.format(" at 0x%04X", address) : "", getMessage());
    }
}
