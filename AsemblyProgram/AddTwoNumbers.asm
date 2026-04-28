MVI A 09H   ; Load first number into Accumulator
MOV B, A    ; Move first number to Register B
MVI A 19  ; Load second number into Accumulator
ADD B       ; Add B to Accumulator (A = A + B)
STA 200A   ; Store result in memory 3007H
HLT         ; Halt execution
