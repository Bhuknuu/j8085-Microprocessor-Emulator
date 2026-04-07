import java.util.Scanner;
public class UserInterface {

    //  FIELDS

    /** Scanner for reading user input from System.in */
    private final Scanner scanner;

    /** The CPU/memory system being simulated */
    private Architecture architecture;

    /** The assembler for converting assembly → machine code */
    private Assembler assembler;

    /** Whether the simulator is currently running (main loop control) */
    private boolean running;

    //  CONSTRUCTOR

    public UserInterface() {
           this.scanner = new Scanner(System.in);
           this.running = true;
    }

    //  MAIN ENTRY POINT

    public void start() {
         printWelcomeBanner();
         configureMemory();
         assembler = new Assembler(architecture);

         while (running) {
             printMainMenu();
             int choice = readIntInput("Enter your choice: ");
             handleMenuChoice(choice);
         }

         printGoodbye();
         scanner.close();
    }

    //  DISPLAY METHODS


    private void printWelcomeBanner() {

         System.out.println("╔══════════════════════════════════════════════════╗");
         System.out.println("║                                                  ║");
         System.out.println("║                                                  ║");
         System.out.println("║                    8 0 8 5                       ║");
         System.out.println("║          MICROPROCESSOR   EMULATOR               ║");
         System.out.println("║                                                  ║");
         System.out.println("║                                                  ║");
         System.out.println("╚══════════════════════════════════════════════════╝");
         System.out.println();
    }


    private void printMainMenu() {
        System.out.println("\n");
         System.out.println("┌───────────── MAIN MENU ───────────┐");
         System.out.println("│                                   │");
         System.out.println("│  1. Enter Assembly Program        │");
         System.out.println("│  2. Run Program                   │");
         System.out.println("│  3. Step Through (Debug)          │");
         System.out.println("│  4. View Registers & Flags        │");
         System.out.println("│  5. View Memory                   │");
         System.out.println("│  6. Load Machine Code (Hex)       │");
         System.out.println("│  7. Set Register/Memory Value     │");
         System.out.println("│  8. Reset Simulator               │");
         System.out.println("│  9. Help                          │");
         System.out.println("│ 10. Exit                          │");
         System.out.println("│                                   │");
         System.out.println("└───────────────────────────────────┘");
    }


    private void printGoodbye() {
           System.out.println("\nThank you for using j8085 Simulator. Goodbye!");
    }

    //  MEMORY CONFIGURATION

    private void configureMemory() {
         System.out.println("═══ Memory Configuration ═══");
         System.out.println("1. Default memory range (0000H - FFFFH, 64KB)");
         System.out.println("2. Custom memory range");
         int choice = readIntInput("Select option: ");

         if (choice == 1) {
             architecture = new Architecture();
             System.out.println("✓ Initialized with default memory (64KB).");
         } else if (choice == 2) {
             int start = readHexInput("Enter start address (hex): ");
             int end   = readHexInput("Enter end address (hex): ");
             try {
                 architecture = new Architecture(start, end);
                 System.out.printf("✓ Initialized with custom memory (%04XH - %04XH).\n", start, end);
             } catch (SimulatorException e) {
                 System.out.println("✗ Error: " + e.getMessage());
                 System.out.println("  Falling back to default memory.");
                 architecture = new Architecture();
             }
         } else {
             System.out.println("Invalid choice. Using default memory.");
             architecture = new Architecture();
         }
    }

    //  MENU ACTION HANDLERS


    private void handleMenuChoice(int choice) {
         switch (choice) {
             case 1: handleEnterProgram();     break;
             case 2: handleRunProgram();       break;
             case 3: handleStepThrough();      break;
             case 4: handleViewRegisters();    break;
             case 5: handleViewMemory();       break;
             case 6: handleLoadMachineCode();  break;
             case 7: handleSetValue();         break;
             case 8: handleReset();            break;
             case 9: handleHelp();             break;
             case 0: running = false;          break;
             default:
                 System.out.println("Invalid option. Please try again.");
         }
    }


    private void handleEnterProgram() {
         System.out.println("\n═══ Enter Assembly Program ═══");
         int startAddr = readHexInput("Start address (default 2000H): ");
         if (startAddr == -1) startAddr = 0x2000;

         System.out.println("Enter your program (type 'END' on a new line to finish):");
         StringBuilder program = new StringBuilder();
         while (true) {
             String line = scanner.nextLine();
             if (line.trim().equalsIgnoreCase("END")) break;
             program.append(line).append("\n");
         }

         try {
             int[] machineCode = assembler.assemble(program.toString(), startAddr);
             System.out.printf("✓ Assembled %d bytes at %04XH.\n", machineCode.length, startAddr);
             architecture.setProgramCounter(startAddr);

             // Display assembled bytes
             System.out.print("Machine code: ");
             for (int b : machineCode) System.out.printf("%02X ", b);
             System.out.println();
         } catch (SimulatorException e) {
             System.out.println("✗ Assembly error: " + e.getMessage());
         }
    }


    private void handleRunProgram() {
         System.out.println("\n═══ Run Program ═══");
         System.out.printf("Current PC = %04XH\n", architecture.getProgramCounter());
         int addr = readHexInput("Run from address (Enter for current PC): ");
         if (addr == -1) addr = architecture.getProgramCounter();

         try {
             architecture.runFrom(addr);
             System.out.println("✓ Program halted.");
             System.out.println(architecture.getCPUState());
         } catch (SimulatorException e) {
             System.out.println("✗ Runtime error: " + e);
         }
    }


