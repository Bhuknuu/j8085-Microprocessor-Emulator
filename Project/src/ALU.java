// ALU — Arithmetic Logic Unit with 5-flag register (S, Z, AC, P, CY)
public class ALU {
    private boolean signFlag;
    private boolean zeroFlag;
    private boolean auxCarryFlag;
    private boolean parityFlag;
    private boolean carryFlag;


    public ALU() { resetFlags(); }

    public void resetFlags() {
        signFlag = false;
        zeroFlag = false;
        auxCarryFlag = false;
        parityFlag = false;
        carryFlag = false;
    }

    // Updates all five flags based on a computation result
    public void updateAllFlags(int result, int op1Lower, int op2Lower, boolean isSubtraction) {
        if (isSubtraction) {
            carryFlag = (result < 0);
            auxCarryFlag = (op1Lower - op2Lower) < 0;
        } else {
            carryFlag = (result > 0xFF);
            auxCarryFlag = (op1Lower + op2Lower) > 0x0F;
        }
        int masked = result & 0xFF;
        zeroFlag = (masked == 0);
        signFlag = ((masked & 0x80) != 0);
        parityFlag = (Integer.bitCount(masked) % 2 == 0);
    }

    // Updates S, Z, AC, P but preserves CY — used by INR/DCR
    public void updateFlagsExceptCarry(int result, int op1Lower, int op2Lower, boolean isSubtraction) {
        boolean savedCy = carryFlag;
        updateAllFlags(result, op1Lower, op2Lower, isSubtraction);
        carryFlag = savedCy;
    }

    // Updates only S, Z, P from an 8-bit result — used by logical ops
    private void updateSzp(int result) {
        int masked = result & 0xFF;
        signFlag = ((masked & 0x80) != 0);
        zeroFlag = (masked == 0);
        parityFlag = (Integer.bitCount(masked) % 2 == 0);
    }

    // Arithmetic: result = a + b + carryIn, updates all flags, returns 8-bit
    // [AG-FIX] AC: mask b BEFORE adding carry to prevent nibble wrap
    public int add(int a, int b, int carryIn) {
        int result = a + b + carryIn;
        updateAllFlags(result, a & 0x0F, (b & 0x0F) + carryIn, false);
        return result & 0xFF;
    }

    // Arithmetic: result = a - b - borrowIn, updates all flags, returns 8-bit
    // [AG-FIX] AC: mask b BEFORE adding borrow to prevent nibble wrap
    public int subtract(int a, int b, int borrowIn) {
        int result = a - b - borrowIn;
        updateAllFlags(result, a & 0x0F, (b & 0x0F) + borrowIn, true);
        return result & 0xFF;
    }

    // Increment: result = value + 1, CY unchanged
    public int increment(int value) {
        int result = value + 1;
        updateFlagsExceptCarry(result, value & 0x0F, 1, false);
        return result & 0xFF;
    }

    // Decrement: result = value - 1, CY unchanged
    public int decrement(int value) {
        int result = value - 1;
        updateFlagsExceptCarry(result, value & 0x0F, 1, true);
        return result & 0xFF;
    }

    // AND: CY=0, AC=1, update S/Z/P
    public int and(int a, int b) {
        int result = (a & b) & 0xFF;
        carryFlag = false;
        auxCarryFlag = true;
        updateSzp(result);
        return result;
    }

    // OR: CY=0, AC=0, update S/Z/P
    public int or(int a, int b) {
        int result = (a | b) & 0xFF;
        carryFlag = false;
        auxCarryFlag = false;
        updateSzp(result);
        return result;
    }

    // XOR: CY=0, AC=0, update S/Z/P
    public int xor(int a, int b) {
        int result = (a ^ b) & 0xFF;
        carryFlag = false;
        auxCarryFlag = false;
        updateSzp(result);
        return result;
    }

    // Compare: flags from (a - b) but result discarded
    public void compare(int a, int b) {
        subtract(a, b, 0);
    }

    // Compose flag register byte: [S Z 0 AC 0 P 1 CY]
    public int getFlagsByte() {
        int flags = 0x02; // bit 1 always 1
        if (signFlag)     flags |= 0x80;
        if (zeroFlag)     flags |= 0x40;
        if (auxCarryFlag) flags |= 0x10;
        if (parityFlag)   flags |= 0x04;
        if (carryFlag)    flags |= 0x01;
        return flags;
    }

    // Decompose flag register byte back into booleans
    public void setFlagsByte(int flagsByte) {
        signFlag     = (flagsByte & 0x80) != 0;
        zeroFlag     = (flagsByte & 0x40) != 0;
        auxCarryFlag = (flagsByte & 0x10) != 0;
        parityFlag   = (flagsByte & 0x04) != 0;
        carryFlag    = (flagsByte & 0x01) != 0;
    }

    public String flagsToString() {
        return String.format("S=%d Z=%d AC=%d P=%d CY=%d",
                signFlag ? 1 : 0, zeroFlag ? 1 : 0, auxCarryFlag ? 1 : 0,
                parityFlag ? 1 : 0, carryFlag ? 1 : 0);
    }

    // Getters
    public boolean isSignFlag()     { return signFlag; }
    public boolean isZeroFlag()     { return zeroFlag; }
    public boolean isAuxCarryFlag() { return auxCarryFlag; }
    public boolean isParityFlag()   { return parityFlag; }
    public boolean isCarryFlag()    { return carryFlag; }

    // Setters
    public void setSignFlag(boolean val)     { signFlag = val; }
    public void setZeroFlag(boolean val)     { zeroFlag = val; }
    public void setAuxCarryFlag(boolean val) { auxCarryFlag = val; }
    public void setParityFlag(boolean val)   { parityFlag = val; }
    public void setCarryFlag(boolean val)    { carryFlag = val; }
}
