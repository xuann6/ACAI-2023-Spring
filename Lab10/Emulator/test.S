.data
tdata1: .byte 0xff
tdata2: .byte 0xf0
tdata3: .byte 0x0f
tdata4: .byte 0x00
tdata5: .word 0x00000000


.text   
test9:    
addi gp, x0, 9
addi t3,x0,1 
la t1, tdata1
lb t2, 0(t1)  
add t3,t2,t3
add t3,t3,t2   
sub t3,t2,t3   
beq  t3,x0, test10
beq  x0,x0, fail  

test10:
addi gp, x0, 10
la t3, tdata5
la t1, tdata1
sw t1, 0(t3)
add t2,x0,t3
lw t2, 0(t2)
lb t2,0(t2)
lb t1,0(t1)
beq  t1,t2, pass
beq  x0,x0, fail   

pass:
  addi sp, x0, 0
  beq x0, x0, exit
fail:
  addi x0,x0,0
  addi x0,x0,0
  addi x0,x0,0
  addi x0,x0,0
  addi x0,x0,0
  addi sp, gp, 0
exit:
addi x0,x0,0
addi x0,x0,0
addi x0,x0,0
addi x0,x0,0
addi x0,x0,0
hcf