    private void handleStepThrough() {
         System.out.println("\n═══ Step Through ═══");
         System.out.println("Press Enter to step, 'q' to quit stepping.");

         while (!architecture.isHalted()) {
             int pc = architecture.getProgramCounter();
             try {
                 int opcode = architecture.readMemory(pc);
                 System.out.printf("[%04XH] %s\n", pc, architecture.disassemble(opcode));
                 architecture.step();
                 System.out.println(architecture.getCPUState());
             } catch (SimulatorException e) {
                 System.out.println("✗ Error: " + e);
                 break;
             }

             String input = scanner.nextLine().trim();
             if (input.equalsIgnoreCase("q")) break;
         }
         if (architecture.isHalted()) {
             System.out.println("✓ Program halted (HLT encountered).");
         }
    }


    private void handleViewRegisters() {
           System.out.println(architecture.getCPUState());
    }


    private void handleViewMemory() {
         System.out.println("\n═══ View Memory ═══");
         int from = readHexInput("From address: ");
         int to   = readHexInput("To address: ");
         architecture.displayMemoryRange(from, to);
    }


    private void handleLoadMachineCode() {
         System.out.println("\n═══ Load Machine Code ═══");
         int addr = readHexInput("Start address: ");
         System.out.println("Enter hex bytes (space-separated), e.g.: 3E 42 06 05 80 76");
         String input = scanner.nextLine().trim();

         String[] hexBytes = input.split("\\s+");
         try {
             for (int i = 0; i < hexBytes.length; i++) {
                 int value = Integer.parseInt(hexBytes[i], 16);
                 architecture.writeMemory(addr + i, value);
             }
             System.out.printf("✓ Loaded %d bytes at %04XH.\n", hexBytes.length, addr);
             architecture.setProgramCounter(addr);
         } catch (Exception e) {
             System.out.println("✗ Error loading code: " + e.getMessage());
         }
    }


    private void handleSetValue() {
         System.out.println("\n═══ Set Value ═══");
         System.out.println("1. Set Register");
         System.out.println("2. Set Memory Location");
         int choice = readIntInput("Choice: ");

         if (choice == 1) {
             System.out.print("Register name (A/B/C/D/E/H/L): ");
             String reg = scanner.nextLine().trim().toUpperCase();
             int val = readHexInput("Value (hex): ");
             try {
                 architecture.setRegister(reg, val);
                 System.out.printf("✓ %s = %02XH\n", reg, val);
             } catch (SimulatorException e) {
                 System.out.println("✗ " + e.getMessage());
             }
         } else if (choice == 2) {
             int addr = readHexInput("Address (hex): ");
             int val  = readHexInput("Value (hex): ");
             try {
                 architecture.writeMemory(addr, val);
                 System.out.printf("✓ [%04XH] = %02XH\n", addr, val);
             } catch (SimulatorException e) {
                 System.out.println("✗ " + e.getMessage());
             }
         }
    }


    private void handleReset() {
         architecture.reset();
         System.out.println("✓ Simulator reset. All registers and memory cleared.");
    }


    private void handleHelp() {
         System.out.println("\n═══════════════════════════════════════════════");
         System.out.println("  j8085 Simulator — Quick Reference Guide");
         System.out.println("═══════════════════════════════════════════════");
         System.out.println();
         System.out.println("  WRITING PROGRAMS:");
         System.out.println("    - Use standard 8085 assembly mnemonics");
         System.out.println("    - Labels end with ':'  (e.g., LOOP:)");
         System.out.println("    - Comments start with ';' (e.g., ; add B to A)");
         System.out.println("    - Numbers: 42H (hex), 66 (decimal), 01000010B (binary)");
         System.out.println("    - Every program MUST end with HLT");
         System.out.println();
         System.out.println("  COMMON INSTRUCTIONS:");
         System.out.println("    MVI A, 42H    ; Load 42H into A");
         System.out.println("    MOV B, A      ; Copy A into B");
         System.out.println("    ADD B         ; A = A + B");
         System.out.println("    SUB C         ; A = A - C");
         System.out.println("    JMP 2000H     ; Jump to address 2000H");
         System.out.println("    CMP B         ; Compare A with B (sets flags)");
         System.out.println("    JZ EQUAL      ; Jump to EQUAL label if A == B");
         System.out.println("    HLT           ; Stop execution");
         System.out.println();
         System.out.println("  EXAMPLE PROGRAM (Add two numbers):");
         System.out.println("    MVI A, 05H    ; A = 05");
         System.out.println("    MVI B, 03H    ; B = 03");
         System.out.println("    ADD B         ; A = A + B = 08");
         System.out.println("    STA 3000H     ; Store result at 3000H");
         System.out.println("    HLT           ; Stop");
         System.out.println();
    }

    //  INPUT HELPER METHODS


    private int readIntInput(String prompt) {
        System.out.print(prompt);
        try {
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) return -1;
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private int readHexInput(String prompt) {
         System.out.print(prompt);
         try {
             String input = scanner.nextLine().trim().toUpperCase();
             if (input.isEmpty()) return -1;
             if (input.endsWith("H")) input = input.substring(0, input.length() - 1);
             if (input.startsWith("0X")) input = input.substring(2);
             return Integer.parseInt(input, 16);
         } catch (NumberFormatException e) {
             System.out.println("Invalid hex value. Please try again.");
             return -1;
         }
    }
}