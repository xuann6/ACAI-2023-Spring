#=======================================================================
# Makefile for riscv-tests/isa
#-----------------------------------------------------------------------
XLEN := 32

# ----------------------------------------------------------------------

#XLEN ?= 64

src_dir := .

#ifeq ($(XLEN),64)
#include $(src_dir)/rv64ui/Makefrag
#endif
include $(src_dir)/rv32ui_FullTest/Makefrag
include $(src_dir)/rv32ui_SingleTest/Makefrag


default: all

#--------------------------------------------------------------------
# Build rules
#--------------------------------------------------------------------

RISCV_PREFIX ?= riscv$(XLEN)-unknown-elf-
RISCV_GCC ?= $(RISCV_PREFIX)gcc
RISCV_GCC_OPTS ?= -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles
RISCV_OBJDUMP ?= $(RISCV_PREFIX)objdump --full-contents --all-headers --disassemble-all --disassemble-zeroes --section=.text --section=.data
RISCV_ASMDUMP ?= $(RISCV_PREFIX)objdump --disassemble --disassemble-zeroes --no-addresses --prefix-addresses --demangle --no-show-raw-insn --section=.text

RISCV_IMDUMP ?= $(RISCV_PREFIX)objdump --disassemble --disassemble-zeroes --no-addresses --prefix-addresses  --show-raw-insn --section=.text

RISCV_DMDUMP ?= $(RISCV_PREFIX)objdump --full-contents --section=.data

DUMP_INST_TO_BYTE ?=  awk -v f=0 'f==1 && $$1=="00000073"{print "0b";print "00";print "00"; print "00"; } f==1 && $$1!="00000073"{print substr($$1,7,2);print substr($$1,5,2);print substr($$1,3,2);print substr($$1,1,2);} /Disassembly of section/{f=1;}'

DUMP_DATA_TO_BYTE ?=  awk -v f=0 'f==1 {print substr($$2,1,2);print substr($$2,3,2);print substr($$2,5,2);print substr($$2,7,2);print substr($$3,1,2);print substr($$3,3,2);print substr($$3,5,2);print substr($$3,7,2);print substr($$4,1,2);print substr($$4,3,2);print substr($$4,5,2);print substr($$4,7,2);print substr($$5,1,2);print substr($$5,3,2);print substr($$5,5,2);print substr($$5,7,2);} /Contents of section .data/{f=1;}'

BYTE_TO_HEX ?= awk '!/^$$/'

DUMP_INST_TO_ASM ?= awk -v f=0 'f==1 && $$1=="ecall" {print "hcf"} f==1 && $$1!="ecall" {print $$1,$$2}/Disassembly of section/{f=1;}'

vpath %.S $(src_dir)

#------------------------------------------------------------

# Build assembly tests

%.hex: %
	$(RISCV_IMDUMP) ./out/obj/$< > ./out/hex/$<-text
	$(DUMP_INST_TO_BYTE) ./out/hex/$<-text > ./out/hex/$<-text-byte
	$(BYTE_TO_HEX) ./out/hex/$<-text-byte > ./out/hex/text/$@

	$(RISCV_DMDUMP) ./out/obj/$< > ./out/hex/$<-data
	$(DUMP_DATA_TO_BYTE) ./out/hex/$<-data > ./out/hex/$<-data-byte
	$(BYTE_TO_HEX) ./out/hex/$<-data-byte > ./out/hex/data/$@

	rm ./out/hex/$<-text
	rm ./out/hex/$<-text-byte
	rm ./out/hex/$<-data
	rm ./out/hex/$<-data-byte
	
%.dump: %
	$(RISCV_OBJDUMP) ./out/obj/$< > ./out/dump/$@
%.asm: %
	$(RISCV_ASMDUMP) ./out/obj/$< > ./out/asm/$<
	$(DUMP_INST_TO_ASM) ./out/asm/$< > ./out/asm/$@
	rm ./out/asm/$<

define compile_template

$$($(1)__tests): $(1)-%: $(1)/%.S
	$$(RISCV_GCC) $(2) $$(RISCV_GCC_OPTS) -I$(src_dir)/ -I$(src_dir)/macros/scalar -T$(src_dir)/link.ld $$< -o ./out/obj/$$@
$(1)_tests += $$($(1)__tests)

$(1)_tests_dump = $$(addsuffix .dump, $$($(1)_tests))
$(1)_tests_hex = $$(addsuffix .hex, $$($(1)_tests))
$(1)_tests_asm = $$(addsuffix .asm, $$($(1)_tests))

$(1): $$($(1)_tests_dump) $$($(1)_tests_hex)

.PHONY: $(1)

COMPILER_SUPPORTS_$(1) := $$(shell $$(RISCV_GCC) $(2) -c -x c /dev/null -o /dev/null 2> /dev/null; echo $$$$?)

ifeq ($$(COMPILER_SUPPORTS_$(1)),0)
tests += $$($(1)_tests)
endif

endef

$(eval $(call compile_template,rv32ui_FullTest,-march=rv32g -mabi=ilp32))
$(eval $(call compile_template,rv32ui_SingleTest,-march=rv32g -mabi=ilp32))
#ifeq ($(XLEN),64)
#$(eval $(call compile_template,rv64ui,-march=rv64g -mabi=lp64))
#endif

tests_dump = $(addsuffix .dump, $(tests))
tests_hex = $(addsuffix .hex, $(tests))
tests_asm = $(addsuffix .asm, $(tests))

junk += $(tests) $(tests_dump) $(tests_hex) $(tests_asm)

#------------------------------------------------------------
# Default

all: dump asm hex
 
dump: MKDIR_DUMP $(tests_dump)
asm: MKDIR_ASM $(tests_asm)
hex: MKDIR_HEX $(tests_hex)

MKDIR_OBJ:
	mkdir -p ./out/obj 
MKDIR_DUMP: MKDIR_OBJ
	mkdir -p ./out/dump  
MKDIR_ASM: MKDIR_OBJ
	mkdir -p ./out/asm
MKDIR_HEX: MKDIR_OBJ
	mkdir -p ./out/hex/text
	mkdir -p ./out/hex/data

#------------------------------------------------------------
# Clean up

clean:
	rm -rf $(junk)
	rm -rf ./out 
