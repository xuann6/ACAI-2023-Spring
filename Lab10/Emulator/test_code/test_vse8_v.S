.data
tdata1: .word 0x05060708
tdata2: .word 0x01020304
.text
main:
la t0, tdata1
vse8_v	v1, 0(t0)
nop
nop
nop
nop
nop
vle8_v	v3, 0(t0)
nop
nop
nop
nop
nop
hcf