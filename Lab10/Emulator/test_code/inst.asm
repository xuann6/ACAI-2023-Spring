lui x02, 0x00000010
addi x02, x02, 0x00000000
addi sp, sp, -4
sw ra, 0(sp)
lui x10, 0x00000008
addi x10, x10, 0x00000000
lui x11, 0x00000008
addi x11, x11, 0x00000080
lui x12, 0x00000008
addi x12, x12, 0x00000092
addi a3, x0, 8
addi a4, x0, 3
addi a5, x0, 2
addi a6, x0, 6
addi a7, x0, 1
jal ra, convolution_2d
lw ra, 0(sp)
addi sp, sp, 4
lui x10, 0x00000008
addi x10, x10, 0x00000092
addi a1, x0, 6
vle8_v v11, 0(a0)
add a0, a0, a1
vle8_v v12, 0(a0)
add a0, a0, a1
vle8_v v13, 0(a0)
add a0, a0, a1
vle8_v v14, 0(a0)
add a0, a0, a1
vle8_v v15, 0(a0)
add a0, a0, a1
vle8_v v16, 0(a0)
nop zero, zero, 0
nop zero, zero, 0
nop zero, zero, 0
nop zero, zero, 0
nop zero, zero, 0
hcf
addi sp, sp, -36
sw s2, 0(sp)
sw s3, 4(sp)
sw s4, 8(sp)
sw s5, 12(sp)
sw s6, 16(sp)
sw s7, 20(sp)
sw s8, 24(sp)
sw s9, 28(sp)
sw s10, 32(sp)
addi s2, x0, 0
addi s3, x0, 0
addi s5, x0, 0
mul t0, s5, a6
add t0, t0, s2
mul t0, t0, a6
add s8, a2, t0
vadd_vv v1, v0, v0
addi s6, x0, 0
addi s7, x0, 0
addi s4, x0, 0
mul t1, s4, a3
add t2, s2, s6
add t1, t1, t2
mul t1, t1, a3
add t1, t1, s7
add s10, a0, t1
mul t0, s5, a5
add t0, t0, s4
mul t0, t0, a4
add t0, t0, s6
mul t0, t0, a4
add t0, t0, s7
add s9, a1, t0
vle8_v v2, 0(s10)
lb t1, 0(s9)
vmul_vx v2, v2, t1
vadd_vv v1, v1, v2
addi s4, s4, 1
blt s4, a5, for_loop_kernel_dims
addi s7, s7, 1
blt s7, a4, for_loop_kernel_columns
addi s6, s6, 1
blt s6, a4, for_loop_kernel_rows
vse8_v v1, 0(s8)
addi s5, s5, 1
blt s5, a7, for_loop_output_dims
addi s3, s3, 8
blt s3, a6, for_loop_columns
addi s2, s2, 1
blt s2, a6, for_loop_rows
lw s2, 0(sp)
lw s3, 4(sp)
lw s4, 8(sp)
lw s5, 12(sp)
lw s6, 16(sp)
lw s7, 20(sp)
lw s8, 24(sp)
lw s9, 28(sp)
lw s10, 32(sp)
addi sp, sp, 36
jalr x0, x1, x0
nop
nop
nop
nop
nop
hcf
