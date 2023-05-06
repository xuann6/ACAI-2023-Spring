package acai_lab11.Lab4

import chisel3._
import chisel3.util._
import acai_lab11.AXI._

class wdata() extends Bundle {
  val data = Flipped(Decoupled(UInt(32.W)))
  val writeResp = Decoupled(UInt(1.W))
}
class rdata() extends Bundle {
  val data = Decoupled(UInt(32.W))
}

// control signal
class dm_axiif() extends Bundle {
  val writeRespvalid = Input(Bool())
  val readRespvalid = Input(Bool())
  val writeAddr_valid = Output(Bool())
  val writeData_valid = Output(Bool())
  val writeData_last = Output(Bool())
  val readAddr_valid = Output(Bool())
  val readData_ready = Output(Bool())
}



class Cache(val table_height: Int, val way: Int,val addrWidth: Int, val dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    // cache <> cpu
    val size = Input(UInt(4.W))
    val addr = Flipped(Decoupled(UInt(32.W)))
    val wdata = new wdata()
    val rdata = new rdata()

    // cache <> AXI Bus
    val axiIF = new AXIMasterIF(addrWidth,dataWidth)
  
    // (Hsuan) performance counter
    val read_hits = Output(UInt(20.W))
    val write_hits = Output(UInt(20.W))
    val read_misses = Output(UInt(20.W))
    val write_misses = Output(UInt(20.W))
    val access_counts = Output(UInt(20.W))

  })
  val controller = Module(new controller)
  val cacheMem = Module(new cacheMem(table_height,way))
  val rdata_reg = RegInit(0.U(256.W))
  
  when(io.axiIF.readData.valid){
    rdata_reg :=Cat(io.axiIF.readData.bits.data(31,0),rdata_reg(255,32))
  }
  // cache <> cpu
  controller.io.addr <> io.addr
  controller.io.wen := io.wdata.data.valid
  controller.io.ren := io.addr.valid
  io.wdata.data.ready <> controller.io.wdata_ready

  // internel wiring
  io.rdata.data.valid := controller.io.rdata_valid
  controller.io.writeResp <> io.wdata.writeResp
  cacheMem.io.addr :=io.addr.bits
  cacheMem.io.tagOut <> controller.io.tag
  cacheMem.io.dataIn <> io.wdata.data.bits
  cacheMem.io.wen := controller.io.cache_wen
  cacheMem.io.cpuDRAM <> controller.io.cache_iscpu
  cacheMem.io.chosenWay := controller.io.chosenWay

  cacheMem.io.Size := io.size
  io.rdata.data.bits:= MuxLookup(io.size,cacheMem.io.dataOut(31,0).asUInt,Seq(
      "b0000".U -> Cat(Fill(24,cacheMem.io.dataOut(7)),cacheMem.io.dataOut(7,0)),
      "b0001".U -> Cat(Fill(16,cacheMem.io.dataOut(15)),cacheMem.io.dataOut(15,0)),
      "b0010".U -> cacheMem.io.dataOut(31,0).asSInt.asUInt,
      "b0100".U -> Cat(0.U(24.W),cacheMem.io.dataOut(7,0)).asSInt.asUInt,
      "b0101".U -> Cat(0.U(16.W),cacheMem.io.dataOut(15,0)).asSInt.asUInt,
      "b1000".U -> cacheMem.io.dataOut(31,0).asSInt.asUInt
    ))
  cacheMem.io.dataIn := MuxLookup(io.size,io.wdata.data.bits(31,0).asSInt,Seq(
      "b0000".U -> io.wdata.data.bits(7,0).asSInt,
      "b0001".U -> io.wdata.data.bits(15,0).asSInt,
      "b0010".U -> io.wdata.data.bits(31,0).asSInt,
      "b0100".U -> Cat(0.U(24.W),io.wdata.data.bits(7,0)).asSInt,
      "b0101".U -> Cat(0.U(16.W),io.wdata.data.bits(15,0)).asSInt,
      "b1000".U -> io.wdata.data.bits(31,0).asSInt
    )).asUInt

  // cache <> AXI Bus

  io.axiIF.writeAddr.valid := controller.io.dm_axi.writeAddr_valid
  io.axiIF.writeAddr.bits.addr := Cat(cacheMem.io.tagOut(controller.io.chosenWay)(17,0), io.addr.bits(13,5),0.U(5.W))
  io.axiIF.writeAddr.bits.len := 8.U
  io.axiIF.writeAddr.bits.size := 32.U
  io.axiIF.writeAddr.bits.burst := 1.U
  io.axiIF.writeData.valid := controller.io.dm_axi.writeData_valid
  io.axiIF.writeData.bits.data := 0.U
  io.axiIF.writeData.bits.strb := (-1.S(64.W)).asUInt
  io.axiIF.writeData.bits.last := controller.io.dm_axi.writeData_last
  io.axiIF.writeResp.ready := true.B
  io.axiIF.readAddr.valid := controller.io.dm_axi.readAddr_valid
  io.axiIF.readAddr.bits.addr := Cat(io.addr.bits(31,5),Fill(5,0.U)) 
  io.axiIF.readAddr.bits.len := 8.U
  io.axiIF.readAddr.bits.size := 32.U
  io.axiIF.readAddr.bits.burst := 1.U
  io.axiIF.readData.ready := controller.io.dm_axi.readData_ready
  controller.io.dm_axi.writeRespvalid := io.axiIF.writeResp.valid
  controller.io.dm_axi.readRespvalid := io.axiIF.readData.bits.last
  cacheMem.io.dm_write :=Cat(io.axiIF.readData.bits.data(31,0),rdata_reg(255,32))
  for(i<-0 until 8){
    when (controller.io.count===i.U){
      io.axiIF.writeData.bits.data := cacheMem.io.dm_read(31+32*i,0+32*i)
    }
  }

  // (Hsuan) performance counter
  io.access_counts := controller.io.access_counts
  io.read_hits := controller.io.read_hits
  io.write_hits := controller.io.write_hits
  io.read_misses := controller.io.read_misses
  io.write_misses := controller.io.write_misses
}

