package lab10.PiplinedCPU.DatapathModule.DatapathComponent

import chisel3._
import chisel3.stage.ChiselStage


class Vector_RegFile(readPorts:Int) extends Module {
   val io    = IO(new Bundle{
   val vector_wen   = Input(Bool())
   val vector_waddr = Input(UInt(5.W))
   val vector_wdata = Input(UInt(512.W))
   val vector_raddr = Input(Vec(readPorts, UInt(5.W)))
   val vector_rdata = Output(Vec(readPorts, UInt(512.W)))
   val vector_regs  = Output(Vec(32,UInt(512.W)))
 })

  // 32 * 64 RegFile
  val init_value = Seq.fill(32)(0.U(512.W))

  //for Lab10-4 vadd_vv demonstration
  // val init_value = Seq(0.U(512.W)) ++ 
  // Seq("h000102030405060708090a0b0c0d0e0f101112131415161718191a1b1f1d1e1f".U(512.W)) ++
  // Seq("h000102030405060708090a0b0c0d0e0f101112131415161718191a1b1f1d1e1f".U(512.W)) ++ 
  // Seq.fill(29)(0.U(512.W))

  val vector_regs = RegInit(VecInit(init_value))

  //Wiring=============================================================================
  //Read
  (io.vector_rdata.zip(io.vector_raddr)).map{case(data,addr)=>data:=vector_regs(addr)}

  //Write
  when(io.vector_wen) {vector_regs(io.vector_waddr) := io.vector_wdata}

  vector_regs(0) := 0.U(512.W)

  io.vector_regs := vector_regs
}