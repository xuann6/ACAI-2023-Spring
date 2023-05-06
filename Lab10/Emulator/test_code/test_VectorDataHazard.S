.data
tdata1: .word 0x05060708
tdata2: .word 0x01020304

.text
  #-------------------------------------------------------------
  # IF rs - MEM rd Data Hazard tests
  #-------------------------------------------------------------
## rs1 - rd
test1:
  vadd_vv v3, v1, v2
  nop
  nop
  vadd_vv v3, v3, v1

## rs2 - rd
test2:
  vadd_vv v4, v1, v2
  nop
  nop
  vadd_vv v4, v1, v4

test3:
  addi t1, x0, 2
  nop
  nop
  vmul_vx v5, v1, t1
  
  #-------------------------------------------------------------
  # IF rs - EXE rd Data Hazard tests
  #-------------------------------------------------------------
## rs1 - rd
test4:
  vadd_vv v6, v1, v2
  nop
  vadd_vv v6, v6, v1

## rs2 - rd
test5:
  vadd_vv v7, v1, v2
  nop
  vadd_vv v7, v1, v7

test6:
  addi t2, x0, 2
  nop
  vmul_vx v8, v1, t2

  #-------------------------------------------------------------
  # IF rs - ID rd Data Hazard tests
  #-------------------------------------------------------------
## rs1 - rd
test7:
  vadd_vv v9, v1, v2
  vadd_vv v9, v9, v1

## rs2 - rd
test8:
  vadd_vv v10, v1, v2
  vadd_vv v10, v1, v10

test9:
  addi t3, x0, 2
  vmul_vx v11, v1, t3

  #-------------------------------------------------------------
  # IF rs - ID rd (Memory Access) Data Hazard tests
  #-------------------------------------------------------------
## rs1 - rd
test10:
  la t4, tdata1
  vle8_v	v12, 0(t4)
  vadd_vv	v12, v12, v1

## rs2 - rd
test11:
  la t5, tdata1
  vle8_v	v13, 0(t5)
  vadd_vv	v13, v1, v13

  #-------------------------------------------------------------
  # All Test
  #-------------------------------------------------------------

nop
nop
nop
nop
nop
hcf