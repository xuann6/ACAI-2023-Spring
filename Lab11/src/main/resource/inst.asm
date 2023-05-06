lui x02, 0x00000020
addi x02, x02, 0x00000000
lui x08, 0x00000008
addi x08, x08, 0x00000000
lui x09, 0x0000000c
addi x09, x09, 0x00000000
lui x18, 0x00000010
addi x18, x18, 0x00000000
lui x19, 0x00000000
addi x19, x19, 0x00000040
lui x20, 0x00000000
addi x20, x20, 0x00000040
lui x21, 0x00000000
addi x21, x21, 0x00000040
lui x05, 0x00000000
addi x05, x05, 0x00000000
lui x06, 0x00000000
addi x06, x06, 0x00000000
lui x07, 0x00000000
addi x07, x07, 0x00000000
bge t0, s3, epilogue
lui x06, 0x00000000
addi x06, x06, 0x00000000
jal dimn
addi t0, t0, 1
j x0, dimm
bge t1, s5, return
addi sp, sp, -4
sw ra, 0(sp)
lui x07, 0x00000000
addi x07, x07, 0x00000000
jal dimk
lw ra, 0(sp)
addi sp, sp, 4
addi t1, t1, 1
j x0, dimn
bge t2, s4, return
addi sp, sp, -4
sw ra, 0(sp)
jal mult
lw ra, 0(sp)
addi sp, sp, 4
addi t2, t2, 1
j x0, dimk
mul t3, t0, s4
add t3, t3, t2
add t3, s0, t3
lw t3, 0(t3)
mul t4, t2, s5
add t4, t4, t1
add t4, s1, t4
lw t4, 0(t4)
mul t5, t3, t4
mul t6, t0, s5
add t6, t6, t1
add t6, s2, t6
lw t3, 0(t6)
add t5, t5, t3
sw t5, 0(t6)
jalr x0, x1, x0
jalr x0, x1, x0
nop zero, zero, 0
nop zero, zero, 0
nop zero, zero, 0
nop zero, zero, 0
nop zero, zero, 0
hcf
nop
nop
nop
nop
nop
hcf
