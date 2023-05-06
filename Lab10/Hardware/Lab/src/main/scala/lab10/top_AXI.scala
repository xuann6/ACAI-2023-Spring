package lab10

import chisel3._
import chisel3.util._

import lab10.PiplinedCPU._
import lab10.Memory._
import lab10.MemIF._
import lab10.AXI._

object config {
  val addr_width = 16
  val data_width = 32
  val addr_map = List(("h8000".U, "h10000".U))
  val data_mem_size = 15 // power of 2 in byte
  val inst_mem_size = 15 // power of 2 in byte
  val data_hex_path = "./src/main/resource/data.hex"
}

import config._

class top_AXI extends Module {
    val io = IO(new Bundle{
        val regs = Output(Vec(32,UInt(data_width.W)))
        val Hcf = Output(Bool())

        /* lab 10-4 Modification of Vector Extension */
        val vector_regs  = Output(Vec(32,UInt(512.W)))

        //for sure that IM and DM will be synthesized
        val inst = Output(UInt(32.W))
        val rdata = Output(UInt(32.W))
        val wdata  = Output(UInt(32.W))

        // Test
        val E_Branch_taken = Output(Bool())
        val Flush = Output(Bool())
        val Stall_MA = Output(Bool())
        val Stall_DH = Output(Bool())
        val IF_PC = Output(UInt(32.W))
        val ID_PC = Output(UInt(32.W))
        val EXE_PC = Output(UInt(32.W))
        val MEM_PC = Output(UInt(32.W))
        val WB_PC = Output(UInt(32.W))
        val EXE_alu_out = Output(UInt(32.W))
        val EXE_src1 = Output(UInt(32.W))
        val EXE_src2 = Output(UInt(32.W))
        val ALU_src1 = Output(UInt(32.W))
        val ALU_src2 = Output(UInt(32.W))
        val raddr = Output(UInt(32.W))
        val WB_rd = Output(UInt(5.W))
        val WB_wdata = Output(UInt(32.W))
        val EXE_Jump = Output(Bool())
        val EXE_Branch = Output(Bool())

        // (Hsuan) lab 10-3
        val Mem_Read = Output(Bool())
        val Mem_Write = Output(Bool())
        val Mem_Read_Write_Length = Output(UInt(4.W))
    })

    val cpu = Module(new PiplinedCPU(addr_width,512))
    val bus = Module(new AXIXBar(1, addr_map.length, addr_width, data_width, addr_map))
    val im = Module(new InstMem(inst_mem_size))
    val dm = Module(new DataMem_AXI(data_mem_size, addr_width, data_width, "./src/main/resource/data.hex"))
    // example : AXI_IF(memAddrWidth: Int, memDataWidth: Int, busDataWidth: Int)
    val axi_if = Module(new AXI_IF(addr_width,512,data_width))
    
    // Insruction - CPU
    cpu.io.InstMem.rdata := im.io.inst
    cpu.io.InstMem.Valid := true.B // Direct to Mem
    im.io.raddr := cpu.io.InstMem.raddr(inst_mem_size-1,0)
    
    // CPU - AXI BUS
    cpu.io.DataMem <> axi_if.io.memIF
    axi_if.io.bus_master <> bus.io.masters(0)

    // Data Memory - AXI BUS
    bus.io.slaves(0) <> dm.io.bus_slave

    //System
    io.regs := cpu.io.regs
    io.Hcf := cpu.io.Hcf
    io.inst := im.io.inst
    io.rdata := dm.io.rdata
    io.wdata := dm.io.wdata

    /* lab 10-4 Modification of Vector Extension */
    io.vector_regs := cpu.io.vector_regs

    // Test
    io.E_Branch_taken := cpu.io.E_Branch_taken
    io.Flush := cpu.io.Flush
    io.Stall_MA := cpu.io.Stall_MA
    io.Stall_DH := cpu.io.Stall_DH
    io.IF_PC := cpu.io.IF_PC
    io.ID_PC := cpu.io.ID_PC
    io.EXE_PC := cpu.io.EXE_PC
    io.MEM_PC := cpu.io.MEM_PC
    io.WB_PC := cpu.io.WB_PC
    io.EXE_alu_out := cpu.io.EXE_alu_out
    io.EXE_src1 := cpu.io.EXE_src1
    io.EXE_src2 := cpu.io.EXE_src2
    io.ALU_src1 := cpu.io.ALU_src1
    io.ALU_src2 := cpu.io.ALU_src2
    io.raddr := cpu.io.DataMem.raddr
    io.WB_rd := cpu.io.WB_rd
    io.WB_wdata := cpu.io.WB_wdata
    io.EXE_Jump := cpu.io.EXE_Jump
    io.EXE_Branch := cpu.io.EXE_Branch

    // (Hsuan) lab 10-3
    io.Mem_Read := cpu.io.Mem_Read
    io.Mem_Write := cpu.io.Mem_Write
    io.Mem_Read_Write_Length := cpu.io.Mem_Read_Write_Length
}


import chisel3.stage.ChiselStage
object top_AXI extends App {
  (
    new chisel3.stage.ChiselStage).emitVerilog(
      new top_AXI(),
      Array("-td","generated/top_AXI")
  )
}
